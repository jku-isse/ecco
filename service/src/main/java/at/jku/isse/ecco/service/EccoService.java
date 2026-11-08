package at.jku.isse.ecco.service;

import at.jku.isse.ecco.logic.LogicCondition;
import at.jku.isse.ecco.core.Constraint;
import at.jku.isse.ecco.*;
import at.jku.isse.ecco.adapter.*;
import at.jku.isse.ecco.adapter.dispatch.*;
import at.jku.isse.ecco.service.dispatch.*;
import at.jku.isse.ecco.artifact.*;
import at.jku.isse.ecco.core.*;
import at.jku.isse.ecco.dao.*;
import at.jku.isse.ecco.feature.*;
import at.jku.isse.ecco.featuretrace.evaluation.EvaluationStrategy;
import at.jku.isse.ecco.featuretrace.evaluation.EvaluationStrategyFactory;
import at.jku.isse.ecco.maintree.building.AssociationMerger;
import at.jku.isse.ecco.maintree.building.BoostedAssociationMerger;
import at.jku.isse.ecco.maintree.building.MainTreeBuildingStrategy;
import at.jku.isse.ecco.maintree.factory.MainTreeBuildingStrategyFactory;
import at.jku.isse.ecco.mining.*;
import at.jku.isse.ecco.module.*;
import at.jku.isse.ecco.repository.*;
import at.jku.isse.ecco.service.listener.*;
import at.jku.isse.ecco.storage.*;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.tree.*;
import com.google.inject.Module;
import com.google.inject.*;
import com.google.inject.name.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;
import java.util.logging.*;
import java.util.stream.*;

import static com.google.common.base.Preconditions.*;

/**
 * A service class that gives access to high level operations like init, fork, pull, push, etc.
 */
public class EccoService implements ProgressInputStream.ProgressListener, ProgressOutputStream.ProgressListener, ReadListener, WriteListener, Closeable {
    static {
        // quiet java.util.logging's noisy INFO-level default before any Logger (here or in any
        // class reached through EccoService, e.g. adapters) is used -- see logging.properties.
        try (InputStream loggingConfig = EccoService.class.getClassLoader().getResourceAsStream("logging.properties")) {
            if (loggingConfig != null) {
                LogManager.getLogManager().readConfiguration(loggingConfig);
            }
        } catch (IOException e) {
            // fall back to JUL's own defaults
        }
    }

    private static final Logger LOGGER = Logger.getLogger(EccoService.class.getName());
    public static final String KEY_USER_NAME = "user.name";
    public static final String DEFAULT_USER_NAME = "anonymous";
    public enum Operation {
        OPEN, INIT, FORK, CLOSE, COMMIT, CHECKOUT, FETCH, PULL, PUSH, SERVER, OTHER
    }
    public static final String ORIGIN_REMOTE_NAME = "origin";
    public static final String ECCO_PROPERTIES_FILE = "ecco.properties";
    public static final String ECCO_PROPERTIES_STORAGE = "ecco.storage";
    public static final String ECCO_PROPERTIES_EVALUATION_STRATEGY = "default.evaluation.strategy";
    public static final String ECCO_PROPERTIES_MAIN_TREE_BUILDING_STRATEGY = "default.main.tree.building.strategy";
    public static final Path REPOSITORY_DIR_NAME = Paths.get(".ecco");
    public static final Path DEFAULT_BASE_DIR = Paths.get("");
    public static final Path DEFAULT_REPOSITORY_DIR = DEFAULT_BASE_DIR.resolve(REPOSITORY_DIR_NAME);
    public static final Path CONFIG_FILE_NAME = Paths.get(".config");
    public static final Path WARNINGS_FILE_NAME = Paths.get(".warnings");
    public static final Path HASHES_FILE_NAME = DispatchWriter.HASHES_FILE_NAME;
    /** The per-repository settings: what each plugin chose when the repository was created. */
    public static final Path SETTINGS_FILE_NAME = Paths.get(".settings");

    private final Properties properties = new Properties();
    Path baseDir;
    private Path repositoryDir;
    private Collection<ArtifactPlugin> artifactPlugins;
    private Injector injector;
    private boolean initialized = false;
    public boolean isInitialized() {
        return this.initialized;
    }
    private EvaluationStrategyFactory evaluationStrategyFactory = new EvaluationStrategyFactory();
    private MainTreeBuildingStrategyFactory mainTreeBuildingStrategyFactory = new MainTreeBuildingStrategyFactory();
    private EvaluationStrategy defaultEvaluationStrategy;
    private MainTreeBuildingStrategy defaultMainTreeBuildingStrategy;
    private int defaultMaxOrder;
    /** The open repository's DAOs, reader and writer; null before the first open, kept after close (see {@link RepositoryContext}). */
    RepositoryContext context;


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
            this.listeners.fireStatusChangedEvent();
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
            this.listeners.fireStatusChangedEvent();
        }
    }

    /**
     * The directory the repository lives in (repositoryDir's parent) -- the natural default base
     * directory for a checkout, independent of whatever {@link #getBaseDir()} currently happens to
     * be set to (e.g. left over from an unrelated Commit/Fork done from a different folder).
     *
     * <p>Deliberately not just {@code repositoryDir.getParent()}: for the common case where
     * repositoryDir is a bare relative path (e.g. {@code .ecco}, as set by
     * {@link #detectRepository()} when running from the repository's own home directory),
     * {@code Path.getParent()} returns {@code null} rather than "the current directory" -- resolve
     * to a real/absolute path first so the parent is always well-defined.
     */
    public Path getRepositoryHomeDir() {
        Path resolved;
        try {
            resolved = this.repositoryDir.toRealPath();
        } catch (IOException e) {
            resolved = this.repositoryDir.toAbsolutePath().normalize();
        }
        Path parent = resolved.getParent();
        return parent != null ? parent : resolved;
    }

    public EntityFactory getEntityFactory() {
        return this.context == null ? null : this.context.entityFactory;
    }

    public DispatchReader getReader() {
        return this.context == null ? null : this.context.reader;
    }

    /**
     * Replaces the open repository's reader. Before the first open there is none to replace: open()
     * injects its own, as it always overwrote a reader set before it.
     */
    public void setReader(DispatchReader reader) {
        if (this.context != null)
            this.context.reader = reader;
    }

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


    // # LISTENERS #####################################################################################################

    final ListenerRegistry listeners = new ListenerRegistry(this);


    // # SERVICES ######################################################################################################
    // what this facade delegates to: each is given what it reads of the facade (ServiceState) and
    // the other services it uses, made in that order - none of them refers to EccoService

    /** What the services read of this service, live - see {@link ServiceState}. */
    private final class State implements ServiceState {
        @Override
        public RepositoryContext context() {
            return EccoService.this.context();
        }

        @Override
        public RepositoryContext currentContext() {
            return EccoService.this.context;
        }

        @Override
        public void checkInitialized() {
            EccoService.this.checkInitialized();
        }

        @Override
        public boolean isInitialized() {
            return EccoService.this.isInitialized();
        }

        @Override
        public Path baseDir() {
            return EccoService.this.baseDir;
        }

        @Override
        public Path repositoryDir() {
            return EccoService.this.getRepositoryDir();
        }

        @Override
        public boolean repositoryDirectoryExists() {
            return EccoService.this.repositoryDirectoryExists();
        }

        @Override
        public Collection<ArtifactPlugin> artifactPlugins() {
            return EccoService.this.getArtifactPlugins();
        }

        @Override
        public Object lock() {
            return EccoService.this;
        }

        @Override
        public void init() {
            EccoService.this.init();
        }

        @Override
        public void abandonNewRepository(Throwable cause) {
            EccoService.this.abandonNewRepository(cause);
        }
    }

    private final ServiceState state = new State();

    final CheckoutOptions checkoutOptions = new CheckoutOptions();

    final RepositorySettings settings = new RepositorySettings(this.state);

    private final ConfigurationParser configurationParser = new ConfigurationParser(this.state);

    private final ConstraintService constraintService = new ConstraintService(this.state, this.checkoutOptions);

    private final CheckoutService checkoutService = new CheckoutService(this.state, this.listeners, this.checkoutOptions, this.constraintService);

    private final CommitService commitService = new CommitService(this.state, this.listeners, this.settings);

    private final OrderService orderService = new OrderService(this.state, this.configurationParser, this.checkoutService, this.commitService);

    private final VariantManager variantManager = new VariantManager(this.state, this.configurationParser);

    private final MinimizationService minimizationService = new MinimizationService(this.state, this.constraintService);

    /** Package-visible: a sync with a remote on disk replays into, and copies from, that remote's service. */
    final CommitReplay commitReplay = new CommitReplay(this.state);

    final RemoteSyncService remoteSyncService = new RemoteSyncService(this.state, this.listeners, this.settings, this.configurationParser, this.commitReplay);

    private final ForkService forkService = new ForkService(this.state, this.listeners, this.settings, this.commitReplay, this.remoteSyncService);

    public boolean isWriteInProgress() {
        return this.listeners.isWriteInProgress();
    }

    public void addListener(EccoListener listener) {
        this.listeners.addListener(listener);
    }

    public void removeListener(EccoListener listener) {
        this.listeners.removeListener(listener);
    }


    // relay reader, writer, and progress events

    @Override
    public void readProgressEvent(double progress, long bytes) {
        this.listeners.readProgressEvent(progress, bytes);
    }

    @Override
    public void writeProgressEvent(double progress, long bytes) {
        this.listeners.writeProgressEvent(progress, bytes);
    }

    @Override
    public void fileReadEvent(Path file, ArtifactReader reader) {
        this.listeners.fireReadEvent(file, reader);
        // a cancelled commit stops between files instead of reading the rest first
        this.commitService.checkCancelled();
    }

    @Override
    public void fileWriteEvent(Path file, ArtifactWriter writer) {
        this.listeners.fireWriteEvent(file, writer);
    }


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
            throw new ServiceNotInitializedException();
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

        String defaultMaxOrderString = this.properties.getProperty("default.max.order");
        this.defaultMaxOrder = Integer.parseInt(defaultMaxOrderString);

        // artifact adapter modules
        List<Module> artifactModules = new ArrayList<>();
        List<Module> allArtifactModules = new ArrayList<>();
        this.artifactPlugins = new ArrayList<>();
        for (ArtifactPlugin artifactPlugin : ArtifactPlugin.getArtifactPlugins()) {
            allArtifactModules.add(artifactPlugin.getModule());
            if (AdapterPreferences.isEnabled(artifactPlugin)) {
                artifactModules.add(artifactPlugin.getModule());
                this.artifactPlugins.add(artifactPlugin);
            }
        }
        LOGGER.config("ARTIFACT PLUGINS: " + artifactModules);
        LOGGER.config("ALL ARTIFACT PLUGINS: " + allArtifactModules);
        if (artifactModules.isEmpty())
            throw new EccoException("At least one artifact plugin must be configured.");

        // storage modules
        String storagePluginId = this.properties.getProperty(ECCO_PROPERTIES_STORAGE);
        List<Module> storageModules = new ArrayList<>();
        List<Module> allStorageModules = new ArrayList<>();
        for (StoragePlugin dataPlugin : StoragePlugin.getDataPlugins()) {
            if (dataPlugin.getPluginId().equals(storagePluginId)) {
                storageModules.add(dataPlugin.getModule());
            }
            allStorageModules.add(dataPlugin.getModule());
        }
        LOGGER.config("STORAGE PLUGINS: " + storageModules);
        LOGGER.config("ALL STORAGE PLUGINS: " + allStorageModules);
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
        // opening any other folder (an empty one, or a working directory picked instead of its .ecco)
        // used to succeed as an empty repository, writing .ignores/.adapters into it - and forking or
        // fetching from one silently produced nothing. openRepository(true) checks for repository
        // data before anything is written.
        if (!Files.isDirectory(this.repositoryDir)) {
            throw new EccoException("Not an ECCO repository (not a directory): " + this.repositoryDir);
        }

        this.openRepository(true);
    }

    /**
     * The repository directory for a path given by a user, e.g. the origin of a fork or a local
     * remote: a repository's working directory stands for the .ecco directory in it.
     */
    public static Path resolveRepositoryDir(Path path) {
        Path nested = path.resolve(REPOSITORY_DIR_NAME);
        return Files.isDirectory(nested) ? nested : path;
    }

    /**
     * Opens the repository directory - an existing repository if {@code mustExist}, otherwise the
     * one init() just created, which has no repository data yet.
     */
    private void openRepository(boolean mustExist) {

        LOGGER.config("BASE_DIR: " + this.baseDir);
        LOGGER.config("REPOSITORY_DIR: " + this.repositoryDir);

        Collection<Module> modules = this.initializeService();

        // TODO: put this in a class
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
            RepositoryContext context = new RepositoryContext(this, this.listeners);
            injector.injectMembers(context);
            this.context = context;
            injector.injectMembers(this.evaluationStrategyFactory);
            injector.injectMembers(this.mainTreeBuildingStrategyFactory);
        } catch (CreationException creationException) {
            throw new EccoException("Error during dependency injection. The storage plugin may be faulty.", creationException);
        }
        if (mustExist && !this.context.transactionStrategy.containsRepository()) {
            Path nested = this.repositoryDir.resolve(REPOSITORY_DIR_NAME);
            throw new EccoException("Not an ECCO repository: " + this.repositoryDir
                    + (Files.isDirectory(nested) ? " (did you mean " + nested + "?)" : ""));
        }

        String evaluationStrategyName = this.properties.getProperty(ECCO_PROPERTIES_EVALUATION_STRATEGY);
        String mainTreeBuildingStrategyName = this.properties.getProperty(ECCO_PROPERTIES_MAIN_TREE_BUILDING_STRATEGY);
        if (evaluationStrategyName == null){
            throw new EccoException("Default evaluation strategy is not configured in properties file.");
        }
        if (mainTreeBuildingStrategyName == null){
            throw new EccoException("Default main tree building strategy is not configured in properties file.");
        }
        this.defaultEvaluationStrategy = this.evaluationStrategyFactory.getEvaluationStrategy(evaluationStrategyName);
        this.defaultMainTreeBuildingStrategy = this.mainTreeBuildingStrategyFactory.getMainTreeBuildingStrategy(mainTreeBuildingStrategyName);

        this.injector = injector;
        this.context.transactionStrategy.open();
        this.context.repositoryDao.init();
        this.context.remoteDao.init();
        this.context.reader.init();

        // add default ignore patterns
        this.context.reader.addIgnorePattern(REPOSITORY_DIR_NAME.toString());
        this.context.reader.addIgnorePattern(CONFIG_FILE_NAME.toString());
        this.context.reader.addIgnorePattern(WARNINGS_FILE_NAME.toString());
        this.context.reader.addIgnorePattern(HASHES_FILE_NAME.toString());

        this.context.reader.addListener(this);
        this.context.writer.addListener(this);
        this.initialized = true;
        this.listeners.fireStatusChangedEvent();
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

        // its sync server goes with it: it went on serving - after another repository was opened
        // in the same window, that one, without asking whether to accept connections
        if (this.serverRunning())
            this.stopServer();
        this.initialized = false;
        this.context.reader.removeListener(this);
        this.context.writer.removeListener(this);
        this.context.repositoryDao.close();
        this.context.remoteDao.close();
        this.context.transactionStrategy.close();
        this.listeners.fireStatusChangedEvent();
        LOGGER.info("Repository closed.");
    }


    // # UTILS #########################################################################################################

    public synchronized Remote addRemote(String name, String address) {
        return this.remoteSyncService.addRemote(name, address);
    }

    public synchronized Remote addRemote(String name, String address, Remote.Type type) {
        return this.remoteSyncService.addRemote(name, address, type);
    }

    /**
     * See {@link RemoteSyncService#updateRemote}.
     */
    public synchronized Remote updateRemote(String name, String newName, String address, Remote.Type type) {
        return this.remoteSyncService.updateRemote(name, newName, address, type);
    }

    public synchronized void removeRemote(String name) {
        this.remoteSyncService.removeRemote(name);
    }

    public synchronized Remote getRemote(String name) {
        return this.remoteSyncService.getRemote(name);
    }

    public synchronized Collection<Remote> getRemotes() {
        return this.remoteSyncService.getRemotes();
    }


    public synchronized Repository getRepository() {
        this.checkInitialized();

        return this.context.repository();
    }

    /**
     * Get all commit objects.
     *
     * @return Collection containing all commit objects.
     */
    public synchronized Collection<Commit> getCommits() {
        this.checkInitialized();

        // no rollback here: this never begins a transaction itself, and getRepository() already rolls
        // its own back - a second rollback() throws "No transaction active", replacing the real error
        try {
            return getRepository().getCommits();
        } catch (EccoException e) {
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
    public synchronized Configuration parseConfigurationString(String configurationString) {
        return this.configurationParser.parseConfigurationString(configurationString);
    }

    public synchronized Collection<FeatureRevision> parseFeatureRevisionsString(String featureRevisionsString) {
        return this.configurationParser.parseFeatureRevisionsString(featureRevisionsString);
    }


    // # CORE SERVICES #################################################################################################


    // DISTRIBUTED OPERATIONS //////////////////////////////////////////////////////////////////////////////////////////

    public boolean serverRunning() {
        return this.remoteSyncService.serverRunning();
    }

    /**
     * See {@link RemoteSyncService#serverPort()}.
     */
    public int serverPort() {
        return this.remoteSyncService.serverPort();
    }

    /**
     * Starts the sync server on the loopback interface and blocks until {@link #stopServer()}.
     * Deliberately not synchronized: it runs the server's accept loop for as long as the server is
     * up, and holding this service's monitor that whole time blocked every other synchronized call
     * (commit, checkout, getRepository, ...). RemoteSyncService guards against concurrent starts
     * itself, and request handling uses the transaction strategy like any other operation.
     */
    public void startServer(int port) {
        this.remoteSyncService.startServer(port);
    }

    /**
     * See {@link RemoteSyncService#startServer(int, boolean)}.
     */
    public void startServer(int port, boolean allInterfaces) {
        this.remoteSyncService.startServer(port, allInterfaces);
    }

    public void stopServer() {
        this.remoteSyncService.stopServer();
    }

    public synchronized void fetch(String remoteName) {
        this.remoteSyncService.fetch(remoteName);
    }

    /** Fetches, cancellable at checkpoints before anything is written: see {@link RemoteSyncService#fetch(String, java.util.function.BooleanSupplier)}. */
    public synchronized void fetch(String remoteName, java.util.function.BooleanSupplier cancelled) {
        this.remoteSyncService.fetch(remoteName, cancelled);
    }

    public synchronized void fork(String hostname, int port) {
        this.fork(hostname, port, "");
    }

    public synchronized void fork(String hostname, int port, String deselectedFeatureRevisionsString) {
        this.forkService.fork(hostname, port, deselectedFeatureRevisionsString);
    }

    /**
     * Commits [commits] of [source] here, in their order, as committing their content would - each
     * keeping its id, date and message (Repository.Op#replay) - in one transaction: see {@link CommitReplay#replay}.
     */
    synchronized void replay(Repository.Op source, List<? extends Commit> commits) {
        this.commitReplay.replay(source, commits);
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
        this.forkService.fork(originRepositoryDir, deselectedFeatureRevisionsString);
    }

    public synchronized void forkAlreadyOpen(EccoService origService, String deselectedFeatureRevisionsString) {
        this.forkService.forkAlreadyOpen(origService, deselectedFeatureRevisionsString);
    }


    public synchronized void pull(String remoteName) {
        this.remoteSyncService.pull(remoteName);
    }

    /**
     * Pulls the changes from the parent repository to this repository.
     *
     * @param remoteName                       The name of the remote.
     * @param deselectedFeatureRevisionsString A string enumerating the deselected feature revisions.
     */
    public synchronized void pull(String remoteName, String deselectedFeatureRevisionsString) {
        this.remoteSyncService.pull(remoteName, deselectedFeatureRevisionsString);
    }

    /** Pulls, cancellable at checkpoints before anything is written: see {@link RemoteSyncService#pull(String, String, java.util.function.BooleanSupplier)}. */
    public synchronized void pull(String remoteName, String deselectedFeatureRevisionsString, java.util.function.BooleanSupplier cancelled) {
        this.remoteSyncService.pull(remoteName, deselectedFeatureRevisionsString, cancelled);
    }

    public synchronized void push(String remoteName) {
        this.remoteSyncService.push(remoteName);
    }

    /**
     * Pushes the changes from this repository to its parent repository.
     *
     * @param remoteName                       The name of the remote.
     * @param deselectedFeatureRevisionsString A string enumerating the deselected feature revisions.
     */
    public synchronized void push(String remoteName, String deselectedFeatureRevisionsString) {
        this.remoteSyncService.push(remoteName, deselectedFeatureRevisionsString);
    }

    /** Pushes, cancellable at checkpoints before anything is written: see {@link RemoteSyncService#push(String, String, java.util.function.BooleanSupplier)}. */
    public synchronized void push(String remoteName, String deselectedFeatureRevisionsString, java.util.function.BooleanSupplier cancelled) {
        this.remoteSyncService.push(remoteName, deselectedFeatureRevisionsString, cancelled);
    }


    // INIT ////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Creates a repository at the repository directory and opens it right away.
     *
     * @return Always true.
     * @throws EccoException if the service is initialized, a repository already exists at this location, or creating it failed.
     */
    public synchronized boolean init() {
        LOGGER.info("INIT()");

        if (this.isInitialized())
            throw new EccoException("Service must not be initialized for init operation.");
        if (this.repositoryDirectoryExists())
            throw new EccoException("Repository already exists at this location.");

        try {
            Files.createDirectory(this.repositoryDir);
        } catch (IOException e) {
            throw new EccoException("Error while creating repository.", e);
        }
        try {
            this.openRepository(false);
            this.settings.writeForNewRepository();
            this.context.transactionStrategy.begin(TransactionStrategy.TRANSACTION.READ_WRITE);
            Repository.Op repository = this.context.repositoryDao.load();
            repository.setMaxOrder(this.defaultMaxOrder);
            repository.setEvaluationStrategy(this.defaultEvaluationStrategy);
            repository.setMaintreeBuildingStrategy(this.defaultMainTreeBuildingStrategy);
            this.context.repositoryDao.store(repository);
            this.context.transactionStrategy.end();
        } catch (Error e) {
            this.abandonNewRepository(e);
            throw e;
        } catch (Exception e) {
            this.abandonNewRepository(e);
            throw new EccoException("Error setting default values.", e);
        }
        return true;
    }

    /**
     * The open repository's context; throws {@link ServiceNotInitializedException} if no repository
     * is open - the check every operation of the services starts with.
     */
    RepositoryContext context() {
        this.checkInitialized();
        return this.context;
    }

    /**
     * {@link RepositoryContext#rollbackIfActive()}, also safe before open() ever created a context
     * and after close() - for undoing an init or fork that failed part way.
     */
    void rollbackIfTransactionActive() {
        if (this.context != null)
            this.context.rollbackIfActive();
    }

    /**
     * Undoes a repository creation (init/fork) that failed part way: releases the transaction and
     * write lock, closes the service and deletes the repository directory again, so that the
     * operation can simply be retried. Only called by operations that require that no repository
     * existed at this location beforehand - i.e. the directory is entirely theirs. Cleanup failures
     * are attached to {@code cause} instead of hiding it.
     */
    void abandonNewRepository(Throwable cause) {
        this.rollbackIfTransactionActive();
        try {
            this.close();
        } catch (RuntimeException e) {
            cause.addSuppressed(e);
        }
        try (java.util.stream.Stream<Path> files = Files.walk(this.repositoryDir)) {
            for (Path file : (Iterable<Path>) files.sorted(java.util.Comparator.reverseOrder())::iterator)
                Files.deleteIfExists(file);
        } catch (IOException | RuntimeException e) {
            cause.addSuppressed(e);
        }
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
        return this.commitService.getConfigStringFromFile(path);
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
     * Commits the files in the base directory as the given configuration and returns the resulting commit object.
     *
     * @param commitMessage the commit message as string.
     * @param configuration The configuration to be commited.
     * @return The resulting commit object.
     */
    public synchronized Commit commit(String commitMessage, Configuration configuration, String committer) {
        return this.commitService.commit(commitMessage, configuration, committer);
    }

    /**
     * Commits the files in [directory] as [configurationString] - or, if it is blank, as the
     * directory's .config says - leaving the base directory as it is: the Git import set it to a
     * temporary directory for each commit and never set it back.
     */
    public synchronized Commit commitDirectory(Path directory, String commitMessage, String configurationString) {
        String configuration = configurationString == null || configurationString.isBlank() ? this.getConfigStringFromFile(directory) : configurationString;
        String username = System.getProperty(KEY_USER_NAME, DEFAULT_USER_NAME);
        return this.commitService.commit(commitMessage, this.parseConfigurationString(configuration), username, directory);
    }

    /**
     * Asks the commit running on another thread to stop. It stops before anything is stored - at the
     * next file read, or before extracting or storing - is rolled back, and throws
     * {@link CommitCancelledException}; a commit that is already storing completes. Not synchronized,
     * since the commit holds this service's lock. Without a commit in progress this does nothing.
     */
    public void cancelCommit() {
        this.commitService.cancel();
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
        this.variantManager.addVariant(configuration, name, description);
    }

    /**
     * Removes a variant.
     *
     * @param id The id of the variant
     */
    public synchronized void removeVariant(String id) {
        this.variantManager.removeVariant(id);
    }

    /**
     * Removes the variant of a configuration.
     *
     * @param configuration The configuration of the variant
     */
    public synchronized void removeVariant(Configuration configuration) {
        this.variantManager.removeVariant(configuration);
    }

    public synchronized void store() {
        this.checkInitialized();

        this.context.inTransaction(TransactionStrategy.TRANSACTION.READ_WRITE, "Store error", () -> {
            this.context.repositoryDao.store(this.context.repositoryDao.load());
            return null;
        });
    }


    public synchronized void updateFeatureRevision(FeatureRevision featureRevision, String featureRevisionUpdate, String id) {
        this.variantManager.updateFeatureRevision(featureRevision, featureRevisionUpdate, id);
    }

    public synchronized void removeFeatureRevision(FeatureRevision featureRevision, String id) {
        this.variantManager.removeFeatureRevision(featureRevision, id);
    }

    /**
     * Updates a variant: its configuration and name.
     *
     * @param configuration The new configuration of the variant
     * @param name          The new name of the variant
     * @param id            The id of the variant
     */
    public synchronized void updateVariant(Configuration configuration, String name, String id) {
        this.variantManager.updateVariant(configuration, name, id);
    }


    public synchronized Set<Node.Op> readFiles() {
        return this.commitService.readFiles(this.baseDir);
    }



    // CHECKOUT ////////////////////////////////////////////////////////////////////////////////////////////////////////


    /**
     * Sets and persists a feature's description.
     *
     * @param featureId   The id of the feature.
     * @param description The new description.
     */
    public synchronized void setFeatureDescription(String featureId, String description) {
        this.checkInitialized();
        checkNotNull(featureId);
        this.context.write("Error updating the feature description.", repository -> {
            Feature feature = repository.getFeature(featureId);
            if (feature == null)
                throw new EccoException("Feature does not exist: " + featureId);
            feature.setDescription(description);
            return repository;
        });
    }

    /**
     * Switches the repository to the feature-trace-boosting main tree building strategy, persistently
     * (this used to set it on the loaded repository without a write transaction, so it only stuck if
     * some later write happened to persist it - see FeatureTraceBoostingPersistenceTest).
     */
    public synchronized void enableFeatureTraceBoosting(){
        this.checkInitialized();
        BoostedAssociationMerger merger = this.context.entityFactory.createBoostedAssociationMerger();
        this.context.write("Error while enabling feature trace boosting.", repository -> {
            repository.setMaintreeBuildingStrategy(merger);
            return repository;
        });
    }

    /**
     * Switches the repository back to the plain main tree building strategy, persistently - see
     * {@link #enableFeatureTraceBoosting()}.
     */
    public synchronized void disableFeatureTraceBoosting(){
        this.checkInitialized();
        AssociationMerger merger = this.context.entityFactory.createAssociationMerger();
        this.context.write("Error while disabling feature trace boosting.", repository -> {
            repository.setMaintreeBuildingStrategy(merger);
            return repository;
        });
    }

    /**
     * Controls whether {@link #compose} suppresses {@link Checkout#getSurplusModules()} entries
     * already provably implied by the accepted feature model (see {@link SurplusModuleSuppressor}).
     * On by default; an escape hatch in case the mining cost this requires on every {@link #compose}
     * call (when there's a non-empty surplus map to check) proves unacceptable in practice.
     *
     * @param enabled Whether surplus-module suppression should run.
     */
    public synchronized void setSurplusSuppressionEnabled(boolean enabled) {
        this.checkoutOptions.surplusSuppressionEnabled = enabled;
    }

    /**
     * Controls whether {@link #compose} removes {@link Checkout#getSurplusModules()} entries that are
     * pure syntactic noise -- a term absorbed ({@code X + XY = X}) by a simpler term already present
     * on the same owning association's condition (see {@link SurplusLatticeAbsorber}). Needs no
     * feature model and can never remove a genuinely-needed warning (the identity is unconditional),
     * so this is on by default; the escape hatch exists for the same reason as
     * {@link #setSurplusSuppressionEnabled} -- an unforeseen cost or behavior in practice.
     *
     * @param enabled Whether surplus-lattice absorption should run.
     */
    public synchronized void setSurplusAbsorptionEnabled(boolean enabled) {
        this.checkoutOptions.surplusAbsorptionEnabled = enabled;
    }

    /**
     * Controls whether {@link #compose} and {@link #checkConstraintViolations} warn when a
     * configuration's selected features violate an accepted, currently-still-hard constraint
     * (see {@link ConstraintViolationChecker}). Purely advisory -- like {@link Constraint} itself,
     * this never blocks {@code commit()}/{@code checkout()}, only surfaces a warning. On by default;
     * the escape hatch exists for the same reason as {@link #setSurplusSuppressionEnabled}.
     *
     * @param enabled Whether constraint-violation warnings should be computed.
     */
    public synchronized void setConstraintViolationWarningsEnabled(boolean enabled) {
        this.checkoutOptions.constraintViolationWarningsEnabled = enabled;
    }

    /**
     * The min-witness/confidence thresholds {@link #acceptedSuggestions} re-mines against. A
     * suggestion accepted in the GUI at a looser threshold (e.g. via {@code ConstraintSuggestionsView}'s
     * spinners) is only ever TRUSTED once it also clears these production values -- see that method's
     * javadoc. Exposed (not just inlined) so the GUI can tell a user *why* an accepted constraint isn't
     * yet trusted, without duplicating the actual numbers.
     */
    public static final int ACCEPTED_CONSTRAINT_MIN_WITNESS = 4;
    public static final double ACCEPTED_CONSTRAINT_CONFIDENCE = 0.9;

    /**
     * Mines fresh from the currently committed configurations and filters to the signatures of
     * constraints already persisted (accepted) in the repository -- the same construction sequence
     * used by {@code MinimizePreviewCommand}/{@code MinimizationResults}' "minimize" pipeline, kept
     * in sync with those rather than diverging into a second implementation.
     *
     * <p><b>Safety-critical:</b> a persisted {@link Constraint} is only trusted here if it ALSO still
     * re-mines as hard against the CURRENT data -- never reconstructed directly from storage. A
     * constraint accepted as hard could have since picked up a counterexample from a later commit;
     * trusting a stale accepted-but-no-longer-hard constraint would make
     * {@link SurplusModuleSuppressor} vacuously "prove" every surplus candidate suppressible. See
     * {@code CONSTRAINT_MINING_DESIGN.md}'s "Crucial safety rule". Moving accepted-constraint storage
     * from local {@link ConstraintSuggestionPreferences} into the repository itself (so it travels
     * with fork/pull/push) must not weaken this guarantee -- only the source of "which signatures were
     * accepted" changed, the fresh-mine-then-filter shape did not.
     *
     * <p>Note this re-mines at the fixed {@link #ACCEPTED_CONSTRAINT_MIN_WITNESS}/
     * {@link #ACCEPTED_CONSTRAINT_CONFIDENCE}, not whatever threshold was used to accept a suggestion
     * in the GUI -- a constraint accepted under a looser threshold silently drops out here until it
     * also earns enough witnesses at the production threshold. Intentional (see javadoc above); the
     * GUI's Accepted list surfaces this to the user rather than leaving it silent.
     *
     * @param repository the already-loaded repository to read accepted constraints and commit
     *                    configurations from -- passed in rather than loaded internally so callers
     *                    already holding one (e.g. {@link #compose}) don't pay for a second,
     *                    independent {@code repositoryDao.load()} and don't risk this method seeing a
     *                    different in-memory {@code Repository.Op} instance than the rest of the call.
     *                    Public: also exercised directly by {@code AcceptedConstraintStaleReMineTest}
     *                    to pin the safety property above, and by the GUI's Accepted-constraints status
     *                    display.
     */
    public List<ConstraintMiner.Suggestion> acceptedSuggestions(Repository repository) {
        return this.constraintService.acceptedSuggestions(repository);
    }

    /**
     * Accepts a mined constraint suggestion, persisting it into the repository itself (travels with
     * fork/pull/push, unlike the old {@link ConstraintSuggestionPreferences}-based tracking). Purely
     * advisory bookkeeping -- see {@link #acceptedSuggestions} for why this is never trusted as-is
     * without re-verification against fresh mining.
     *
     * @param suggestion the mined suggestion to accept.
     */
    public synchronized void acceptConstraint(ConstraintMiner.Suggestion suggestion) {
        this.constraintService.acceptConstraint(suggestion);
    }

    /**
     * Lower-level variant taking kind/a/b directly (e.g. for test fixtures or GUI undo round-trips).
     *
     * @param kind     of the constraint.
     * @param featureA antecedent / first feature / mandatory feature name.
     * @param featureB consequent / second feature name; null for MANDATORY.
     */
    public synchronized void acceptConstraint(Constraint.Kind kind, String featureA, String featureB) {
        this.constraintService.acceptConstraint(kind, featureA, featureB);
    }

    /**
     * Batched variant of {@link #acceptConstraint(ConstraintMiner.Suggestion)} - accepts every
     * suggestion as one repository transaction/event instead of one each, so accepting many at once
     * (e.g. a multi-select in the GUI) doesn't redo a full re-mine/re-render per item. See
     * {@link ConstraintService#acceptConstraints} for why that mattered in practice.
     *
     * @param suggestions the mined suggestions to accept; a no-op if empty.
     */
    public synchronized void acceptConstraints(List<ConstraintMiner.Suggestion> suggestions) {
        this.constraintService.acceptConstraints(suggestions);
    }

    /**
     * "Move back to pending" / undo-accept: removes a persisted constraint, if present.
     *
     * @param kind     of the constraint.
     * @param featureA antecedent / first feature / mandatory feature name.
     * @param featureB consequent / second feature name; null for MANDATORY.
     */
    public synchronized void unacceptConstraint(Constraint.Kind kind, String featureA, String featureB) {
        this.constraintService.unacceptConstraint(kind, featureA, featureB);
    }

    /**
     * Batched variant of {@link #unacceptConstraint} - see {@link #acceptConstraints}/
     * {@link ConstraintService#acceptConstraints} for why batching matters.
     *
     * @param constraints the previously-accepted constraints to move back to pending; a no-op if empty.
     */
    public synchronized void unacceptConstraints(List<ConstraintSuggestionPreferences.AcceptedConstraint> constraints) {
        this.constraintService.unacceptConstraints(constraints);
    }

    /**
     * Rejects suggestions, so they are not proposed again; stored in the repository, so the decision
     * travels with fork/pull/push. See {@link ConstraintService#rejectConstraints}.
     */
    public synchronized void rejectConstraints(List<ConstraintMiner.Suggestion> suggestions) {
        this.constraintService.rejectConstraints(suggestions);
    }

    /**
     * Rejects one constraint given by kind and features (e.g. from the command line). See
     * {@link ConstraintService#rejectConstraints}.
     */
    public synchronized void rejectConstraint(Constraint.Kind kind, String featureA, String featureB) {
        this.constraintService.rejectConstraint(kind, featureA, featureB);
    }

    /**
     * "Move back to pending" for rejected suggestions. See {@link ConstraintService#unrejectConstraints}.
     */
    public synchronized void unrejectConstraints(List<ConstraintSuggestionPreferences.AcceptedConstraint> constraints) {
        this.constraintService.unrejectConstraints(constraints);
    }

    /** Accepts parents in the feature model, each with its REQUIRES. See {@link ConstraintService#acceptFeatureParents}. */
    public synchronized void acceptFeatureParents(Map<String, String> parents) {
        this.constraintService.acceptFeatureParents(parents);
    }

    /** See {@link ConstraintService#rejectFeatureParents}. */
    public synchronized void rejectFeatureParents(List<Map.Entry<String, String>> parents) {
        this.constraintService.rejectFeatureParents(parents);
    }

    /** See {@link ConstraintService#unacceptFeatureParents}. */
    public synchronized void unacceptFeatureParents(Collection<String> features) {
        this.constraintService.unacceptFeatureParents(features);
    }

    /** See {@link ConstraintService#unrejectFeatureParents}. */
    public synchronized void unrejectFeatureParents(List<Map.Entry<String, String>> parents) {
        this.constraintService.unrejectFeatureParents(parents);
    }

    /**
     * See {@link ConstraintService#moveLocalRejectionsIntoRepository}.
     */
    public synchronized int moveLocalRejectionsIntoRepository() {
        return this.constraintService.moveLocalRejectionsIntoRepository();
    }

    /** See {@link MinimizationService#minimizationBases}. */
    public synchronized Map<String, String> minimizationBases() {
        return this.minimizationService.minimizationBases();
    }

    /** See {@link MinimizationService#validMinimizedConditions}. */
    public synchronized Map<String, String> validMinimizedConditions() {
        return this.minimizationService.validMinimizedConditions();
    }

    /** See {@link MinimizationService#persistMinimizedConditions}. */
    public synchronized void persistMinimizedConditions(Map<String, String> minimizedByAssociationId, Map<String, String> basisByAssociationId) {
        this.minimizationService.persistMinimizedConditions(minimizedByAssociationId, basisByAssociationId);
    }

    /**
     * Whether checkout uses the stored revision-exact minimized conditions
     * ({@link #minimizeConditionsForCheckout}) of the associations that have a valid one, instead of
     * their own. They are equivalent for every configuration the trusted accepted constraints allow;
     * for a configuration that violates one, the checkout can differ (and warns about the violation).
     * Off by default.
     */
    public synchronized void setMinimizedConditionsInCheckout(boolean enabled) {
        this.checkoutOptions.minimizedConditionsInCheckout = enabled;
    }

    public boolean isMinimizedConditionsInCheckout() {
        return this.checkoutOptions.minimizedConditionsInCheckout;
    }

    /** See {@link MinimizationService#minimizeConditionsForCheckout}: not synchronized, the minimization itself runs without this service's lock. */
    public int minimizeConditionsForCheckout() {
        return this.minimizationService.minimizeConditionsForCheckout();
    }

    /** See {@link MinimizationService#validCheckoutConditions}. */
    public synchronized Map<String, LogicCondition> validCheckoutConditions() {
        return this.minimizationService.validCheckoutConditions();
    }

    /**
     * Composes checkout with given configuration - see {@link CheckoutService#compose}.
     */
    synchronized Checkout compose(Configuration configuration) {
        return this.checkoutService.compose(configuration);
    }

    /**
     * Checks out the implementation of the given configuration into the base directory, together with
     * its .config and .warnings files - see {@link CheckoutService#checkout(Configuration)}.
     *
     * @param configuration The configuration to be checked out.
     * @return The checkout object.
     */
    public synchronized Checkout checkout(Configuration configuration) {
        return this.checkoutService.checkout(configuration);
    }

    public synchronized Checkout checkout(Node node) {
        return this.checkoutService.checkout(node);
    }

    /**
     * Writes the artifacts of [node] into [directory], leaving the base directory as it is -
     * "Checkout Selected" set the base directory to the folder chosen, also when it was cancelled.
     */
    public synchronized Checkout checkout(Node node, Path directory) {
        return this.checkoutService.checkout(node, directory);
    }

    /**
     * Re-writes the file enclosing {@code node} - see {@link CheckoutService#writeCheckoutFile}.
     */
    public synchronized Path[] writeCheckoutFile(Node node) {
        return this.checkoutService.writeCheckoutFile(node);
    }

    /**
     * Records the order of {@code orderedChildren}, children of {@code parent} in a checkout, in the
     * repository - typically to resolve an ORDER warning. Only the parent's partial order graph
     * changes ({@link at.jku.isse.ecco.pog.PartialOrderGraph.Op#addOrder}): no commit is made and no
     * presence condition changes, so the order holds in every later checkout that contains these
     * children, whatever its configuration. A commit would instead record the whole checkout as a
     * variant (Known gap #23).
     *
     * @return how many precedences were added; 0 if the repository already had the order
     * @throws EccoException if the parent has no order graph, is not part of this repository, or the
     *                       order contradicts one recorded before; the repository is then unchanged.
     */
    public synchronized int recordOrder(Node parent, List<? extends Node> orderedChildren) {
        return this.orderService.recordOrder(parent, orderedChildren);
    }

    /**
     * Records the order of files as they are now in the working directory, which holds a checkout -
     * reorder them, then record (the command line's {@code order}). See
     * {@link OrderService#recordOrderOfFiles}.
     *
     * @param files paths relative to the working directory
     * @return how many precedences were added; 0 if the repository already had the files' order
     */
    public synchronized int recordOrderOfFiles(List<Path> files) {
        return this.orderService.recordOrderOfFiles(files);
    }

    /**
     * Checks whether the given configuration's selected features violate any accepted, currently
     * hard constraint (see {@link ConstraintViolationChecker}). Purely advisory: does not affect
     * {@code commit()}/{@code checkout()}; callers (e.g. the GUI, right after a successful commit)
     * decide how to surface the result.
     *
     * @param configuration The configuration to check.
     * @return Human-readable descriptions of violated constraints; empty if none.
     */
    public synchronized List<String> checkConstraintViolations(Configuration configuration) {
        return this.constraintService.checkConstraintViolations(configuration);
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
     * Refuses, as {@link #checkout(String)} does before writing anything, a configuration that cannot
     * be parsed or names a feature or revision never committed - for a caller with something to do
     * before the checkout: the GUI deleted the folder's contents first, then the checkout was refused.
     *
     * @return the parsed configuration
     */
    public synchronized Configuration checkCheckoutConfiguration(String configurationString) {
        Configuration configuration = this.parseConfigurationString(configurationString);
        this.checkoutService.checkCommitted(configuration);
        return configuration;
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

        return this.context.read("Error during map.", () -> {
            Repository.Op repository = this.context.repositoryDao.load();

            Set<Node.Op> nodes = this.context.reader.readSpecificFiles(this.baseDir, paths.toArray(new Path[0]));

            RootNode.Op rootNode = this.context.entityFactory.createRootNode();
            for (Node.Op node : nodes) {
                rootNode.addChild(node);
            }

            repository.map(rootNode);
            return rootNode;
        });
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
