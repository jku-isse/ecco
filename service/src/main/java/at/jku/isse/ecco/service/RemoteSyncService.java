package at.jku.isse.ecco.service;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.EccoUtil;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Remote;
import at.jku.isse.ecco.dao.TransactionStrategy;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.repository.Repository;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

/**
 * Owns remote CRUD (add/remove/get/getAll) and the raw-socket sync protocol (the {@code startServer}/
 * {@code stopServer} server, and the {@code fetch}/{@code pull}/{@code push} clients) for one
 * {@link EccoService} instance. {@code fork} is deliberately NOT part of this class -- it shares the
 * same "spin up a second nested {@code EccoService} for a LOCAL remote" idiom as {@code pull}/
 * {@code push} but is commit/checkout-orchestration-shaped (creates and initializes a new repository),
 * not sync-shaped, and stayed in {@link EccoService} for that reason.
 */
public class RemoteSyncService {

    private static final Logger LOGGER = Logger.getLogger(RemoteSyncService.class.getName());

    private final EccoService owner;

    public RemoteSyncService(EccoService owner) {
        this.owner = owner;
    }

    public Remote addRemote(String name, String address) {
        owner.checkInitialized();

        Path path;
        try {
            path = Paths.get(address);
        } catch (InvalidPathException | NullPointerException ex) {
            path = null;
        }
        if (address.matches("[a-zA-Z]+:[0-9]+")) {
            return this.addRemote(name, address, Remote.Type.REMOTE);
        } else if (path != null) {
            return this.addRemote(name, address, Remote.Type.LOCAL);
        } else {
            throw new EccoException("Invalid remote address provided.");
        }
    }

    public Remote addRemote(String name, String address, Remote.Type type) {
        owner.checkInitialized();

        try {
            owner.transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_WRITE);

            if (this.getRemote(name) != null)
                throw new EccoException("Remote with this name already exists.");

            Remote remote = owner.entityFactory.createRemote(name, address, type);
            remote = owner.remoteDao.storeRemote(remote);

            owner.transactionStrategy.end();

            return remote;
        } catch (Exception e) {
            owner.transactionStrategy.rollback();

            throw new EccoException("Error adding remote.", e);
        }
    }

    public void removeRemote(String name) {
        owner.checkInitialized();

        try {
            owner.transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_WRITE);

            owner.remoteDao.removeRemote(name);

            owner.transactionStrategy.end();
        } catch (Exception e) {
            owner.transactionStrategy.rollback();

            throw new EccoException("Error removing remote.", e);
        }
    }

    public Remote getRemote(String name) {
        owner.checkInitialized();

        try {
            owner.transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_ONLY);

            Remote remote = owner.remoteDao.loadRemote(name);

            owner.transactionStrategy.end();

            return remote;
        } catch (Exception e) {
            owner.transactionStrategy.rollback();

            throw new EccoException("Error retrieving remote.", e);
        }
    }

    public Collection<Remote> getRemotes() {
        owner.checkInitialized();

        try {
            owner.transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_ONLY);

            Collection<Remote> remotes = owner.remoteDao.loadAllRemotes();

            owner.transactionStrategy.end();

            return remotes;
        } catch (Exception e) {
            owner.transactionStrategy.rollback();

            throw new EccoException("Error retrieving remotes.", e);
        }
    }


    // DISTRIBUTED OPERATIONS //////////////////////////////////////////////////////////////////////////////////////////

    private ServerSocketChannel ssChannel = null;
    private boolean serverShutdown = false;
    private boolean serverRunning = false;
    private final Lock serverLock = new ReentrantLock();

    public boolean serverRunning() {
        return this.serverRunning;
    }

    /**
     * Rolls back a transaction left open by a request handler that threw mid-transaction (e.g. a
     * PUSH's merge() failing after transactionStrategy.begin() but before end()) -- otherwise the
     * repository becomes permanently unclosable ({@code close()} throws "Not all transactions have
     * been ended.") for the rest of this service's lifetime. {@link TransactionStrategy} has no
     * "is a transaction currently active" query, so this probes via rollback() itself and swallows
     * the "no transaction active" case, rather than widening the interface for every backend
     * (Ser/Mem/Neo4j/Jpa/Xml/Jackson) just for this.
     */
    private void rollbackIfTransactionInProgress() {
        try {
            owner.transactionStrategy.rollback();
        } catch (EccoException ignored) {
            // no transaction was active -- nothing to roll back
        }
    }

    public void startServer(int port) {
        owner.checkInitialized();

        if (!this.serverLock.tryLock())
            throw new EccoException("Server is already running.");

        try (ServerSocketChannel ssChannel = ServerSocketChannel.open()) {
            this.ssChannel = ssChannel;
            this.serverRunning = true;
            this.serverShutdown = false;

            ssChannel.configureBlocking(true);
            ssChannel.socket().bind(new InetSocketAddress(port));

            LOGGER.info("Server started on port " + port + ".");
            owner.listeners.fireServerEvent("Server started on port " + port + ".");
            owner.listeners.fireServerStartedEvent(port);

            while (!serverShutdown) {
                try (SocketChannel sChannel = ssChannel.accept()) {
                    ObjectOutputStream oos = new ObjectOutputStream(sChannel.socket().getOutputStream());
                    ObjectInputStream ois = new ObjectInputStream(sChannel.socket().getInputStream());


                    // determine if it is a push (receive data) or a pull (send data)
                    String command = (String) ois.readObject();
                    LOGGER.info("COMMAND: " + command);
                    owner.listeners.fireServerEvent("New connection from " + sChannel.getRemoteAddress() + " with command '" + command + "'.");

                    switch (command) {
                        case "FETCH": { // if fetch, send data
                            // copy features using mem entity factory
                            owner.transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_ONLY);
                            Repository.Op repository = owner.repositoryDao.load();
                            Collection<Feature> copiedFeatures = EccoUtil.deepCopyFeatures(repository.getFeatures(), owner.entityFactory);
                            owner.transactionStrategy.end();

                            // send features, size-prefixed for the client's progress bar. The size is
                            // only an estimate (measured via a scratch ObjectOutputStream) -- the actual
                            // payload below is written through the connection's own `oos` so the client's
                            // single ObjectInputStream sees one continuous stream. A previous version
                            // spliced the scratch stream's raw bytes (including ITS OWN stream header)
                            // directly onto the socket instead, which corrupted the stream from the
                            // client's point of view (StreamCorruptedException: invalid type code: AC)
                            // for every FETCH/PULL -- this cluster had no test coverage until the
                            // characterization test written for this extraction caught it.
                            ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
                            new ObjectOutputStream(byteOutputStream).writeObject(copiedFeatures);
                            oos.writeObject(byteOutputStream.size());
                            oos.writeObject(copiedFeatures);
                            // ObjectOutputStream does not guarantee its internal buffer reaches the
                            // socket after every writeObject() -- without an explicit flush before this
                            // connection closes with nothing more read on it, the last chunk of data can
                            // be left unsent, hanging the client's matching ois.readObject() forever. Hit
                            // intermittently (~50% of runs) on the larger PUSH/PULL payloads below before
                            // this was added; included here too for the same reason, even though this
                            // smaller payload never reproduced it.
                            oos.flush();
                            // flush() only reaches the OS socket send buffer, not the wire -- closing the
                            // connection (below) immediately after can race the OS's own transmission and
                            // emit a TCP RST that discards not-yet-acknowledged bytes. shutdownOutput()
                            // sends a proper FIN instead, which the OS guarantees only happens after
                            // already-written data has been handed off. See RemoteSyncService#push for
                            // where this was actually root-caused (an intermittent, ~1-in-3 hang).
                            sChannel.shutdownOutput();

                            break;
                        }
                        case "PULL": { // if pull, send data
                            // retrieve deselection
                            String deselectedFeatureRevisionsString = (String) ois.readObject();
                            Collection<FeatureRevision> deselected = owner.parseFeatureRevisionsString(deselectedFeatureRevisionsString);

                            // compute subset repository using mem entity factory
                            owner.transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_ONLY);
                            Repository.Op repository = owner.repositoryDao.load();
                            Repository.Op subsetRepository = repository.subset(deselected, repository.getMaxOrder(), owner.entityFactory);
                            owner.transactionStrategy.end();

                            // send subset repository, size-prefixed -- see the FETCH case above for why
                            // the payload is written through `oos` rather than spliced in separately.
                            ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
                            new ObjectOutputStream(byteOutputStream).writeObject(subsetRepository);
                            oos.writeObject(byteOutputStream.size());
                            oos.writeObject(subsetRepository);
                            // associations are their own independently-persisted entities behind a
                            // transient index (see Repository.Op#restoreAssociations) -- not part of
                            // subsetRepository's own serialized form, so send them as a companion payload
                            // for the receiving end to restore. Same for artifacts (Repository.Op#
                            // collectArtifacts/#resolveArtifacts) -- each node only carries an artifactId
                            // surrogate for its (possibly shared) artifact.
                            oos.writeObject(new ArrayList<Association.Op>(subsetRepository.getAssociations()));
                            oos.writeObject(new ArrayList<Artifact.Op<?>>(subsetRepository.collectArtifacts()));
                            // see the FETCH case above for why this flush is necessary -- confirmed via a
                            // 8x-repeated stress test that without it, the client's final readObject() for
                            // this payload hangs forever in ~50% of runs.
                            oos.flush();
                            // see the FETCH case above for why this is also necessary.
                            sChannel.shutdownOutput();

                            break;
                        }
                        case "PUSH": { // if push, receive data
                            // retrieve repository
                            Repository.Op subsetRepository = (Repository.Op) ois.readObject();
                            @SuppressWarnings("unchecked")
                            Collection<Association.Op> pushedAssociations = (Collection<Association.Op>) ois.readObject();
                            subsetRepository.restoreAssociations(pushedAssociations);
                            @SuppressWarnings("unchecked")
                            Collection<Artifact.Op<?>> pushedArtifacts = (Collection<Artifact.Op<?>>) ois.readObject();
                            subsetRepository.resolveArtifacts(pushedArtifacts);

                            // copy it using this entity factory
                            Repository.Op copiedRepository = subsetRepository.copy(owner.entityFactory);

                            // merge into this repository
                            owner.transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_WRITE);
                            Repository.Op repository = owner.repositoryDao.load();
                            repository.merge(copiedRepository);
                            owner.repositoryDao.store(repository);
                            owner.transactionStrategy.end();
                            break;
                        }
                    }
                } catch (AsynchronousCloseException e) {
                    // server shut down
                    this.rollbackIfTransactionInProgress();
                } catch (SocketException | ClosedChannelException e) {
                    LOGGER.warning("Error receiving request.");
                    owner.listeners.fireServerEvent("Error receiving request: " + e.getMessage());
                    e.printStackTrace();
                    this.rollbackIfTransactionInProgress();
                } catch (Exception e) {
                    LOGGER.warning("Error receiving request.");
                    owner.listeners.fireServerEvent("Error receiving request: " + e.getMessage());
                    e.printStackTrace();
                    this.rollbackIfTransactionInProgress();
                }
            }
        } catch (Exception e) {
            throw new EccoException("Error starting server.", e);
        } finally {
            this.serverRunning = false;
            this.serverLock.unlock();
        }

        LOGGER.info("Server stopped.");
        owner.listeners.fireServerEvent("Server stopped.");
        owner.listeners.fireServerStoppedEvent();
    }

    public void stopServer() {
        if (!this.serverRunning)
            throw new EccoException("Server is not running.");

        this.serverShutdown = true;
        try {
            if (this.ssChannel != null) {
                this.ssChannel.close();

                LOGGER.info("Server stopped.");
            }
        } catch (IOException e) {
            throw new EccoException("Error stopping server.", e);
        }
        this.ssChannel = null;
    }


    public void fetch(String remoteName) {
        owner.checkInitialized();

        try {
            owner.transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_WRITE);

            // load remote
            Remote remote = owner.remoteDao.loadRemote(remoteName);
            if (remote == null) {
                throw new EccoException("Remote '" + remoteName + "' does not exist.");
            } else if (remote.getType() == Remote.Type.REMOTE) {

                try (SocketChannel sChannel = SocketChannel.open()) {
                    sChannel.configureBlocking(true);
                    String[] pair = remote.getAddress().split(":");
                    if (sChannel.connect(new InetSocketAddress(pair[0], Integer.parseInt(pair[1])))) {
                        ProgressInputStream progressInputStream = new ProgressInputStream(sChannel.socket().getInputStream());

                        ObjectOutputStream oos = new ObjectOutputStream(sChannel.socket().getOutputStream());
                        ObjectInputStream ois = new ObjectInputStream(progressInputStream);

                        oos.writeObject("FETCH");
                        oos.flush();


                        int size = (Integer) ois.readObject();
                        progressInputStream.setMaxBytes(size);
                        progressInputStream.resetProgress();
                        progressInputStream.addListener(owner);

                        // retrieve features
                        @SuppressWarnings("unchecked")
                        Collection<Feature> features = (Collection<Feature>) ois.readObject();

                        progressInputStream.removeListener(owner);


                        // copy it using this entity factory
                        Collection<Feature> copiedFeatures = EccoUtil.deepCopyFeatures(features, owner.entityFactory);

                        // store with remote
                        remote.getFeatures().clear();
                        remote.getFeatures().addAll(copiedFeatures);
                        owner.remoteDao.storeRemote(remote);
                    } else {
                        throw new EccoException("Error connecting to remote: " + remote.getName() + ": " + pair[0] + ":" + pair[1]);
                    }
                } catch (Exception e) {
                    throw new EccoException("Error during remote fetch.", e);
                }

            } else if (remote.getType() == Remote.Type.LOCAL) {
                // open parent repository
                EccoService parentService = new EccoService();
                parentService.setRepositoryDir(Paths.get(remote.getAddress()));
                parentService.open(); // TODO: init read only! add read only mode for that (also useful for other read only services on a repository such as a read only web interface REST API service).

                // copy features
                Collection<Feature> copiedFeatures = EccoUtil.deepCopyFeatures(parentService.getRepository().getFeatures(), owner.entityFactory);

                // close parent repository
                parentService.close();

                // merge into this repository
                remote.getFeatures().clear();
                remote.getFeatures().addAll(copiedFeatures);
                owner.remoteDao.storeRemote(remote);
            }

            owner.transactionStrategy.end();
        } catch (Exception e) {
            owner.transactionStrategy.rollback();

            throw new EccoException("Error during fetch.", e);
        }
    }

    public void pull(String remoteName) {
        this.pull(remoteName, "");
    }

    /**
     * Pulls the changes from the parent repository to this repository.
     *
     * @param remoteName                       The name of the remote.
     * @param deselectedFeatureRevisionsString A string enumerating the deselected feature revisions.
     */
    public void pull(String remoteName, String deselectedFeatureRevisionsString) {
        owner.checkInitialized();

        try {
            owner.transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_WRITE);

            // load remote
            Remote remote = owner.remoteDao.loadRemote(remoteName);
            if (remote == null) {
                throw new EccoException("Remote '" + remoteName + "' does not exist.");
            } else if (remote.getType() == Remote.Type.REMOTE) {

                try (SocketChannel sChannel = SocketChannel.open()) {
                    sChannel.configureBlocking(true);
                    String[] pair = remote.getAddress().split(":");
                    if (sChannel.connect(new InetSocketAddress(pair[0], Integer.parseInt(pair[1])))) {
                        ProgressInputStream progressInputStream = new ProgressInputStream(sChannel.socket().getInputStream());

                        ObjectOutputStream oos = new ObjectOutputStream(sChannel.socket().getOutputStream());
                        ObjectInputStream ois = new ObjectInputStream(progressInputStream);

                        oos.writeObject("PULL");
                        oos.writeObject(deselectedFeatureRevisionsString);
                        oos.flush();


                        int size = (Integer) ois.readObject();
                        progressInputStream.setMaxBytes(size);
                        progressInputStream.resetProgress();
                        progressInputStream.addListener(owner);

                        // retrieve remote repository
                        Repository.Op subsetRepository = (Repository.Op) ois.readObject();
                        // associations and artifacts travel as companion payloads -- see
                        // Repository.Op#restoreAssociations/#collectArtifacts/#resolveArtifacts.
                        @SuppressWarnings("unchecked")
                        Collection<Association.Op> pulledAssociations = (Collection<Association.Op>) ois.readObject();
                        subsetRepository.restoreAssociations(pulledAssociations);
                        @SuppressWarnings("unchecked")
                        Collection<Artifact.Op<?>> pulledArtifacts = (Collection<Artifact.Op<?>>) ois.readObject();
                        subsetRepository.resolveArtifacts(pulledArtifacts);

                        progressInputStream.removeListener(owner);


                        // copy it using this entity factory
                        Repository.Op copiedRepository = subsetRepository.copy(owner.entityFactory);

                        // merge into this repository
                        Repository.Op repository = owner.repositoryDao.load();
                        repository.merge(copiedRepository);
                        owner.repositoryDao.store(repository);
                    } else {
                        throw new EccoException("Error connecting to remote: " + remote.getName() + ": " + pair[0] + ":" + pair[1]);
                    }
                } catch (Exception e) {
                    throw new EccoException("Error during remote pull.", e);
                }

            } else if (remote.getType() == Remote.Type.LOCAL) {
                // open parent repository
                EccoService parentService = new EccoService();
                parentService.setRepositoryDir(Paths.get(remote.getAddress()));
                parentService.open(); // TODO: init read only! add read only mode for that (also useful for other read only services on a repository such as a read only web interface REST API service).

                // create subset repository
                Repository.Op subsetParentRepository;
                try {
                    parentService.transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_ONLY);

                    Repository.Op parentRepository = parentService.repositoryDao.load();
                    subsetParentRepository = parentRepository.subset(parentService.parseFeatureRevisionsString(deselectedFeatureRevisionsString), parentRepository.getMaxOrder(), owner.entityFactory);

                    parentService.transactionStrategy.end();
                } catch (Exception e) {
                    parentService.transactionStrategy.rollback();

                    throw new EccoException("Error during local pull.", e);
                }

                // close parent repository
                parentService.close();

                // merge into this repository
                Repository.Op repository = owner.repositoryDao.load();
                repository.merge(subsetParentRepository);
                owner.repositoryDao.store(repository);
            }

            owner.transactionStrategy.end();
        } catch (Exception e) {
            owner.transactionStrategy.rollback();

            throw new EccoException("Error during pull.", e);
        }
    }


    public void push(String remoteName) {
        this.push("", remoteName);
    }

    /**
     * Pushes the changes from this repository to its parent repository.
     *
     * @param remoteName                       The name of the remote.
     * @param deselectedFeatureRevisionsString A string enumerating the deselected feature revisions.
     */
    public void push(String remoteName, String deselectedFeatureRevisionsString) {
        owner.checkInitialized();

        try {
            owner.transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_ONLY);

            // load remote
            Remote remote = owner.remoteDao.loadRemote(remoteName);
            if (remote == null) {
                throw new EccoException("Remote " + remoteName + " does not exist");
            } else if (remote.getType() == Remote.Type.REMOTE) {

                try (SocketChannel sChannel = SocketChannel.open()) {
                    sChannel.configureBlocking(true);
                    String[] pair = remote.getAddress().split(":");
                    if (sChannel.connect(new InetSocketAddress(pair[0], Integer.parseInt(pair[1])))) {
                        // oos wraps the progress-tracking stream (not the raw socket stream directly) so
                        // that the actual payload write below -- not just a separate scratch stream used
                        // only to estimate size, see below -- is what the progress listener observes.
                        ProgressOutputStream pos = new ProgressOutputStream(sChannel.socket().getOutputStream());
                        ObjectOutputStream oos = new ObjectOutputStream(pos);

                        oos.writeObject("PUSH");

                        // compute subset repository using mem entity factory
                        owner.transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_ONLY);
                        Repository.Op repository = owner.repositoryDao.load();
                        Repository.Op subsetRepository = repository.subset(owner.parseFeatureRevisionsString(deselectedFeatureRevisionsString), repository.getMaxOrder(), owner.entityFactory);
                        owner.transactionStrategy.end();

                        // estimate size for the progress bar via a scratch stream -- the actual payload
                        // is sent through `oos` below (see PULL's server-side case in startServer() for
                        // why splicing a separate ObjectOutputStream's raw bytes directly onto the
                        // connection, as this used to do, corrupts the stream from the reader's side).
                        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
                        new ObjectOutputStream(byteOutputStream).writeObject(subsetRepository);
                        pos.setMaxBytes(byteOutputStream.size());
                        pos.resetProgress();

                        pos.addListener(owner);
                        oos.writeObject(subsetRepository);
                        // associations and artifacts travel as companion payloads -- see
                        // Repository.Op#restoreAssociations/#collectArtifacts/#resolveArtifacts.
                        oos.writeObject(new ArrayList<Association.Op>(subsetRepository.getAssociations()));
                        oos.writeObject(new ArrayList<Artifact.Op<?>>(subsetRepository.collectArtifacts()));
                        // flush() only pushes ObjectOutputStream's/pos's own buffers into the OS socket
                        // send buffer -- it does NOT wait for the peer to ACK receipt over TCP. Closing
                        // the channel (via the try-with-resources below) immediately after can race the
                        // OS's own transmission of that already-flushed data: if the close happens before
                        // the OS has actually sent + gotten an ACK for the last chunk, closing can emit a
                        // TCP RST that discards it, leaving the server's matching read blocked forever
                        // waiting for bytes that were "successfully written" on this end but never
                        // actually arrived. shutdownOutput() sends a proper FIN instead, which the OS
                        // guarantees only happens after already-written data has been handed off -- this
                        // is what actually fixed the intermittent (~1-in-3 to ~1-in-4) PUSH hang; oos.flush()
                        // alone was necessary but not sufficient (it fixed a distinct, contained,
                        // ~50%-reproducible bug: data that never left ObjectOutputStream's own buffer at
                        // all -- see the FETCH case in startServer() for that one).
                        oos.flush();
                        sChannel.shutdownOutput();
                        pos.removeListener(owner);

                    } else {
                        throw new EccoException("Error connecting to remote: " + pair[0] + ":" + pair[1]);
                    }
                } catch (Exception e) {
                    throw new EccoException("Error during remote push.", e);
                }

            } else if (remote.getType() == Remote.Type.LOCAL) {
                // open parent repo
                EccoService parentService = new EccoService();
                parentService.setRepositoryDir(Paths.get(remote.getAddress()));
                parentService.open(); // TODO: init read only! add read only mode for that (also useful for other read only services on a repository such as a read only web interface REST API service).

                // create subset repository
                Repository.Op repository = owner.repositoryDao.load();
                Repository.Op subsetRepository = repository.subset(owner.parseFeatureRevisionsString(deselectedFeatureRevisionsString), repository.getMaxOrder(), parentService.entityFactory);

                // merge into parent repository
                try {
                    parentService.transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_WRITE);

                    Repository.Op parentRepository = parentService.repositoryDao.load();
                    parentRepository.merge(subsetRepository);
                    parentService.repositoryDao.store(parentRepository);

                    parentService.transactionStrategy.end();
                } catch (Exception e) {
                    parentService.transactionStrategy.rollback();

                    throw new EccoException("Error during local push.", e);
                }

                // close parent repository
                parentService.close();
            }

            owner.transactionStrategy.end();
        } catch (Exception e) {
            owner.transactionStrategy.rollback();

            throw new EccoException("Error during push.", e);
        }
    }

}
