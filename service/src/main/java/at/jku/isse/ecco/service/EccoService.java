package at.jku.isse.ecco.service;

import at.jku.isse.ecco.*;
import at.jku.isse.ecco.adapter.*;
import at.jku.isse.ecco.adapter.dispatch.*;
import at.jku.isse.ecco.artifact.*;
import at.jku.isse.ecco.core.*;
import at.jku.isse.ecco.dao.*;
import at.jku.isse.ecco.feature.*;
import at.jku.isse.ecco.module.*;
import at.jku.isse.ecco.repository.*;
import at.jku.isse.ecco.service.listener.*;
import at.jku.isse.ecco.storage.*;
import at.jku.isse.ecco.storage.mem.core.*;
import at.jku.isse.ecco.storage.mem.dao.*;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.tree.*;
import com.google.inject.Module;
import com.google.inject.*;
import com.google.inject.name.*;

import java.io.*;
import java.net.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.locks.*;
import java.util.function.*;
import java.util.logging.*;
import java.util.stream.*;

import static com.google.common.base.Preconditions.*;

/**
 * A service class that gives access to high level operations like init, fork, pull, push, etc.
 */
public class EccoService implements ProgressInputStream.ProgressListener, ProgressOutputStream.ProgressListener, ReadListener, WriteListener, Closeable {
    private static final Logger LOGGER = Logger.getLogger(EccoService.class.getName());
    public static final String KEY_USER_NAME = "user.name";
    public static final String DEFAULT_USER_NAME = "anonymous";

    public enum Operation {
        OPEN, INIT, FORK, CLOSE, COMMIT, CHECKOUT, FETCH, PULL, PUSH, SERVER, OTHER
    }

    public static final String ORIGIN_REMOTE_NAME = "origin";

    public static final String ECCO_PROPERTIES_FILE = "ecco.properties";
    public static final String ECCO_PROPERTIES_STORAGE = "ecco.storage";

    public static final Path REPOSITORY_DIR_NAME = Paths.get(".ecco");
    public static final Path DEFAULT_BASE_DIR = Paths.get("");
    public static final Path DEFAULT_REPOSITORY_DIR = DEFAULT_BASE_DIR.resolve(REPOSITORY_DIR_NAME);
    public static final Path CONFIG_FILE_NAME = Paths.get(".config");
    public static final Path WARNINGS_FILE_NAME = Paths.get(".warnings");
    public static final Path HASHES_FILE_NAME = Paths.get(".hashes");


    private final Properties properties = new Properties();

    private Path baseDir;
    private Path repositoryDir;

    public Properties getProperties() {
        return this.properties;
    }

    public Path getBaseDir() {
        return this.baseDir;
    }

    public void setBaseDir(Path baseDir) {
        checkNotNull(baseDir);

        if (this.baseDir == null || !this.baseDir.equals(baseDir)) {
            this.baseDir = baseDir;
            this.fireStatusChangedEvent();
        }
    }

    public Path getRepositoryDir() {
        return this.repositoryDir;
    }

    public void setRepositoryDir(Path repositoryDir) {
        checkNotNull(repositoryDir);

        if (this.initialized)
            throw new EccoException("The repository directory cannot be changed after the service has been initialized.");

        if (!this.repositoryDir.equals(repositoryDir)) {
            this.repositoryDir = repositoryDir;
            this.fireStatusChangedEvent();
        }
    }

    public EntityFactory getEntityFactory() {
        return entityFactory;
    }

    public void setEntityFactory(EntityFactory entityFactory) {
        this.entityFactory = entityFactory;
    }

    public DispatchReader getReader() {
        return reader;
    }

    public void setReader(DispatchReader reader) {
        this.reader = reader;
    }

    // TODO: set current operation. update progress during operations (instead of just relaying the progress from input and output streams) and notify listeners.
    private Operation currentOperation;
    private int maxAbsoluteProgress;


    /**
     * Creates the service and tries to detect an existing repository automatically using {@link #detectRepository(Path path) detectRepository}. If no existing repository was found the base directory (directory from which files are committed and checked out) and repository directory (directory at which the repository data is stored) are set to their defaults:
     *
     * <br>Base Directory (baseDir) Default: current directory
     * <br>Repository Directory (repoDir) Default: .ecco
     */
    public EccoService() {
        this(DEFAULT_BASE_DIR, DEFAULT_REPOSITORY_DIR);

        this.detectRepository();
    }

    /**
     * Creates the service and sets the base directory to {@code baseDir} and the repository dir to "&lt;baseDir&gt;/.ecco".
     *
     * @param baseDir The base directory.
     */
    public EccoService(Path baseDir) {
        this(baseDir, baseDir.resolve(REPOSITORY_DIR_NAME));
    }

    /**
     * Creates the service and sets the base directory to {@code baseDir} and the repository dir to {@code repositoryDir}.
     *
     * @param baseDir       The base directory.
     * @param repositoryDir The repository directory.
     */
    public EccoService(Path baseDir, Path repositoryDir) {
        this.baseDir = baseDir;
        this.repositoryDir = repositoryDir;

        LOGGER.info("Logging to: " + Arrays.stream(LOGGER.getHandlers()).map(Object::toString).collect(Collectors.joining(", ")));

        // load properties file
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(ECCO_PROPERTIES_FILE);
        if (inputStream != null) {
            try {
                this.properties.load(inputStream);
            } catch (IOException e) {
                throw new EccoException("Could not load properties from file '" + ECCO_PROPERTIES_FILE + "'.", e);
            }
        } else {
            throw new EccoException("Property file '" + ECCO_PROPERTIES_FILE + "' not found in the classpath.");
        }
        LOGGER.config("PROPERTIES: " + this.properties);
    }


    private Collection<ArtifactPlugin> artifactPlugins;
    private Collection<StoragePlugin> dataPlugins;
    private StoragePlugin dataPlugin;

    private Injector injector;

    private boolean initialized = false;

    public boolean isInitialized() {
        return this.initialized;
    }

    private final MemEntityFactory memEntityFactory = new MemEntityFactory();

    @Inject
    private DispatchReader reader;
    @Inject
    private DispatchWriter writer;
    @Inject
    private EntityFactory entityFactory;
    @Inject
    private TransactionStrategy transactionStrategy;

    // DAOs
    @Inject
    private RepositoryDao repositoryDao;
    @Inject
    private RemoteDao remoteDao;

    // # LISTENERS #####################################################################################################

    private final Collection<EccoListener> listeners = new ArrayList<>();

    public void addListener(EccoListener listener) {
        this.listeners.add(listener);
    }

    public void removeListener(EccoListener listener) {
        this.listeners.remove(listener);
    }


    // relay reader, writer, and progress events

    @Override
    public void readProgressEvent(double progress, long bytes) {
        this.fireOperationProgressEvent("READ", progress);
    }

    @Override
    public void writeProgressEvent(double progress, long bytes) {
        this.fireOperationProgressEvent("WRITE", progress);
    }

    @Override
    public void fileReadEvent(Path file, ArtifactReader reader) {
        this.fireReadEvent(file, reader);
    }

    @Override
    public void fileWriteEvent(Path file, ArtifactWriter writer) {
        this.fireWriteEvent(file, writer);
    }


    // service events

    private void fireStatusChangedEvent() {
        for (EccoListener listener : this.listeners) {
            listener.statusChangedEvent(this);
        }
    }

    private void fireOperationProgressEvent(String operationString, double progress) {
        for (EccoListener listener : this.listeners) {
            listener.operationProgressEvent(this, operationString, progress);
        }
    }

    private void fireReadEvent(Path path, ArtifactReader reader) {
        for (ReadListener listener : this.listeners) {
            listener.fileReadEvent(path, reader);
        }
    }

    private void fireWriteEvent(Path path, ArtifactWriter writer) {
        for (WriteListener listener : this.listeners) {
            listener.fileWriteEvent(path, writer);
        }
    }


    // repository events

    private void fireCommitsChangedEvent(Commit commit) {
        for (EccoListener listener : this.listeners) {
            listener.commitsChangedEvent(this, commit);
        }
    }

    private void fireAssociationSelectedEvent(Association association) {
        for (EccoListener listener : this.listeners) {
            listener.associationSelectedEvent(this, association);
        }
    }


    // server events

    private void fireServerEvent(String message) {
        for (ServerListener listener : this.listeners) {
            listener.serverEvent(this, message);
        }
    }

    private void fireServerStartedEvent(int port) {
        for (ServerListener listener : this.listeners) {
            listener.serverStartEvent(this, port);
        }
    }

    private void fireServerStoppedEvent() {
        for (ServerListener listener : this.listeners) {
            listener.serverStopEvent(this);
        }
    }


    // # SETTINGS ######################################################################################################

//	public int getMaxOrder() {
//		return this.settingsDao.loadMaxOrder();
//	}
//
//	public void setMaxOrder(int maxOrder) {
//		try {
//			this.transactionStrategy.begin();
//
//			this.settingsDao.storeMaxOrder(maxOrder);
//
//			this.transactionStrategy.end();
//		} catch (Exception e) {
//			this.transactionStrategy.rollback();
//
//			throw new EccoException("Error changing settings.", e);
//		}
//	}


    // # REPOSITORY SERVICES ###########################################################################################

    /**
     * Checks if the repository directory (either given as a constructor parameter or detected using {@link #detectRepository(Path path) detectRepository}) exists.
     *
     * @return True if the repository directory exists, false otherwise.
     */
    public boolean repositoryDirectoryExists() {
        return Files.exists(this.repositoryDir);
    }

    /**
     * Checks if a repository exists at the current path (the working directory from which ecco was started) or any of its parents.
     *
     * @return True if a repository was found, false otherwise.
     */
    public boolean repositoryExists() {
        return this.repositoryExists(Paths.get(""));
    }

    /**
     * Checks if a repository exists at the given path or any of its parents.
     *
     * @param path The path at which to start looking for a repository.
     * @return True if a repository was found, false otherwise.
     */
    public boolean repositoryExists(Path path) {
        if (!Files.exists(path.resolve(REPOSITORY_DIR_NAME))) { // repository was not found
            try {
                Path parent = path.toRealPath().getParent();
                if (parent != null) // if the current directory has a parent
                    return this.repositoryExists(parent); // try to find a repository in the parent
            } catch (IOException e) {
                e.printStackTrace();
            }
            return false;
        } else { // repository was found
            return true;
        }
    }

    /**
     * Detects the repository directory automatically by checking the given path and all its parents for the existence of a repository.
     * If a repository was found the repository directory is set accordingly, otherwise the current repository directory is left untouched.
     *
     * @param path The path at which to start looking for a repository.
     * @return True if a repository was found, false otherwise.
     */
    public boolean detectRepository(Path path) {
        if (!Files.exists(path.resolve(REPOSITORY_DIR_NAME))) { // repository was not found
            //Path parent = current.normalize().getParent();
            try {
                Path parent = path.toRealPath().getParent();
                if (parent != null) // if the current directory has a parent
                    return this.detectRepository(parent); // try to find a repository in the parent
            } catch (IOException e) {
                e.printStackTrace();
            }
            return false;
        } else { // repository was found
            //this.baseDir = current;
            this.setBaseDir(path);
            //this.repositoryDir = current.resolve(REPOSITORY_DIR_NAME);
            this.setRepositoryDir(path.resolve(REPOSITORY_DIR_NAME));
            return true;
        }
    }

    /**
     * Detects the repository directory automatically by checking the current path (the working directory from which ecco was started) and all its parents for the existence of a repository.
     * If a repository was found the repository directory is set accordingly, otherwise the current repository directory is left untouched.
     *
     * @return True if a repository was found, false otherwise.
     */
    public boolean detectRepository() {
        return this.detectRepository(Paths.get(""));
    }


    protected void checkInitialized() {
        if (!this.isInitialized()) {
            throw new EccoException("Service is not initialized. No repository is loaded.");
        }
    }

    protected Collection<Module> initializeService() {
        if (this.isInitialized()) {
            throw new EccoException("Repository is already open.");
        }

        LOGGER.config("PROPERTIES: " + this.properties);
        if (this.properties.getProperty(ECCO_PROPERTIES_STORAGE) == null) {
            throw new EccoException("No data plugin specified.");
        }

        // artifact adapter modules
        List<Module> artifactModules = new ArrayList<>();
        List<Module> allArtifactModules = new ArrayList<>();
        this.artifactPlugins = new ArrayList<>();

        for (ArtifactPlugin artifactPlugin : ArtifactPlugin.getArtifactPlugins()) {
            artifactModules.add(artifactPlugin.getModule());
            this.artifactPlugins.add(artifactPlugin);
            allArtifactModules.add(artifactPlugin.getModule());
            artifactPlugin.init();
        }

        LOGGER.config("ARTIFACT PLUGINS: " + artifactModules.toString());
        LOGGER.config("ALL ARTIFACT PLUGINS: " + allArtifactModules.toString());
        if (artifactModules.isEmpty())
            throw new EccoException("At least one artifact plugin must be configured.");

        // storage modules
        String storagePluginId = this.properties.getProperty(ECCO_PROPERTIES_STORAGE);
        List<Module> storageModules = new ArrayList<>();
        List<Module> allStorageModules = new ArrayList<>();
        this.dataPlugins = new ArrayList<>();
        for (StoragePlugin dataPlugin : StoragePlugin.getDataPlugins()) {
            if (dataPlugin.getPluginId().equals(storagePluginId)) {
                storageModules.add(dataPlugin.getModule());
                this.dataPlugin = dataPlugin;
            }
            this.dataPlugins.add(dataPlugin);
            allStorageModules.add(dataPlugin.getModule());
        }
        LOGGER.config("STORAGE PLUGINS: " + storageModules.toString());
        LOGGER.config("ALL STORAGE PLUGINS: " + allStorageModules.toString());
        if (storageModules.size() != 1)
            throw new EccoException("Exactly one storage plugin must be configured.");

        // put modules together
        List<Module> modules = new ArrayList<>();
        modules.add(new DispatchModule());
        modules.addAll(artifactModules);
        modules.addAll(storageModules);

        return modules;
    }

    /**
     * Initializes the service.
     */
    public synchronized void open() {
        LOGGER.info("OPEN()");

        if (!this.repositoryDirectoryExists()) {
            throw new EccoException("Repository does not exist.");
        }

        LOGGER.config("BASE_DIR: " + this.baseDir);
        LOGGER.config("REPOSITORY_DIR: " + this.repositoryDir);

        Collection<Module> modules = this.initializeService();

        for (Module module : modules) {
            LOGGER.info(module.toString());
        }

        // create settings module
        final Module settingsModule = new AbstractModule() {
            @Override
            protected void configure() {
                bind(Path.class).annotatedWith(Names.named("repositoryDir")).toInstance(EccoService.this.repositoryDir);
            }
        };
        modules.add(settingsModule);


        // create injector
        Injector injector = Guice.createInjector(modules);

        // inject
        try {
            injector.injectMembers(this);
        } catch (CreationException creationException) {
            throw new EccoException("Error during dependency injection. The storage plugin may be faulty.", creationException);
        }

        this.injector = injector;

        this.transactionStrategy.open();

        this.repositoryDao.init();
        this.remoteDao.init();

        this.reader.init();

        // add default ignore patterns
        this.reader.addIgnorePattern(REPOSITORY_DIR_NAME.toString());
        this.reader.addIgnorePattern(CONFIG_FILE_NAME.toString());
        this.reader.addIgnorePattern(WARNINGS_FILE_NAME.toString());
        this.reader.addIgnorePattern(HASHES_FILE_NAME.toString());

        this.reader.addListener(this);
        this.writer.addListener(this);

        this.initialized = true;

        this.fireStatusChangedEvent();

        LOGGER.info("Repository opened.");

    }

    /**
     * Properly shuts down the service.
     */
    @Override
    public synchronized void close() {
        LOGGER.info("CLOSE()");

        if (!this.initialized)
            return;

        this.initialized = false;

        this.reader.removeListener(this);
        this.writer.removeListener(this);

        this.repositoryDao.close();
        this.remoteDao.close();

        this.transactionStrategy.close();

        this.fireStatusChangedEvent();

        LOGGER.info("Repository closed.");
    }


    // # UTILS #########################################################################################################

    public synchronized Remote addRemote(String name, String address) {
        this.checkInitialized();

        Path path;
        try {
            path = Paths.get(address);
        } catch (InvalidPathException | NullPointerException ex) {
            path = null;
        }
//		if (path != null) {
//			return this.addRemote(name, address, Remote.Type.LOCAL);
//		} else
        if (address.matches("[a-zA-Z]+:[0-9]+")) {
            return this.addRemote(name, address, Remote.Type.REMOTE);
        } else if (path != null) {
            return this.addRemote(name, address, Remote.Type.LOCAL);
        } else {
            throw new EccoException("Invalid remote address provided.");
        }
    }

    public synchronized Remote addRemote(String name, String address, Remote.Type type) {
        this.checkInitialized();

        try {
            this.transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_WRITE);

            if (this.getRemote(name) != null)
                throw new EccoException("Remote with this name already exists.");

            //Remote.Type type = Remote.Type.valueOf(typeString);
            Remote remote = this.entityFactory.createRemote(name, address, type);
            remote = this.remoteDao.storeRemote(remote);

            this.transactionStrategy.end();

            return remote;
        } catch (Exception e) {
            this.transactionStrategy.rollback();

            throw new EccoException("Error adding remote.", e);
        }
    }

    public synchronized void removeRemote(String name) {
        this.checkInitialized();

        try {
            this.transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_WRITE);

            this.remoteDao.removeRemote(name);

            this.transactionStrategy.end();
        } catch (Exception e) {
            this.transactionStrategy.rollback();

            throw new EccoException("Error removing remote.", e);
        }
    }

    public synchronized Remote getRemote(String name) {
        this.checkInitialized();

        try {
            this.transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_ONLY);

            Remote remote = this.remoteDao.loadRemote(name);

            this.transactionStrategy.end();

            return remote;
        } catch (Exception e) {
            this.transactionStrategy.rollback();

            throw new EccoException("Error retrieving remote.", e);
        }
    }

    public synchronized Collection<Remote> getRemotes() {
        this.checkInitialized();

        try {
            this.transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_ONLY);

            Collection<Remote> remotes = this.remoteDao.loadAllRemotes();

            this.transactionStrategy.end();

            return remotes;
        } catch (Exception e) {
            this.transactionStrategy.rollback();

            throw new EccoException("Error retrieving remotes.", e);
        }
    }


    public synchronized Repository getRepository() {
        this.checkInitialized();

        try {
            this.repositoryDao.init();
            this.transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_ONLY);
            Repository repository = this.repositoryDao.load();
            this.transactionStrategy.end();
            return repository;
        } catch (EccoException e) {
            this.transactionStrategy.rollback();
            throw new EccoException("Error when retrieving repository.", e);
        }
    }

    /**
     * Get all commit objects.
     *
     * @return Collection containing all commit objects.
     */
    public synchronized Collection<Commit> getCommits() {
        this.checkInitialized();

        try {
            return getRepository().getCommits();
        } catch (EccoException e) {
            this.transactionStrategy.rollback();
            throw new EccoException("Error when retrieving commits.", e);
        }
    }


    /**
     * Parses the given configuration string (see {@link Configuration#CONFIGURATION_STRING_REGULAR_EXPRESSION} to create a configuration object.
     * The configuration object contains feature revision object instances of this repository in case they already exist, otherwise temporary feature and feature revision objects are created.
     *
     * @param configurationString The configuration string to parse.
     * @return The configuration object.
     */
    public Configuration parseConfigurationString(String configurationString) {
        checkNotNull(configurationString);

        if (!configurationString.matches(Configuration.CONFIGURATION_STRING_REGULAR_EXPRESSION))
            throw new EccoException("Invalid configuration string provided.");

        if (configurationString.isEmpty()) {
            return this.entityFactory.createConfiguration(new FeatureRevision[0]);
        }

        try {
            this.transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_ONLY);

            Repository.Op repository = this.repositoryDao.load();

            Set<FeatureRevision> featureRevisions = new HashSet<>();

            String[] featureRevisionStrings = configurationString.split(",");
            for (String featureRevisionString : featureRevisionStrings) {
                featureRevisionString = featureRevisionString.trim();

                if (featureRevisionString.contains(".")) { // use specified feature revision
                    String[] pair = featureRevisionString.split("\\.");
                    String featureName = pair[0];
                    String featureRevisionId = pair[1];

                    Feature feature = getFeature(repository, featureName);

                    FeatureRevision featureRevision = feature.getRevision(featureRevisionId);
                    if (featureRevision == null) {
                        featureRevision = feature.addRevision(featureRevisionId);
                    }

                    featureRevisions.add(featureRevision);
                } else if (featureRevisionString.endsWith("'")) { // create new feature revision for feature
                    String featureName = featureRevisionString.substring(0, featureRevisionString.length() - 1);

                    Feature feature = getFeature(repository, featureName);

                    FeatureRevision featureRevision = feature.addRevision(UUID.randomUUID().toString());
                    featureRevisions.add(featureRevision);
                } else { // use most recent feature revision of feature (or create a new one if none existed so far)
                    String featureName = featureRevisionString;

                    Feature feature = getFeature(repository, featureName);

                    FeatureRevision featureRevision = feature.getLatestRevision();
                    if (featureRevision == null) {
                        featureRevision = feature.addRevision(UUID.randomUUID().toString());
                    }

                    featureRevisions.add(featureRevision);
                }
            }

            Configuration configuration = this.entityFactory.createConfiguration(featureRevisions.toArray(new FeatureRevision[0]));

            this.transactionStrategy.end();

            return configuration;
        } catch (Exception e) {
            this.transactionStrategy.rollback();

            throw new EccoException("Error parsing configuration string: " + configurationString, e);
        }
    }

    private Feature getFeature(Repository.Op repository, String featureName) {
        Feature feature;
        if (featureName.startsWith("[") && featureName.endsWith("]")) { // feature id
            featureName = featureName.substring(1, featureName.length() - 1);
            feature = repository.getFeature(featureName);
            if (feature == null) {
                //throw new EccoException("Feature id does not exist. Use feature name instead if you want to create a new feature.");
                // create temporary feature object
                feature = this.entityFactory.createFeature(featureName, featureName);
            } else {
                feature = this.entityFactory.createFeature(feature.getId(), feature.getName());
            }
        } else { // feature name
            Collection<Feature> features = repository.getFeaturesByName(featureName);
            if (features.isEmpty()) {
                //feature = this.addFeature(UUID.randomUUID().toString(), featureName);
                // create temporary feature object
                feature = this.entityFactory.createFeature(UUID.randomUUID().toString(), featureName);
            } else if (features.size() == 1) {
                feature = features.iterator().next();
                feature = this.entityFactory.createFeature(feature.getId(), feature.getName());
            } else {
                throw new EccoException("Feature name is not unique. Use feature id instead.");
            }
        }
        return feature;
    }


    public Collection<FeatureRevision> parseFeatureRevisionsString(String featureRevisionsString) {
        if (featureRevisionsString == null)
            throw new EccoException("No feature revisions string provided.");

        if (!featureRevisionsString.matches("(((\\[[a-zA-Z0-9_-]+\\])|([a-zA-Z0-9_-]+))(\\.([a-zA-Z0-9_-])+)(\\s*,\\s*((\\[[a-zA-Z0-9_-]+\\])|([a-zA-Z0-9_-]+))(\\.([a-zA-Z0-9_-])+))*)?"))
            throw new EccoException("Invalid feature revisions string provided.");

        try {
            this.transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_ONLY);

            Collection<FeatureRevision> featureRevisions = new ArrayList<>();

            if (featureRevisionsString.isEmpty()) {
                this.transactionStrategy.end();
                return featureRevisions;
            }

            Repository.Op repository = this.repositoryDao.load();

            String[] featureRevisionsStrings = featureRevisionsString.split(",");
            for (String featureRevisionString : featureRevisionsStrings) {
                featureRevisionString = featureRevisionString.trim();

                String[] pair = featureRevisionString.split("\\.");
                String featureName = pair[0];
                String featureRevisionId = pair[1];

                Feature feature;
                if (featureName.startsWith("[") && featureName.endsWith("]")) { // id
                    feature = repository.getFeature(featureName);
                    if (feature == null) {
                        throw new EccoException("Feature with id does not exist: " + featureName);
                    }
                } else { // name
                    Collection<Feature> features = repository.getFeaturesByName(featureName);
                    if (features.isEmpty()) {
                        throw new EccoException("Feature with name does not exist: " + featureName);
                    } else if (features.size() == 1) {
                        feature = features.iterator().next();
                    } else {
                        throw new EccoException("Feature name is not unique. Use feature id instead.");
                    }
                }

                FeatureRevision featureRevision = feature.getRevision(featureRevisionId);
                if (featureRevision != null) {
                    featureRevisions.add(featureRevision);
                } else {
                    throw new EccoException("Feature revision with id does not exist: " + featureRevisionId);
                }
            }

            this.transactionStrategy.end();

            return featureRevisions;
        } catch (Exception e) {
            this.transactionStrategy.rollback();

            throw new EccoException("Error parsing feature revisions string: " + featureRevisionsString, e);
        }
    }


    // # CORE SERVICES #################################################################################################


    // DISTRIBUTED OPERATIONS //////////////////////////////////////////////////////////////////////////////////////////

    private ServerSocketChannel ssChannel = null;
    private boolean serverShutdown = false;
    private boolean serverRunning = false;
    private final Lock serverLock = new ReentrantLock();

    public boolean serverRunning() {
        return this.serverRunning;
    }

    public synchronized void startServer(int port) {
        this.checkInitialized();

        if (!this.serverLock.tryLock())
            throw new EccoException("Server is already running.");
//		if (this.serverRunning)
//			throw new EccoException("Server is already running.");

        try (ServerSocketChannel ssChannel = ServerSocketChannel.open()) {
            this.ssChannel = ssChannel;
            this.serverRunning = true;
            this.serverShutdown = false;

            ssChannel.configureBlocking(true);
            ssChannel.socket().bind(new InetSocketAddress(port));

            LOGGER.info("Server started on port " + port + ".");
            this.fireServerEvent("Server started on port " + port + ".");
            this.fireServerStartedEvent(port);

            while (!serverShutdown) {
                try (SocketChannel sChannel = ssChannel.accept()) {
                    ObjectOutputStream oos = new ObjectOutputStream(sChannel.socket().getOutputStream());
                    ObjectInputStream ois = new ObjectInputStream(sChannel.socket().getInputStream());


                    // determine if it is a push (receive data) or a pull (send data)
                    String command = (String) ois.readObject();
                    LOGGER.info("COMMAND: " + command);
                    this.fireServerEvent("New connection from " + sChannel.getRemoteAddress() + " with command '" + command + "'.");

                    switch (command) {
                        case "FETCH": { // if fetch, send data
                            // copy features using mem entity factory
                            this.transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_ONLY);
                            Repository.Op repository = this.repositoryDao.load();
                            Collection<Feature> copiedFeatures = EccoUtil.deepCopyFeatures(repository.getFeatures(), this.memEntityFactory);
                            this.transactionStrategy.end();


                            // send features
                            // with size:
                            ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
                            ObjectOutputStream temp_oos = new ObjectOutputStream(byteOutputStream);
                            // write object to temp_oos
                            temp_oos.writeObject(copiedFeatures);
                            // get size of data
                            int size = byteOutputStream.size();
                            // send size
                            oos.writeObject(size);
                            // send data
                            byteOutputStream.writeTo(sChannel.socket().getOutputStream());
                            byteOutputStream.close();
                            // without size:
                            //oos.writeObject(copiedFeatures);

                            break;
                        }
                        case "PULL": { // if pull, send data
                            // retrieve deselection
                            //Collection<FeatureRevision> deselected = (Collection<FeatureRevision>) ois.readObject();
                            String deselectedFeatureRevisionsString = (String) ois.readObject();
                            Collection<FeatureRevision> deselected = this.parseFeatureRevisionsString(deselectedFeatureRevisionsString);

                            // compute subset repository using mem entity factory
                            this.transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_ONLY);
                            Repository.Op repository = this.repositoryDao.load();
                            Repository.Op subsetRepository = repository.subset(deselected, repository.getMaxOrder(), this.memEntityFactory);
                            this.transactionStrategy.end();


                            // send subset repository
                            // with size:
                            ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
                            ObjectOutputStream temp_oos = new ObjectOutputStream(byteOutputStream);
                            // write object to temp_oos
                            temp_oos.writeObject(subsetRepository);
                            // get size of data
                            int size = byteOutputStream.size();
                            // send size
                            oos.writeObject(size);
                            // send data
                            byteOutputStream.writeTo(sChannel.socket().getOutputStream());
                            byteOutputStream.close();
                            // without size:
                            //oos.writeObject(subsetRepository);

                            break;
                        }
                        case "PUSH": { // if push, receive data
                            // retrieve repository
                            Repository.Op subsetRepository = (Repository.Op) ois.readObject();

                            // copy it using this entity factory
                            Repository.Op copiedRepository = subsetRepository.copy(this.entityFactory);

                            // merge into this repository
                            this.transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_WRITE);
                            Repository.Op repository = this.repositoryDao.load();
                            repository.merge(copiedRepository);
                            this.repositoryDao.store(repository);
                            this.transactionStrategy.end();
                            break;
                        }
                    }
                } catch (AsynchronousCloseException e) {
                    // server shut down
                    //e.printStackTrace();
                } catch (SocketException | ClosedChannelException e) {
                    LOGGER.warning("Error receiving request.");
                    this.fireServerEvent("Error receiving request: " + e.getMessage());
                    e.printStackTrace();
                } catch (Exception e) {
                    //throw new EccoException("Error receiving request.", e);
                    LOGGER.warning("Error receiving request.");
                    this.fireServerEvent("Error receiving request: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            throw new EccoException("Error starting server.", e);
        } finally {
            this.serverRunning = false;
            this.serverLock.unlock();
        }

        LOGGER.info("Server stopped.");
        this.fireServerEvent("Server stopped.");
        this.fireServerStoppedEvent();
    }

    public void stopServer() {
//		if (this.serverLock.tryLock()) {
//			this.serverLock.unlock();
//			throw new EccoException("Server is not running.");
//		}
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


    public synchronized void fetch(String remoteName) {
        this.checkInitialized();

        try {
            this.transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_WRITE);

            // load remote
            Remote remote = this.remoteDao.loadRemote(remoteName);
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


                        int size = (Integer) ois.readObject();
                        progressInputStream.setMaxBytes(size);
                        progressInputStream.resetProgress();
                        progressInputStream.addListener(this);

                        // retrieve features
                        @SuppressWarnings("unchecked")
                        Collection<Feature> features = (Collection<Feature>) ois.readObject();

                        progressInputStream.removeListener(this);


                        // copy it using this entity factory
                        Collection<Feature> copiedFeatures = EccoUtil.deepCopyFeatures(features, this.entityFactory);

                        // store with remote
                        remote.getFeatures().clear();
                        remote.getFeatures().addAll(copiedFeatures);
                        this.remoteDao.storeRemote(remote);
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
                Collection<Feature> copiedFeatures = EccoUtil.deepCopyFeatures(parentService.getRepository().getFeatures(), this.entityFactory);

                // close parent repository
                parentService.close();

                // merge into this repository
                remote.getFeatures().clear();
                remote.getFeatures().addAll(copiedFeatures);
                this.remoteDao.storeRemote(remote);
            }

            this.transactionStrategy.end();
        } catch (Exception e) {
            this.transactionStrategy.rollback();

            throw new EccoException("Error during fetch.", e);
        }
    }

    public synchronized void fork(String hostname, int port) {
        this.fork(hostname, port, "");
    }

    public synchronized void fork(String hostname, int port, String deselectedFeatureRevisionsString) {
        if (this.isInitialized())
            throw new EccoException("Service must not be initialized for fork operation.");
        if (this.repositoryDirectoryExists())
            throw new EccoException("A repository already exists at the given location: " + this.repositoryDir);

        Repository.Op copiedRepository;
        try (SocketChannel sChannel = SocketChannel.open()) {
            sChannel.configureBlocking(true);
            if (sChannel.connect(new InetSocketAddress(hostname, port))) {
                ProgressInputStream progressInputStream = new ProgressInputStream(sChannel.socket().getInputStream());

                ObjectOutputStream oos = new ObjectOutputStream(sChannel.socket().getOutputStream());
                ObjectInputStream ois = new ObjectInputStream(progressInputStream);

                oos.writeObject("PULL");
                oos.writeObject(deselectedFeatureRevisionsString);


                int size = (Integer) ois.readObject();
                progressInputStream.setMaxBytes(size);
                progressInputStream.resetProgress();
                progressInputStream.addListener(this);

                // retrieve remote repository
                Repository.Op subsetRepository = (Repository.Op) ois.readObject();

                progressInputStream.removeListener(this);


                // copy it using this entity factory
                copiedRepository = subsetRepository.copy(this.entityFactory);
            } else {
                throw new EccoException("Error connecting to remote: " + hostname + ":" + port);
            }
        } catch (Exception e) {
            throw new EccoException("Error during remote fork.", e);
        }

        try {
            this.init();
            this.open();

            this.transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_WRITE);

            // merge into this repository
            Repository.Op repository = this.repositoryDao.load();
            repository.merge(copiedRepository);
            this.repositoryDao.store(repository);

            // after fork add used remote as default origin remote
            Remote remote = this.entityFactory.createRemote(ORIGIN_REMOTE_NAME, hostname + ":" + port, Remote.Type.REMOTE);
            this.remoteDao.storeRemote(remote);

            this.transactionStrategy.end();
        } catch (Exception e) {
            throw new EccoException("Error during remote fork.", e);
        }
    }

    public synchronized void fork(Path originRepositoryDir) {
        this.fork(originRepositoryDir, "");
    }

    /**
     * This operation clones/forks a complete repository, i.e. all of its features, artifacts and traces.
     * It can only be executed on a not initialized repository.
     * After the operation the repository is initialized and ready to use.
     * The fork operation is like the init operation in the sense that it creates a new repository at a given location.
     *
     * @param originRepositoryDir              The directory of the repository from which to fork.
     * @param deselectedFeatureRevisionsString A string enumerating the deselected feature revisions.
     */
    public synchronized void fork(Path originRepositoryDir, String deselectedFeatureRevisionsString) {
        // check that this service has not yet been initialized and that no repository already exists,
        if (this.isInitialized())
            throw new EccoException("Service must not be initialized for fork operation.");
        if (this.repositoryDirectoryExists())
            throw new EccoException("A repository already exists at the given location: " + this.repositoryDir);

        // the repository must be initialized here or the entity factory needed during the subset operation is null
        this.init();

        // create another ecco service and init it on the parent repository directory.
        EccoService originService = new EccoService();
        originService.setRepositoryDir(originRepositoryDir);
        // create subset repository
        Repository.Op subsetOriginRepository;
        try {
            originService.open(); // TODO: init read only! add read only mode for that (also useful for other read only services on a repository such as a read only web interface REST API service).

            originService.transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_ONLY);

            Repository.Op originRepository = originService.repositoryDao.load();
            subsetOriginRepository = originRepository.subset(originService.parseFeatureRevisionsString(deselectedFeatureRevisionsString), originRepository.getMaxOrder(), this.entityFactory);

            originService.transactionStrategy.end();
        } catch (Exception e) {
            originService.transactionStrategy.rollback();

            throw new EccoException("Error during local fork.", e);
        } finally {
            // close parent repository
            originService.close();
        }

        try {
            this.transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_WRITE);

            // merge into this repository
            Repository.Op repository = this.repositoryDao.load();
            repository.merge(subsetOriginRepository);
            this.repositoryDao.store(repository);

            // after fork add used remote as default origin remote
            Remote remote = this.entityFactory.createRemote(ORIGIN_REMOTE_NAME, originRepositoryDir.toString(), Remote.Type.LOCAL);
            this.remoteDao.storeRemote(remote);

            this.transactionStrategy.end();
        } catch (Exception e) {
            this.transactionStrategy.rollback();

            throw new EccoException("Error during local fork.", e);
        }
    }

    public synchronized void forkAlreadyOpen(EccoService origService, String deselectedFeatureRevisionsString) {
        // create subset repository
        Repository.Op subsetOriginRepository;

        try {
            origService.transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_ONLY);
            Repository.Op originRepository = (Repository.Op) origService.getRepository();
            subsetOriginRepository = originRepository.subset(origService.parseFeatureRevisionsString(deselectedFeatureRevisionsString), originRepository.getMaxOrder(), this.entityFactory);
            origService.transactionStrategy.end();
        } catch (Exception e) {
            throw new EccoException("Error during local fork.", e);
        }

        try {
            Repository.Op repository = (Repository.Op) this.getRepository();
            repository.merge(subsetOriginRepository);

            this.transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_WRITE);
            this.repositoryDao.store(repository);
            this.transactionStrategy.end();
        } catch (Exception e) {
            throw new EccoException("Error during local fork.", e);
        }
    }


    public synchronized void pull(String remoteName) {
        this.pull(remoteName, "");
    }

    /**
     * Pulls the changes from the parent repository to this repository.
     *
     * @param remoteName                       The name of the remote.
     * @param deselectedFeatureRevisionsString A string enumerating the deselected feature revisions.
     */
    public synchronized void pull(String remoteName, String deselectedFeatureRevisionsString) {
        this.checkInitialized();

        try {
            this.transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_WRITE);

            // load remote
            Remote remote = this.remoteDao.loadRemote(remoteName);
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


                        int size = (Integer) ois.readObject();
                        progressInputStream.setMaxBytes(size);
                        progressInputStream.resetProgress();
                        progressInputStream.addListener(this);

                        // retrieve remote repository
                        Repository.Op subsetRepository = (Repository.Op) ois.readObject();

                        progressInputStream.removeListener(this);


                        // copy it using this entity factory
                        Repository.Op copiedRepository = subsetRepository.copy(this.entityFactory);

                        // merge into this repository
                        Repository.Op repository = this.repositoryDao.load();
                        repository.merge(copiedRepository);
                        this.repositoryDao.store(repository);
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
//					Repository.Op parentRepository = (Repository.Op) parentService.getRepository();
                    subsetParentRepository = parentRepository.subset(parentService.parseFeatureRevisionsString(deselectedFeatureRevisionsString), parentRepository.getMaxOrder(), this.entityFactory);

                    parentService.transactionStrategy.end();
                } catch (Exception e) {
                    parentService.transactionStrategy.rollback();

                    throw new EccoException("Error during local pull.", e);
                }

                // close parent repository
                parentService.close();

                // merge into this repository
                Repository.Op repository = this.repositoryDao.load();
                repository.merge(subsetParentRepository);
                this.repositoryDao.store(repository);
            }

            this.transactionStrategy.end();
        } catch (Exception e) {
            this.transactionStrategy.rollback();

            throw new EccoException("Error during pull.", e);
        }
    }


    public synchronized void push(String remoteName) {
        this.push("", remoteName);
    }

    /**
     * Pushes the changes from this repository to its parent repository.
     *
     * @param remoteName                       The name of the remote.
     * @param deselectedFeatureRevisionsString A string enumerating the deselected feature revisions.
     */
    public synchronized void push(String remoteName, String deselectedFeatureRevisionsString) {
        this.checkInitialized();

        try {
            this.transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_ONLY);

            // load remote
            Remote remote = this.remoteDao.loadRemote(remoteName);
            if (remote == null) {
                throw new EccoException("Remote " + remoteName + " does not exist");
            } else if (remote.getType() == Remote.Type.REMOTE) {

                try (SocketChannel sChannel = SocketChannel.open()) {
                    sChannel.configureBlocking(true);
                    String[] pair = remote.getAddress().split(":");
                    if (sChannel.connect(new InetSocketAddress(pair[0], Integer.parseInt(pair[1])))) {
                        ObjectOutputStream oos = new ObjectOutputStream(sChannel.socket().getOutputStream());
                        //ObjectInputStream ois = new ObjectInputStream(sChannel.socket().getInputStream());

                        oos.writeObject("PUSH");

                        // compute subset repository using mem entity factory
                        this.transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_ONLY);
                        Repository.Op repository = this.repositoryDao.load();
                        Repository.Op subsetRepository = repository.subset(this.parseFeatureRevisionsString(deselectedFeatureRevisionsString), repository.getMaxOrder(), this.memEntityFactory);
                        this.transactionStrategy.end();


                        // send subset repository
                        // without size:
                        //oos.writeObject(subsetRepository);
                        // with size:
                        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
                        ObjectOutputStream temp_oos = new ObjectOutputStream(byteOutputStream);
                        temp_oos.writeObject(subsetRepository);
                        // get size of data
                        int size = byteOutputStream.size();
                        byteOutputStream.close();
                        // send data
                        ProgressOutputStream pos = new ProgressOutputStream(sChannel.socket().getOutputStream());
                        pos.setMaxBytes(size);
                        pos.resetProgress();
                        pos.addListener(this);
                        byteOutputStream.writeTo(pos);
                        pos.removeListener(this);

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
                Repository.Op repository = this.repositoryDao.load();
                Repository.Op subsetRepository = repository.subset(this.parseFeatureRevisionsString(deselectedFeatureRevisionsString), repository.getMaxOrder(), parentService.entityFactory);

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

            this.transactionStrategy.end();
        } catch (Exception e) {
            this.transactionStrategy.rollback();

            throw new EccoException("Error during push.", e);
        }
    }


    // INIT ////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Creates and initializes a repository at the current location if no repository already exists at the current location or any of its parents.
     * The newly created repository is opened right away.
     *
     * @return True if the repository was created, false otherwise.
     */
    public synchronized boolean init() {
        LOGGER.info("INIT()");

        if (this.isInitialized())
            throw new EccoException("Service must not be initialized for init operation.");

        if (this.repositoryDirectoryExists())
            throw new EccoException("Repository already exists at this location.");

        try {
            if (!this.repositoryDirectoryExists())
                Files.createDirectory(this.repositoryDir);

            this.open();

            // TODO: do some initialization in backend like generating root object, etc.?

            try {
                this.transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_WRITE);

                // set max order for new repository
                Repository.Op repository = this.repositoryDao.load();
                repository.setMaxOrder(2);
                this.repositoryDao.store(repository);

                this.transactionStrategy.end();
            } catch (Exception e) {
                this.transactionStrategy.rollback();

                throw new EccoException("Error setting max order.", e);
            }

        } catch (IOException e) {
            throw new EccoException("Error while creating repository.", e);
        }

        return true;
    }


    // COMMIT //////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Commits the files in the base directory using the configuration string given in file {@link #CONFIG_FILE_NAME} or an empty configuration string if the file does not exist.
     *
     * @return The resulting commit object.
     */
    public synchronized Commit commit() {
        return this.commit("", getConfigStringFromFile(this.baseDir));
    }

    /**
     * Commits the files in the base directory with the commit message using the configuration string given in file {@link #CONFIG_FILE_NAME} or an empty configuration string if the file does not exist.
     *
     * @param commitMessage the commit message as string.
     * @return The resulting commit object.
     */
    public synchronized Commit commit(String commitMessage) {
        return this.commit(commitMessage, getConfigStringFromFile(this.baseDir));
    }


    /**
     * Gets the configuration by the given path.
     *
     * @param path the path to the config file.
     * @return the configuration string.
     */
    public String getConfigStringFromFile(Path path) {
        Path configFile = path.resolve(CONFIG_FILE_NAME);
        try {
            String configurationString = "";
            if (Files.exists(configFile))
                configurationString = new String(Files.readAllBytes(configFile)).trim();
            return configurationString;
        } catch (IOException e) {
            throw new EccoException("Error during commit: '.config' file existed but could not be read.", e);
        }
    }


    /**
     * Commits the files in the base directory using the given configuration string.
     *
     * @param commitMessage       the commit message as string.
     * @param configurationString The configuration string.
     * @return The resulting commit object.
     */
    public synchronized Commit commit(String commitMessage, String configurationString, String committer) {
        return this.commit(commitMessage, this.parseConfigurationString(configurationString), committer);
    }

    public synchronized Commit commit(String commitMessage, String configurationString) {
        String username = System.getProperty(KEY_USER_NAME, DEFAULT_USER_NAME);
        return this.commit(commitMessage, this.parseConfigurationString(configurationString), username);
    }

    /**
     * Commits the files in the base directory as the given configuration and returns the resulting commit object, or null in case of an error.
     *
     * @param commitMessage the commit message as string.
     * @param configuration The configuration to be commited.
     * @return The resulting commit object or null in case of an error.
     */
    public synchronized Commit commit(String commitMessage, Configuration configuration, String committer) {
        this.checkInitialized();

        checkNotNull(configuration);

        try {
            this.transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_WRITE);

            Set<Node.Op> nodes = readFiles();
            Repository.Op repository = this.repositoryDao.load();

            ArrayList<Variant> variants = repository.getVariants();

            long extractTime = System.currentTimeMillis();
            Commit commit = repository.extract(configuration, nodes, committer);
            extractTime = System.currentTimeMillis() - extractTime;

            //storing new variant
            boolean hasConfiguration = false;
            for (Variant v : variants) {
                if (v.getConfiguration().equals(configuration)) {
                    hasConfiguration = true;
                }
            }
            if (!hasConfiguration) {
                MemVariant memVariant = new MemVariant("Commit", configuration, UUID.randomUUID().toString());
                memVariant.setDescription(commitMessage);
                repository.addVariant(memVariant);
            }

            commit.setCommitMessage(commitMessage);

            this.repositoryDao.store(repository);

            long endStrategyTime = System.currentTimeMillis();
            this.transactionStrategy.end();
            endStrategyTime = System.currentTimeMillis() - endStrategyTime;

            LOGGER.info(Repository.class.getName() + ".extract(): " + extractTime +
                    "ms, .transactionStrategy.end(): " + endStrategyTime + "ms");

            return commit;
        } catch (Exception e) {
            this.transactionStrategy.rollback();

            throw new EccoException("Error during commit.", e);
        }
    }

    /**
     * Add a new variant configuration
     *
     * @param configurationString The configuration of the variant as String
     */
    public synchronized void addVariant(String configurationString, String name, String description) {
        Configuration configuration = parseConfigurationString(configurationString);
        addVariant(configuration, name, description);
    }

    /**
     * Add a new variant configuration
     *
     * @param configuration The configuration of the variant
     */
    public synchronized void addVariant(Configuration configuration, String name) {
        addVariant(configuration, name, null);
    }

    /**
     * Add a new variant configuration
     *
     * @param configuration The configuration of the variant
     */
    public synchronized void addVariant(Configuration configuration, String name, String description) {
        this.checkInitialized();

        checkNotNull(configuration);

        try {
            this.transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_WRITE);

            Repository.Op repository = this.repositoryDao.load();
            ArrayList<Variant> variants = repository.getVariants();

            //storing new variant
            boolean hasConfigurarion = false;
            for (Variant v : variants) {
                if (v.getConfiguration().equals(configuration)) {
                    hasConfigurarion = true;
                }
            }
            if (!hasConfigurarion) {
                MemVariant memVariant = new MemVariant(name, configuration, UUID.randomUUID().toString());
                memVariant.setDescription(description);
                repository.addVariant(memVariant);
            }
            //

            repositoryDao.store(repository);

            transactionStrategy.end();
        } catch (Exception e) {
            transactionStrategy.rollback();

            throw new EccoException("Error during adding a variant.", e);
        }
    }

    /**
     * Remove a new variant configuration
     *
     * @param id The configuration of the variant
     */
    public synchronized void removeVariant(String id) {
        checkInitialized();
        checkNotNull(id);
        safeTransaction(repository -> removeVariantById(repository, id));
    }

    /**
     * Remove a new variant configuration
     *
     * @param configuration The configuration of the variant
     */
    public synchronized void removeVariant(Configuration configuration) {
        checkInitialized();
        checkNotNull(configuration);
        safeTransaction(repository -> removeVariantByConfiguration(repository, configuration));
    }

    private Repository.Op removeVariantById(Repository.Op repository, String id) {
        Variant variant = repository.getVariant(id);

        if (variant != null) {
            repository.removeVariant(variant);
        }

        return repository;
    }

    private Repository.Op removeVariantByConfiguration(Repository.Op repository, Configuration configuration) {
        Variant variant = repository.getVariant(configuration);

        if (variant != null) {
            repository.removeVariant(variant);
        }

        return repository;
    }

    private void safeTransaction(Function<Repository.Op, Repository.Op> transaction) {
        try {
            transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_WRITE);
            Repository.Op repository = repositoryDao.load();

            repository = transaction.apply(repository);

            repositoryDao.store(repository);
            transactionStrategy.end();
        } catch (Exception e) {
            transactionStrategy.rollback();

            throw new EccoException("Error while adding a variant.", e);
        }
    }

    public void store() {
        this.checkInitialized();

        try {
            this.transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_WRITE);

            Repository.Op repository = this.repositoryDao.load();
            this.repositoryDao.store(repository);
            this.transactionStrategy.end();

        } catch (Exception e) {
            this.transactionStrategy.rollback();

            throw new EccoException("Store error", e);
        }
    }


    public void updateFeatureRevision(FeatureRevision featureRevision, String featureRevisionUpdate, String id, EccoService service) {
        service.checkInitialized();

        try {
            service.transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_WRITE);

            Repository.Op repository = service.repositoryDao.load();

            Variant variant = service.getRepository().getVariant(id);
            FeatureRevision[] featureRevisions = variant.getConfiguration().getFeatureRevisions();
            FeatureRevision[] newFeatureRevisions = new FeatureRevision[featureRevisions.length];
            int count = 0;
            Collection<? extends Feature> features = this.getRepository().getFeatures();
            FeatureRevision featureRevisionToUpdate = null;
            for (Feature feature : features) {
                if (feature.getName().equals(featureRevisionUpdate.substring(0, featureRevisionUpdate.indexOf(".")))) {
                    for (FeatureRevision revision : feature.getRevisions()) {
                        if (revision.getFeatureRevisionString().equals(featureRevisionUpdate)) {
                            featureRevisionToUpdate = revision;
                        }
                    }
                }
            }
            StringBuilder sb = new StringBuilder();
            for (FeatureRevision fr : featureRevisions) {
                if (fr.equals(featureRevision) && featureRevisionToUpdate != null) {
                    newFeatureRevisions[count] = featureRevisionToUpdate;
                    sb.append(",").append(featureRevisionUpdate);
                } else {
                    newFeatureRevisions[count] = fr;
                    sb.append(",").append(fr);
                }
                count++;
            }
            String config = sb.length() > 0 ? sb.substring(1) : ""; // remove first ','
            variant.getConfiguration().setFeatureRevisions(newFeatureRevisions);
            Configuration newConfiguration = service.parseConfigurationString(config);
            variant.setConfiguration(newConfiguration);

            service.repositoryDao.store(repository);
            service.transactionStrategy.end();
        } catch (Exception e) {
            service.transactionStrategy.rollback();
            throw new EccoException("Error during adding a variant.", e);
        }

    }


    public void removeFeatureRevision(FeatureRevision featureRevision, String id, EccoService service) {
        service.checkInitialized();

        try {
            service.transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_WRITE);

            Repository.Op repository = service.repositoryDao.load();

            Variant variant = service.getRepository().getVariant(id);
            FeatureRevision[] featureRevisions = variant.getConfiguration().getFeatureRevisions();
            FeatureRevision[] newFeatureRevisions = new FeatureRevision[featureRevisions.length];
            int count = 0;
            StringBuilder sb = new StringBuilder();
            for (FeatureRevision fr : featureRevisions) {
                if (!fr.equals(featureRevision)) {
                    newFeatureRevisions[count] = fr;
                    sb.append(",").append(fr);
                }
                count++;
            }
            String config = sb.length() > 0 ? sb.substring(1) : ""; // remove first ','
            variant.getConfiguration().setFeatureRevisions(newFeatureRevisions);
            Configuration newConfiguration = service.parseConfigurationString(config);
            variant.setConfiguration(newConfiguration);

            service.repositoryDao.store(repository);

            service.transactionStrategy.end();


        } catch (Exception e) {
            service.transactionStrategy.rollback();

            throw new EccoException("Error during adding a variant.", e);
        }

    }

    /**
     * Remove a new variant configuration
     *
     * @param configuration The configuration of the variant
     * @param service       The service to update a variant in a repository
     */
    public synchronized void updateVariant(Configuration configuration, String name, String id, EccoService service) {
        service.checkInitialized();

        checkNotNull(configuration);

        try {
            service.transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_WRITE);

            Repository.Op repository = service.repositoryDao.load();
            Variant variant = repository.getVariant(id);
            if (variant != null) {
                repository.updateVariant(variant, configuration, name);
            }
            //

            service.repositoryDao.store(repository);

            service.transactionStrategy.end();


        } catch (Exception e) {
            service.transactionStrategy.rollback();

            throw new EccoException("Error during adding a variant.", e);
        }
    }


    public synchronized Set<Node.Op> readFiles() {
        return this.reader.read(this.baseDir, new Path[]{Paths.get("")});
    }


    // CHECKOUT ////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Composes checkout with given configuration.
     *
     * @param configuration Configuration to be composed.
     * @return Checkout with composed artifacts.
     */
    private synchronized Checkout compose(Configuration configuration) {
        this.checkInitialized();

        checkNotNull(configuration);

        Repository.Op repository = this.repositoryDao.load();
        return repository.compose(configuration);
    }

    /**
     * Checks out the implementation of the configuration (given as configuration string) into the base directory.
     *
     * @param configurationString The configuration string representing the configuration that shall be checked out.
     * @return The checkout object.
     */
    public synchronized Checkout checkout(String configurationString) {
        return this.checkout(this.parseConfigurationString(configurationString));
    }

    /**
     * Retrieves associations needed to compose a configuration.
     *
     * @param configurationString The configuration string for that associations shall be retrieved.
     * @return Set of associations.
     */
    public synchronized Set<Association> getAssociations(String configurationString) {
        return this.getAssociations(this.parseConfigurationString(configurationString));
    }

    /**
     * Retrieves associations needed to compose a configuration.
     *
     * @param configuration The configuration for that associations shall be retrieved.
     * @return Set of associations.
     */
    public synchronized Set<Association> getAssociations(Configuration configuration) {
        Checkout checkout = compose(configuration);
        return checkout.getSelectedAssociations();
    }

    public synchronized Set<Node> compareArtifacts(String configurationString) {
        return this.compareArtifacts(this.parseConfigurationString(configurationString));
    }

    public synchronized Set<Node> compareArtifacts(Configuration configuration) {
        Checkout checkout = compose(configuration);
        return compareArtifacts(checkout);
    }

    // TODO: check if 'compareArtifacts' is proper name for method (fires association-selected events and returns artifact nodes)
    private synchronized Set<Node> compareArtifacts(Checkout checkout) {
        for (Association selectedAssociation : checkout.getSelectedAssociations()) {
            this.fireAssociationSelectedEvent(selectedAssociation);
        }
        // nodes (artifacts) to write to files
        return new HashSet<>(checkout.getNode().getChildren());
    }

    /**
     * Checks out the implementation of the given configuration into the base directory.
     *
     * @param configuration The configuration to be checked out.
     * @return The checkout object.
     */
    public synchronized Checkout checkout(Configuration configuration) {
        Checkout checkout = compose(configuration);

        Set<Node> nodes = compareArtifacts(checkout);
        this.writer.write(this.baseDir, nodes);

        // write config file into base directory
        Path configFile = this.baseDir.resolve(CONFIG_FILE_NAME);
        if (Files.exists(configFile)) {
            throw new EccoException("Configuration file already exists in base directory.");
        } else {
            try {
                Files.write(configFile, configuration.toString().getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (IOException e) {
                throw new EccoException("Could not create configuration file.", e);
            }
            this.fireWriteEvent(configFile, this.writer);
        }

        // write warnings file into base directory
        Path warningsFile = this.baseDir.resolve(WARNINGS_FILE_NAME);
        if (Files.exists(warningsFile)) {
            throw new EccoException("Warnings file already exists in base directory.");
        } else {
            try {
                StringBuilder sb = new StringBuilder();
                for (ModuleRevision mr : checkout.getMissing()) {
                    sb.append("MISSING: ").append(mr).append(System.lineSeparator());
                }
                for (Map.Entry<ModuleRevision, String> mr : checkout.getSurplusModules().entrySet()) {
                    sb.append("SURPLUS: ").append(mr.getKey()).append(" trace id: ")
                            .append(mr.getValue()).append(System.lineSeparator());
                }
                for (Artifact a : checkout.getOrderWarnings()) {
                    List<String> pathList = new LinkedList<>();
                    Node current = a.getContainingNode().getParent();
                    while (current != null) {
                        if (current.getArtifact() != null)
                            pathList.add(0, current.getArtifact().toString() + " > ");
                        current = current.getParent();
                    }
                    pathList.add(a.toString());
                    sb.append("ORDER: ").append(String.join("", pathList)).append(System.lineSeparator());
                }
                for (Association association : checkout.getUnresolvedAssociations()) {
                    sb.append("UNRESOLVED: ").append(association).append(System.lineSeparator());
                }
                Files.write(warningsFile, sb.toString().getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (IOException e) {
                throw new EccoException("Could not create warnings file.", e);
            }
            this.fireWriteEvent(warningsFile, this.writer);
        }

        return checkout;
    }

    public synchronized Checkout checkout(Node node) {
        this.checkInitialized();

        Checkout checkout = new Checkout();
        checkout.setNode(node);

        Set<Node> nodes = new HashSet<>(node.getChildren());
        this.writer.write(this.baseDir, nodes);

        return checkout;
    }


    /**
     * Parses a given set of files or directories with the respective plugins and returns the resulting artifact tree.
     * Every artifact in the tree for which a matching artifact already exists in the repository contains a reference to that artifact as a property.
     * This can be used to map current files to the contents in the repository.
     * In case the respective readers attach additional information to the artifacts below file level in the form of properties (e.g. line and column numbers in the case of source code) this can be used to highlight lower level artifacts (e.g. statements in the source code) based on the features they belongs to.
     *
     * @param paths The collection of paths (files or directories, relative to the base directory) to map to the repository.
     * @return The root node of the mapped artifact tree.
     */
    public synchronized RootNode map(Collection<Path> paths) {
        checkNotNull(paths);
        checkArgument(!paths.isEmpty());

        Set<Node.Op> nodes = this.reader.readSpecificFiles(this.baseDir, paths.toArray(new Path[0]));

        RootNode.Op rootNode = this.entityFactory.createRootNode();
        for (Node.Op node : nodes) {
            rootNode.addChild(node);
        }

        try {
            this.transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_ONLY);

            Repository.Op repository = this.repositoryDao.load();

            repository.map(rootNode);

            this.transactionStrategy.end();

            return rootNode;
        } catch (Exception e) {
            this.transactionStrategy.rollback();

            throw new EccoException("Error during map.", e);
        }
    }


    // OTHERS //////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Returns the list of loaded artifact plugins.
     *
     * @return The list of artifact plugins.
     */
    public Collection<ArtifactPlugin> getArtifactPlugins() {
        return new ArrayList<>(this.artifactPlugins);
    }

    /**
     * Get the injector that can be used to retrieve arbitrary artifact readers, writers, viewers, etc.
     * This is lower level functionality that should not be used unless absolutely necessary.
     *
     * @return The injector object.
     */
    public Injector getInjector() {
        return this.injector;
    }


}
