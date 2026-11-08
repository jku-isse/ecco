package at.jku.isse.ecco.service;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.core.Remote;
import at.jku.isse.ecco.dao.TransactionStrategy;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.repository.Repository;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Forks into one {@link EccoService}: from a server, from a repository on disk, and from a service
 * already open. A fork of everything is a copy (Repository.Op#copyInto), one leaving revisions out
 * replays the origin's commits ({@link CommitReplay#replayInto}), and origins too old for either are
 * merged. A failed fork into a new repository leaves no repository behind. The callers hold the
 * service's lock, as its fork methods are synchronized.
 */
final class ForkService {

    private static final Logger LOGGER = Logger.getLogger(ForkService.class.getName());

    private final ServiceState state;

    private final ListenerRegistry listeners;

    private final RepositorySettings repositorySettings;

    private final CommitReplay commitReplay;

    private final RemoteSyncService remoteSyncService;

    ForkService(ServiceState state, ListenerRegistry listeners, RepositorySettings repositorySettings, CommitReplay commitReplay, RemoteSyncService remoteSyncService) {

    
    this.state = state;

    
    this.listeners = listeners;

    
    this.repositorySettings = repositorySettings;

    
    this.commitReplay = commitReplay;

    
    this.remoteSyncService = remoteSyncService;
    }

    void fork(String hostname, int port, String deselectedFeatureRevisionsString) {
        if (this.state.isInitialized())
            throw new EccoException("Service must not be initialized for fork operation.");
        if (this.state.repositoryDirectoryExists())
            throw new EccoException("A repository already exists at the given location: " + this.state.repositoryDir());

        // a fork reads files the way its origin's history was read; null from an older server
        Map<String, String> originSettings;
        try {
            originSettings = RemoteSyncService.remoteSettings(new InetSocketAddress(hostname, port), hostname + ":" + port);
        } catch (Exception e) {
            throw new EccoException("Error during remote fork.", e);
        }

        // initialized before the copy, like a local fork - the copy needs this service's entity
        // factory, which was null here: every fork over the network failed
        this.state.init();
        Repository.Op copiedRepository;
        // a fork of everything is a copy, commits and counts included (Repository.Op#copyInto); from a
        // server too old to send one, a merge as before
        Repository.Op originCopy = null;
        Repository.Op originCommits = null;
        try {
            if (originSettings != null)
                this.repositorySettings.replace(originSettings);
            if (deselectedFeatureRevisionsString == null || deselectedFeatureRevisionsString.isBlank())
                originCopy = this.receiveCopy(hostname, port);
            else
                // leaving revisions out: the origin's commits without them, to replay
                originCommits = this.remoteSyncService.receiveCommits(this.state.currentContext().entityFactory.createRemote(EccoService.ORIGIN_REMOTE_NAME, hostname + ":" + port, Remote.Type.REMOTE),
                        new ArrayList<>(), deselectedFeatureRevisionsString);
        } catch (Error e) {
            this.state.abandonNewRepository(e);
            throw e;
        } catch (Exception e) {
            this.state.abandonNewRepository(e);
            throw new EccoException("Error during remote fork.", e);
        }
        if (originCopy != null || originCommits != null) {
            try {
                this.state.currentContext().transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_WRITE);
                Repository.Op repository = this.state.currentContext().repositoryDao.load();
                if (originCopy != null)
                    originCopy.copyInto(repository);
                else
                    this.commitReplay.replayInto(repository, originCommits, new ArrayList<>(originCommits.getCommits()));
                this.state.currentContext().repositoryDao.store(repository);
                this.state.currentContext().remoteDao.storeRemote(this.state.currentContext().entityFactory.createRemote(EccoService.ORIGIN_REMOTE_NAME, hostname + ":" + port, Remote.Type.REMOTE));
                this.state.currentContext().transactionStrategy.end();
            } catch (Error e) {
                this.state.abandonNewRepository(e);
                throw e;
            } catch (Exception e) {
                this.state.abandonNewRepository(e);
                throw new EccoException("Error during remote fork.", e);
            }
            return;
        }
        try (SocketChannel sChannel = SocketChannel.open()) {
            sChannel.configureBlocking(true);
            RemoteSyncService.connect(sChannel, new InetSocketAddress(hostname, port));
            ProgressInputStream progressInputStream = new ProgressInputStream(sChannel.socket().getInputStream());

            ObjectOutputStream oos = new ObjectOutputStream(sChannel.socket().getOutputStream());
            ObjectInputStream ois = SyncObjectStreams.newObjectInputStream(progressInputStream);

            oos.writeObject("PULL");
            oos.writeObject(deselectedFeatureRevisionsString);
            oos.flush();


            int size = (Integer) ois.readObject();
            progressInputStream.setMaxBytes(size);
            progressInputStream.resetProgress();
            progressInputStream.addListener(this.listeners);

            // retrieve remote repository
            Repository.Op subsetRepository = SyncObjectStreams.readRepository(ois);

            progressInputStream.removeListener(this.listeners);


            // copy it using this entity factory
            copiedRepository = subsetRepository.copy(this.state.currentContext().entityFactory);
        } catch (Error e) {
            this.state.abandonNewRepository(e);
            throw e;
        } catch (Exception e) {
            this.state.abandonNewRepository(e);
            throw new EccoException("Error during remote fork.", e);
        }

        try {
            this.state.currentContext().transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_WRITE);

            // merge into this repository
            Repository.Op repository = this.state.currentContext().repositoryDao.load();
            repository.merge(copiedRepository);
            this.state.currentContext().repositoryDao.store(repository);

            // after fork add used remote as default origin remote
            Remote remote = this.state.currentContext().entityFactory.createRemote(EccoService.ORIGIN_REMOTE_NAME, hostname + ":" + port, Remote.Type.REMOTE);
            this.state.currentContext().remoteDao.storeRemote(remote);

            this.state.currentContext().transactionStrategy.end();
        } catch (Error e) {
            this.state.abandonNewRepository(e);
            throw e;
        } catch (Exception e) {
            this.state.abandonNewRepository(e);
            throw new EccoException("Error during remote fork.", e);
        }
    }

    /**
     * The repository of the server at [hostname]:[port] as it is (its CLONE request); null from a server
     * too old to know the request, which closes the connection without an answer.
     */
    private Repository.Op receiveCopy(String hostname, int port) throws IOException, ClassNotFoundException {
        try (SocketChannel sChannel = SocketChannel.open()) {
            sChannel.configureBlocking(true);
            RemoteSyncService.connect(sChannel, new InetSocketAddress(hostname, port));
            ProgressInputStream progressInputStream = new ProgressInputStream(sChannel.socket().getInputStream());
            ObjectOutputStream oos = new ObjectOutputStream(sChannel.socket().getOutputStream());
            ObjectInputStream ois = SyncObjectStreams.newObjectInputStream(progressInputStream);
            oos.writeObject("CLONE");
            oos.flush();

            int size;
            try {
                size = (Integer) ois.readObject();
            } catch (EOFException e) {
                LOGGER.warning("The server at " + hostname + ":" + port + " is too old to send a copy of its repository: forked by merging, without its commits.");
                return null;
            }
            progressInputStream.setMaxBytes(size);
            progressInputStream.resetProgress();
            progressInputStream.addListener(this.listeners);
            try {
                return SyncObjectStreams.readCopy((byte[]) ois.readObject());
            } finally {
                progressInputStream.removeListener(this.listeners);
            }
        }
    }

    void fork(Path originRepositoryDir, String deselectedFeatureRevisionsString) {
        // check that this service has not yet been initialized and that no repository already exists,
        if (this.state.isInitialized())
            throw new EccoException("Service must not be initialized for fork operation.");
        if (this.state.repositoryDirectoryExists())
            throw new EccoException("A repository already exists at the given location: " + this.state.repositoryDir());

        // the repository must be initialized here or the entity factory needed during the subset operation is null
        this.state.init();
        // a fork reads files the way its origin's history was read
        this.repositorySettings.inheritFrom(EccoService.resolveRepositoryDir(originRepositoryDir));

        // create another ecco service and init it on the parent repository directory.
        EccoService originService = new EccoService();
        originService.setRepositoryDir(EccoService.resolveRepositoryDir(originRepositoryDir));
        // create subset repository
        Repository.Op subsetOriginRepository;
        try {
            // open() takes only shared locks and the origin is read in a read-only transaction, so a fork
            // never writes to its origin (ForkLeavesOriginUnchangedTest) - except .ignores/.adapters,
            // which open() creates in a repository old enough not to have them
            originService.open();

            originService.context.transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_ONLY);

            Repository.Op originRepository = originService.context.repositoryDao.load();
            Collection<FeatureRevision> deselected = originService.parseFeatureRevisionsString(deselectedFeatureRevisionsString);
            if (deselected.isEmpty()) {
                // a fork of everything is a copy, commits and counts included (Repository.Op#copyInto)
                this.state.currentContext().transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_WRITE);
                Repository.Op repository = this.state.currentContext().repositoryDao.load();
                originRepository.copyInto(repository);
                this.state.currentContext().repositoryDao.store(repository);
                this.state.currentContext().transactionStrategy.end();
                subsetOriginRepository = null;
            } else if (!originRepository.hasMergeCommits()) {
                // a fork leaving revisions out: the origin's commits without them, replayed (Repository.Op#replay)
                Repository.Op commits = this.commitReplay.copyOfCommits(originRepository, originRepository.commitsMissingIn(List.of(), deselected));
                this.state.currentContext().transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_WRITE);
                Repository.Op repository = this.state.currentContext().repositoryDao.load();
                this.commitReplay.replayInto(repository, commits, new ArrayList<>(commits.getCommits()));
                this.state.currentContext().repositoryDao.store(repository);
                this.state.currentContext().transactionStrategy.end();
                subsetOriginRepository = null;
            } else {
                subsetOriginRepository = originRepository.subset(deselected, originRepository.getMaxOrder(), this.state.currentContext().entityFactory);
            }

            originService.context.transactionStrategy.end();
        } catch (Error e) {
            this.state.rollbackIfTransactionActive();
            originService.rollbackIfTransactionActive();
            this.state.abandonNewRepository(e);
            throw e;
        } catch (Exception e) {
            // quietly: open() itself may have failed, with no transaction (or strategy) to roll back -
            // an unconditional rollback() then replaced the real error
            this.state.rollbackIfTransactionActive();
            originService.rollbackIfTransactionActive();
            this.state.abandonNewRepository(e);

            throw new EccoException("Error during local fork.", e);
        } finally {
            // close parent repository
            originService.close();
        }

        try {
            this.state.currentContext().transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_WRITE);

            // merge into this repository, unless it is a copy already
            if (subsetOriginRepository != null) {
                Repository.Op repository = this.state.currentContext().repositoryDao.load();
                repository.merge(subsetOriginRepository);
                this.state.currentContext().repositoryDao.store(repository);
            }

            // after fork add used remote as default origin remote
            Remote remote = this.state.currentContext().entityFactory.createRemote(EccoService.ORIGIN_REMOTE_NAME, originRepositoryDir.toString(), Remote.Type.LOCAL);
            this.state.currentContext().remoteDao.storeRemote(remote);

            this.state.currentContext().transactionStrategy.end();
        } catch (Error e) {
            this.state.abandonNewRepository(e);
            throw e;
        } catch (Exception e) {
            this.state.abandonNewRepository(e);

            throw new EccoException("Error during local fork.", e);
        }
    }

    void forkAlreadyOpen(EccoService origService, String deselectedFeatureRevisionsString) {
        // create subset repository
        Repository.Op subsetOriginRepository;

        try {
            origService.context.transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_ONLY);
            Repository.Op originRepository = (Repository.Op) origService.getRepository();
            Collection<FeatureRevision> deselected = origService.parseFeatureRevisionsString(deselectedFeatureRevisionsString);
            Repository.Op repository = (Repository.Op) this.state.context().repository();
            if (deselected.isEmpty() && repository.getAssociations().isEmpty() && repository.getCommits().isEmpty()) {
                // a fork of everything into an empty repository is a copy (Repository.Op#copyInto)
                this.state.currentContext().transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_WRITE);
                originRepository.copyInto(repository);
                this.state.currentContext().repositoryDao.store(repository);
                this.state.currentContext().transactionStrategy.end();
                subsetOriginRepository = null;
            } else if (!originRepository.hasMergeCommits() && !repository.hasMergeCommits()) {
                // the origin's commits this repository does not have, without the revisions left out, replayed (Repository.Op#replay)
                Repository.Op commits = this.commitReplay.copyOfCommits(originRepository, originRepository.commitsMissingIn(RemoteSyncService.commitIds(repository), deselected));
                this.state.currentContext().transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_WRITE);
                this.commitReplay.replayInto(repository, commits, new ArrayList<>(commits.getCommits()));
                this.state.currentContext().repositoryDao.store(repository);
                this.state.currentContext().transactionStrategy.end();
                subsetOriginRepository = null;
            } else {
                subsetOriginRepository = originRepository.subset(deselected, originRepository.getMaxOrder(), this.state.currentContext().entityFactory);
            }
            origService.context.transactionStrategy.end();
        } catch (Error e) {
            this.state.rollbackIfTransactionActive();
            origService.rollbackIfTransactionActive();
            throw e;
        } catch (Exception e) {
            // otherwise the ORIGIN's read transaction stays open, breaking that service for good
            this.state.rollbackIfTransactionActive();
            origService.rollbackIfTransactionActive();
            throw new EccoException("Error during local fork.", e);
        }
        if (subsetOriginRepository == null)
            return;

        try {
            Repository.Op repository = (Repository.Op) this.state.context().repository();
            repository.merge(subsetOriginRepository);

            this.state.currentContext().transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_WRITE);
            this.state.currentContext().repositoryDao.store(repository);
            this.state.currentContext().transactionStrategy.end();
        } catch (Error e) {
            this.state.rollbackIfTransactionActive();
            throw e;
        } catch (Exception e) {
            // the target already existed (unlike fork()), so only release the transaction
            this.state.rollbackIfTransactionActive();
            throw new EccoException("Error during local fork.", e);
        }
    }
}
