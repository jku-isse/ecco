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
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    /** How long connecting to a remote may take. */
    static final int CONNECT_TIMEOUT_MILLIS = 10_000;
    /** How long a single read from a remote may wait - a pull of a large repository can take a while on the server before it answers. */
    static final int READ_TIMEOUT_MILLIS = Integer.getInteger("ecco.sync.readTimeoutSeconds", 300) * 1000;

    /**
     * Connects {@code channel} (blocking) to {@code address} with a connect timeout, and sets a read
     * timeout: without them a remote that accepted the connection and never answered held a fetch -
     * and the service's lock, which the GUI's Save, Close and Quit wait for - forever.
     */
    static boolean connect(SocketChannel channel, InetSocketAddress address) throws IOException {
        channel.configureBlocking(true);
        channel.socket().connect(address, CONNECT_TIMEOUT_MILLIS);
        channel.socket().setSoTimeout(READ_TIMEOUT_MILLIS);
        return true;
    }

    public Remote addRemote(String name, String address) {
        owner.checkInitialized();

        Path path;
        try {
            path = Paths.get(address);
        } catch (InvalidPathException | NullPointerException ex) {
            path = null;
        }
        if (RemoteAddress.parseHostPort(address).isPresent()) {
            return this.addRemote(name, address, Remote.Type.REMOTE);
        } else if (path != null) {
            return this.addRemote(name, address, Remote.Type.LOCAL);
        } else {
            throw new EccoException("Invalid remote address provided.");
        }
    }

    /**
     * Resolves a REMOTE remote's "host:port" address (see {@link RemoteAddress}).
     */
    private static InetSocketAddress socketAddressOf(Remote remote) {
        InetSocketAddress address = RemoteAddress.parseHostPort(remote.getAddress())
                .orElseThrow(() -> new EccoException("Invalid remote address: " + remote.getAddress()));
        return new InetSocketAddress(address.getHostString(), address.getPort());
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
        } catch (Error e) {
            owner.rollbackIfTransactionActive();
            throw e;
        } catch (Exception e) {
            owner.rollbackIfTransactionActive();

            throw new EccoException("Error adding remote.", e);
        }
    }

    /**
     * Changes the name, address and type of the remote {@code name}; what was fetched from it is kept.
     *
     * @return the changed remote
     * @throws EccoException if there is no such remote, the new name is empty or taken, or the
     *                       address does not fit the type; the remote is then unchanged.
     */
    public Remote updateRemote(String name, String newName, String address, Remote.Type type) {
        owner.checkInitialized();

        if (newName == null || newName.isBlank())
            throw new EccoException("Remote name must not be empty.");
        if (type == Remote.Type.REMOTE && RemoteAddress.parseHostPort(address).isEmpty())
            throw new EccoException("Invalid remote address provided: expected host:port.");
        if (type == Remote.Type.LOCAL) {
            try {
                Paths.get(address);
            } catch (InvalidPathException | NullPointerException ex) {
                throw new EccoException("Invalid remote address provided: expected a directory.");
            }
        }

        try {
            owner.transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_WRITE);

            Remote remote = owner.remoteDao.loadRemote(name);
            if (remote == null)
                throw new EccoException("Remote '" + name + "' does not exist.");
            if (!newName.equals(name) && owner.remoteDao.loadRemote(newName) != null)
                throw new EccoException("Remote with this name already exists.");

            owner.remoteDao.removeRemote(name);
            remote.setName(newName);
            remote.setAddress(address);
            remote.setType(type);
            remote = owner.remoteDao.storeRemote(remote);

            owner.transactionStrategy.end();

            return remote;
        } catch (Error e) {
            owner.rollbackIfTransactionActive();
            throw e;
        } catch (EccoException e) {
            owner.rollbackIfTransactionActive();
            throw e;
        } catch (Exception e) {
            owner.rollbackIfTransactionActive();

            throw new EccoException("Error changing remote.", e);
        }
    }

    public void removeRemote(String name) {
        owner.checkInitialized();

        try {
            owner.transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_WRITE);

            owner.remoteDao.removeRemote(name);

            owner.transactionStrategy.end();
        } catch (Error e) {
            owner.rollbackIfTransactionActive();
            throw e;
        } catch (Exception e) {
            owner.rollbackIfTransactionActive();

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
        } catch (Error e) {
            owner.rollbackIfTransactionActive();
            throw e;
        } catch (Exception e) {
            owner.rollbackIfTransactionActive();

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
        } catch (Error e) {
            owner.rollbackIfTransactionActive();
            throw e;
        } catch (Exception e) {
            owner.rollbackIfTransactionActive();

            throw new EccoException("Error retrieving remotes.", e);
        }
    }


    // DISTRIBUTED OPERATIONS //////////////////////////////////////////////////////////////////////////////////////////

    // volatile: written by the server thread, read/written by whoever calls stopServer()/serverRunning()
    private volatile ServerSocketChannel ssChannel = null;
    private volatile boolean serverShutdown = false;
    private volatile boolean serverRunning = false;
    private volatile int serverPort = -1;
    private final Lock serverLock = new ReentrantLock();

    public boolean serverRunning() {
        return this.serverRunning;
    }

    /**
     * The port the running server listens on, or -1 while none is listening.
     */
    public int serverPort() {
        return this.serverPort;
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
            synchronized (owner) {
                owner.transactionStrategy.rollback();
            }
        } catch (EccoException ignored) {
            // no transaction was active -- nothing to roll back
        }
    }

    /**
     * Starts the fetch/pull/push server, listening on the loopback interface only - the protocol
     * has no authentication, so accepting connections from other machines has to be an explicit
     * choice, see {@link #startServer(int, boolean)}.
     */
    public void startServer(int port) {
        this.startServer(port, false);
    }

    /**
     * Starts the fetch/pull/push server and blocks, running its accept loop, until
     * {@link #stopServer()} is called.
     *
     * @param allInterfaces listen on all network interfaces instead of loopback only. The protocol
     *                      is unauthenticated: anyone who can reach the port can read the whole
     *                      repository and push into it.
     */
    public void startServer(int port, boolean allInterfaces) {
        owner.checkInitialized();

        if (!this.serverLock.tryLock())
            throw new EccoException("Server is already running.");

        try (ServerSocketChannel ssChannel = ServerSocketChannel.open()) {
            this.ssChannel = ssChannel;
            this.serverRunning = true;
            this.serverShutdown = false;

            ssChannel.configureBlocking(true);
            ssChannel.socket().bind(allInterfaces ? new InetSocketAddress(port) : new InetSocketAddress(InetAddress.getLoopbackAddress(), port));
            this.serverPort = ssChannel.socket().getLocalPort();

            String scope = allInterfaces ? " (all interfaces)" : " (local connections only)";
            LOGGER.info("Server started on port " + port + scope + ".");
            owner.listeners.fireServerEvent("Server started on port " + port + scope + ".");
            owner.listeners.fireServerStartedEvent(port);

            while (!serverShutdown) {
                try (SocketChannel sChannel = ssChannel.accept()) {
                    ObjectOutputStream oos = new ObjectOutputStream(sChannel.socket().getOutputStream());
                    ObjectInputStream ois = SyncObjectStreams.newObjectInputStream(sChannel.socket().getInputStream());


                    // determine if it is a push (receive data) or a pull (send data)
                    String command = (String) ois.readObject();
                    LOGGER.info("COMMAND: " + command);
                    owner.listeners.fireServerEvent("New connection from " + sChannel.getRemoteAddress() + " with command '" + command + "'.");

                    // Every repository/transaction section below runs under the EccoService monitor,
                    // like EccoService's own synchronized operations: the transaction strategy keeps
                    // one shared transaction state, so a request interleaving with e.g. a commit from
                    // the GUI would corrupt it. Only those sections - the network I/O stays outside,
                    // so a slow peer can't stall the rest of the application. (EccoService.
                    // startServer() used to be synchronized instead, holding the monitor for the
                    // server's whole lifetime and blocking every other operation until it stopped.)
                    switch (command) {
                        case "SETTINGS": { // how this repository reads files, for a pull to compare
                            // the settings, or why they cannot be told: a closed connection is taken
                            // for an older server, which was then synced with unchecked
                            Object reply;
                            synchronized (owner) {
                                try {
                                    reply = new HashMap<>(owner.repositorySettings());
                                } catch (RuntimeException e) {
                                    reply = "Could not tell its settings: " + messages(e);
                                }
                            }
                            oos.writeObject(reply);
                            oos.flush();
                            sChannel.shutdownOutput();
                            break;
                        }
                        case "MERGE_SETTINGS": { // before a push: whether this repository reads the files the same way
                            @SuppressWarnings("unchecked")
                            Map<String, String> settings = (Map<String, String>) ois.readObject();
                            @SuppressWarnings("unchecked")
                            Set<String> plugins = (Set<String>) ois.readObject();
                            String remoteName = (String) ois.readObject();
                            String reply;
                            synchronized (owner) {
                                try {
                                    owner.takeOverSettings(owner.settingsToTakeOver(plugins, settings, owner.repositorySettings(),
                                            owner.pluginsWithFiles(), "push to '" + remoteName + "'"));
                                    reply = "OK";
                                } catch (EccoException e) {
                                    reply = e.getMessage();
                                } catch (RuntimeException e) { // answered, not closed (see SETTINGS)
                                    reply = "Could not compare settings: " + messages(e);
                                }
                            }
                            oos.writeObject(reply);
                            oos.flush();
                            sChannel.shutdownOutput();
                            break;
                        }
                        case "FETCH": { // if fetch, send data
                            // copy features using mem entity factory - under the service monitor
                            // (see the comment above the switch)
                            Collection<Feature> copiedFeatures;
                            synchronized (owner) {
                                owner.transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_ONLY);
                                Repository.Op repository = owner.repositoryDao.load();
                                copiedFeatures = EccoUtil.deepCopyFeatures(repository.getFeatures(), owner.entityFactory);
                                owner.transactionStrategy.end();
                            }

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

                            // compute subset repository using mem entity factory - under the service
                            // monitor (see the comment above the switch)
                            Repository.Op subsetRepository;
                            synchronized (owner) {
                                Collection<FeatureRevision> deselected = owner.parseFeatureRevisionsString(deselectedFeatureRevisionsString);
                                owner.transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_ONLY);
                                Repository.Op repository = owner.repositoryDao.load();
                                subsetRepository = repository.subset(deselected, repository.getMaxOrder(), owner.entityFactory);
                                owner.transactionStrategy.end();
                            }

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
                            // the settings the files were read with; a client from before sends none,
                            // after asking with MERGE_SETTINGS
                            Map<String, String> pushedSettings = null;
                            String pushedFrom = "a client";
                            try {
                                @SuppressWarnings("unchecked")
                                Map<String, String> settings = (Map<String, String>) ois.readObject();
                                pushedSettings = settings;
                                pushedFrom = (String) ois.readObject();
                            } catch (EOFException e) {
                                LOGGER.warning("A push without the settings its files were read with (an older ECCO, which compared them before).");
                            }

                            // copy it using this entity factory
                            Repository.Op copiedRepository = subsetRepository.copy(owner.entityFactory);

                            // merge into this repository - under the service monitor (see the
                            // comment above the switch) - if both read the files the same way:
                            // checked here, in the same transaction, also when MERGE_SETTINGS said
                            // yes - a client need not ask, and the settings could have changed since
                            String reply;
                            synchronized (owner) {
                                try {
                                    owner.transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_WRITE);
                                    Repository.Op repository = owner.repositoryDao.load();
                                    Map<String, String> takeOver = pushedSettings == null ? Map.of()
                                            : owner.settingsToTakeOver(EccoService.pluginsWithFiles(copiedRepository), pushedSettings,
                                            owner.repositorySettings(), EccoService.pluginsWithFiles(repository), "push to '" + pushedFrom + "'");
                                    repository.merge(copiedRepository);
                                    owner.repositoryDao.store(repository);
                                    owner.endTakingOverSettings(takeOver);
                                    reply = "OK";
                                } catch (RuntimeException e) {
                                    owner.rollbackIfTransactionActive();
                                    LOGGER.warning("Push refused: " + messages(e));
                                    owner.listeners.fireServerEvent("Push refused: " + messages(e));
                                    reply = messages(e);
                                }
                            }
                            // a client from before reads no answer and may have closed already
                            try {
                                oos.writeObject(reply);
                                oos.flush();
                                sChannel.shutdownOutput();
                            } catch (IOException e) {
                                LOGGER.fine("The pushing client did not wait for the answer: " + e);
                            }
                            break;
                        }
                    }
                } catch (StackOverflowError e) {
                    // a payload nested too deeply to read - sent by mistake or on purpose - fails
                    // this request only; it stopped the server
                    LOGGER.warning("Error receiving request: nested too deeply.");
                    owner.listeners.fireServerEvent("Error receiving request: nested too deeply.");
                    this.rollbackIfTransactionInProgress();
                } catch (Error e) {
                    this.rollbackIfTransactionInProgress();
                    throw e;
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
            this.serverPort = -1;
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
                    if (RemoteSyncService.connect(sChannel, socketAddressOf(remote))) {
                        ProgressInputStream progressInputStream = new ProgressInputStream(sChannel.socket().getInputStream());

                        ObjectOutputStream oos = new ObjectOutputStream(sChannel.socket().getOutputStream());
                        ObjectInputStream ois = SyncObjectStreams.newObjectInputStream(progressInputStream);

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
                        throw new EccoException("Error connecting to remote: " + remote.getName() + ": " + remote.getAddress());
                    }
                } catch (Exception e) {
                    throw new EccoException("Error during remote fetch.", e);
                }

            } else if (remote.getType() == Remote.Type.LOCAL) {
                // open parent repository - try-with-resources so a failure below (e.g. the
                // getRepository() call itself) still closes parentService instead of leaking
                // whatever open()/init() acquired on the parent repository indefinitely.
                Collection<Feature> copiedFeatures;
                try (EccoService parentService = new EccoService()) {
                    parentService.setRepositoryDir(EccoService.resolveRepositoryDir(Paths.get(remote.getAddress())));
                    // only read, so the remote stays unchanged (FetchPullLeaveRemoteUnchangedTest), as for fork
                    parentService.open();

                    // copy features
                    copiedFeatures = EccoUtil.deepCopyFeatures(parentService.getRepository().getFeatures(), owner.entityFactory);
                }

                // merge into this repository
                remote.getFeatures().clear();
                remote.getFeatures().addAll(copiedFeatures);
                owner.remoteDao.storeRemote(remote);
            }

            owner.transactionStrategy.end();
        } catch (Error e) {
            owner.rollbackIfTransactionActive();
            throw e;
        } catch (Exception e) {
            owner.rollbackIfTransactionActive();

            throw new EccoException("Error during fetch.", e);
        }
    }

    public void pull(String remoteName) {
        this.pull(remoteName, "");
    }

    /** [e]'s message and its causes', for an answer to a client: "Error during merge.: No space left on device". */
    static String messages(Throwable e) {
        List<String> messages = new ArrayList<>();
        for (Throwable t = e; t != null; t = t.getCause()) {
            String message = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
            if (!messages.contains(message))
                messages.add(message);
        }
        return String.join(": ", messages);
    }

    /**
     * The settings of the repository [remote] serves, or null from a server too old to tell - it
     * closes the connection on a request it does not know.
     */
    private Map<String, String> remoteSettings(Remote remote) throws IOException, ClassNotFoundException {
        return remoteSettings(socketAddressOf(remote), "Remote '" + remote.getName() + "'");
    }

    /** Like {@link #remoteSettings(Remote)}, of the repository served at [address]; [name] for messages. */
    @SuppressWarnings("unchecked")
    static Map<String, String> remoteSettings(InetSocketAddress address, String name) throws IOException, ClassNotFoundException {
        try (SocketChannel sChannel = SocketChannel.open()) {
            sChannel.configureBlocking(true);
            if (!RemoteSyncService.connect(sChannel, address))
                throw new EccoException("Error connecting to remote: " + name + ": " + address);
            ObjectOutputStream oos = new ObjectOutputStream(sChannel.socket().getOutputStream());
            oos.writeObject("SETTINGS");
            oos.flush();
            sChannel.shutdownOutput();
            Object reply = SyncObjectStreams.newObjectInputStream(sChannel.socket().getInputStream()).readObject();
            if (reply instanceof Map<?, ?> settings)
                return (Map<String, String>) settings;
            // not checked whether the files are read the same way: nothing is synced
            throw new EccoException(name + ": " + reply);
        } catch (EOFException e) {
            LOGGER.warning(name + " does not tell its settings (an older ECCO): not checked whether it reads files the same way.");
            return null;
        }
    }

    /**
     * Asks the repository [remote] serves whether it reads files of [plugins] the way this one does,
     * before a push - it takes over this repository's settings where it has none and none of the
     * files. A server too old to answer is pushed to as before.
     */
    private void mergeSettingsIntoRemote(Remote remote, Set<String> plugins) throws IOException, ClassNotFoundException {
        String reply;
        try (SocketChannel sChannel = SocketChannel.open()) {
            sChannel.configureBlocking(true);
            if (!RemoteSyncService.connect(sChannel, socketAddressOf(remote)))
                throw new EccoException("Error connecting to remote: " + remote.getName() + ": " + remote.getAddress());
            ObjectOutputStream oos = new ObjectOutputStream(sChannel.socket().getOutputStream());
            oos.writeObject("MERGE_SETTINGS");
            oos.writeObject(new HashMap<>(owner.repositorySettings()));
            oos.writeObject(new HashSet<>(plugins));
            oos.writeObject(remote.getName());
            oos.flush();
            sChannel.shutdownOutput();
            reply = (String) SyncObjectStreams.newObjectInputStream(sChannel.socket().getInputStream()).readObject();
        } catch (EOFException e) {
            LOGGER.warning("Remote '" + remote.getName() + "' does not compare settings (an older ECCO): not checked whether it reads files the same way.");
            return;
        }
        if (!"OK".equals(reply))
            throw new EccoException(reply);
    }

    /**
     * Sends [subsetRepository], read with [settings], to [remote] to merge - which checks the settings
     * itself, and answers whether it merged it.
     */
    void sendPush(Remote remote, Repository.Op subsetRepository, Map<String, String> settings) throws IOException, ClassNotFoundException {
        try (SocketChannel sChannel = SocketChannel.open()) {
            sChannel.configureBlocking(true);
            if (!RemoteSyncService.connect(sChannel, socketAddressOf(remote)))
                throw new EccoException("Error connecting to remote: " + remote.getAddress());
            // oos wraps the progress-tracking stream (not the raw socket stream directly) so
            // that the actual payload write below -- not just a separate scratch stream used
            // only to estimate size, see below -- is what the progress listener observes.
            ProgressOutputStream pos = new ProgressOutputStream(sChannel.socket().getOutputStream());
            ObjectOutputStream oos = new ObjectOutputStream(pos);

            oos.writeObject("PUSH");

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
            // the settings the files were read with, for the remote to check
            oos.writeObject(new HashMap<>(settings));
            oos.writeObject(remote.getName());
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

            // whether the remote merged it. A server from before reads only the repository and
            // answers nothing - it may reset the connection over the settings it did not read
            Object reply;
            try {
                reply = SyncObjectStreams.newObjectInputStream(sChannel.socket().getInputStream()).readObject();
            } catch (EOFException | SocketException e) {
                LOGGER.warning("Remote '" + remote.getName() + "' does not answer a push (an older ECCO): not known whether it merged it.");
                return;
            }
            if (!"OK".equals(reply))
                throw new EccoException("Remote '" + remote.getName() + "' did not merge the push: " + reply);
        }
    }

    /**
     * Pulls the changes from the parent repository to this repository.
     *
     * @param remoteName                       The name of the remote.
     * @param deselectedFeatureRevisionsString A string enumerating the deselected feature revisions.
     */
    public void pull(String remoteName, String deselectedFeatureRevisionsString) {
        owner.checkInitialized();

        // settings this repository takes over from the remote with its first files of a plugin
        Map<String, String> takeOver = Map.of();
        try {
            owner.transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_WRITE);

            // load remote
            Remote remote = owner.remoteDao.loadRemote(remoteName);
            if (remote == null) {
                throw new EccoException("Remote '" + remoteName + "' does not exist.");
            } else if (remote.getType() == Remote.Type.REMOTE) {

                Map<String, String> remoteSettings = this.remoteSettings(remote);
                try (SocketChannel sChannel = SocketChannel.open()) {
                    sChannel.configureBlocking(true);
                    if (RemoteSyncService.connect(sChannel, socketAddressOf(remote))) {
                        ProgressInputStream progressInputStream = new ProgressInputStream(sChannel.socket().getInputStream());

                        ObjectOutputStream oos = new ObjectOutputStream(sChannel.socket().getOutputStream());
                        ObjectInputStream ois = SyncObjectStreams.newObjectInputStream(progressInputStream);

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

                        // merge into this repository - if both read the files the same way
                        Repository.Op repository = owner.repositoryDao.load();
                        if (remoteSettings != null)
                            takeOver = owner.settingsToTakeOver(EccoService.pluginsWithFiles(copiedRepository), remoteSettings,
                                    owner.repositorySettings(), EccoService.pluginsWithFiles(repository), "pull from '" + remote.getName() + "'");
                        repository.merge(copiedRepository);
                        owner.repositoryDao.store(repository);
                    } else {
                        throw new EccoException("Error connecting to remote: " + remote.getName() + ": " + remote.getAddress());
                    }
                } catch (Exception e) {
                    throw new EccoException("Error during remote pull.", e);
                }

            } else if (remote.getType() == Remote.Type.LOCAL) {
                // open parent repository - try-with-resources so a failure below still closes
                // parentService instead of leaking whatever open()/init() acquired indefinitely.
                Repository.Op subsetParentRepository;
                Map<String, String> parentSettings;
                try (EccoService parentService = new EccoService()) {
                    parentService.setRepositoryDir(EccoService.resolveRepositoryDir(Paths.get(remote.getAddress())));
                    // only read, in a read-only transaction, so the remote stays unchanged (FetchPullLeaveRemoteUnchangedTest)
                    parentService.open();
                    parentSettings = parentService.repositorySettings();

                    // create subset repository
                    try {
                        parentService.transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_ONLY);

                        Repository.Op parentRepository = parentService.repositoryDao.load();
                        subsetParentRepository = parentRepository.subset(parentService.parseFeatureRevisionsString(deselectedFeatureRevisionsString), parentRepository.getMaxOrder(), owner.entityFactory);

                        parentService.transactionStrategy.end();
                    } catch (Error e) {
                        parentService.rollbackIfTransactionActive();
                        throw e;
                    } catch (Exception e) {
                        parentService.rollbackIfTransactionActive();

                        throw new EccoException("Error during local pull.", e);
                    }
                }

                // merge into this repository - if both read the files the same way
                Repository.Op repository = owner.repositoryDao.load();
                takeOver = owner.settingsToTakeOver(EccoService.pluginsWithFiles(subsetParentRepository), parentSettings,
                        owner.repositorySettings(), EccoService.pluginsWithFiles(repository), "pull from '" + remote.getName() + "'");
                repository.merge(subsetParentRepository);
                owner.repositoryDao.store(repository);
            }

            owner.endTakingOverSettings(takeOver);
        } catch (Error e) {
            owner.rollbackIfTransactionActive();
            throw e;
        } catch (Exception e) {
            owner.rollbackIfTransactionActive();

            throw new EccoException("Error during pull.", e);
        }
    }


    public void push(String remoteName) {
        this.push(remoteName, "");
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
                throw new EccoException("Remote '" + remoteName + "' does not exist.");
            } else if (remote.getType() == Remote.Type.REMOTE) {

                // compute subset repository using mem entity factory
                owner.transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_ONLY);
                Repository.Op repository = owner.repositoryDao.load();
                Repository.Op subsetRepository = repository.subset(owner.parseFeatureRevisionsString(deselectedFeatureRevisionsString), repository.getMaxOrder(), owner.entityFactory);
                owner.transactionStrategy.end();
                // the remote merges only files it reads the same way - asked before sending anything
                this.mergeSettingsIntoRemote(remote, EccoService.pluginsWithFiles(subsetRepository));

                try {
                    this.sendPush(remote, subsetRepository, owner.repositorySettings());
                } catch (EccoException e) {
                    throw e;
                } catch (Exception e) {
                    throw new EccoException("Error during remote push.", e);
                }

            } else if (remote.getType() == Remote.Type.LOCAL) {
                // open parent repo - try-with-resources so a failure below still closes
                // parentService instead of leaking whatever open()/init() acquired indefinitely.
                try (EccoService parentService = new EccoService()) {
                    parentService.setRepositoryDir(EccoService.resolveRepositoryDir(Paths.get(remote.getAddress())));
                    parentService.open();

                    // create subset repository
                    Repository.Op repository = owner.repositoryDao.load();
                    Repository.Op subsetRepository = repository.subset(owner.parseFeatureRevisionsString(deselectedFeatureRevisionsString), repository.getMaxOrder(), parentService.entityFactory);

                    // merge into parent repository - if both read the files the same way
                    try {
                        parentService.transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_WRITE);

                        Repository.Op parentRepository = parentService.repositoryDao.load();
                        Map<String, String> takeOver = parentService.settingsToTakeOver(EccoService.pluginsWithFiles(subsetRepository),
                                owner.repositorySettings(), parentService.repositorySettings(), EccoService.pluginsWithFiles(parentRepository),
                                "push to '" + remote.getName() + "'");
                        parentRepository.merge(subsetRepository);
                        parentService.repositoryDao.store(parentRepository);

                        parentService.endTakingOverSettings(takeOver);
                    } catch (Error e) {
                        parentService.rollbackIfTransactionActive();
                        throw e;
                    } catch (Exception e) {
                        parentService.rollbackIfTransactionActive();

                        throw new EccoException("Error during local push.", e);
                    }
                }
            }

            owner.transactionStrategy.end();
        } catch (Error e) {
            owner.rollbackIfTransactionActive();
            throw e;
        } catch (Exception e) {
            owner.rollbackIfTransactionActive();

            throw new EccoException("Error during push.", e);
        }
    }

}
