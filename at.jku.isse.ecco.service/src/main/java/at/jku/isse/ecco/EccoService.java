package at.jku.isse.ecco;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Checkout;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.core.Remote;
import at.jku.isse.ecco.dao.*;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureVersion;
import at.jku.isse.ecco.listener.EccoListener;
import at.jku.isse.ecco.listener.ReadListener;
import at.jku.isse.ecco.listener.ServerListener;
import at.jku.isse.ecco.listener.WriteListener;
import at.jku.isse.ecco.plugin.CoreModule;
import at.jku.isse.ecco.plugin.artifact.*;
import at.jku.isse.ecco.plugin.data.DataPlugin;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.tree.Node;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.name.Names;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A service class that gives access to high level operations like init, fork, pull, push, etc.
 */
public class EccoService implements ProgressInputStream.ProgressListener, ProgressOutputStream.ProgressListener, ReadListener, WriteListener, Closeable {

	protected static final Logger LOGGER = LoggerFactory.getLogger(EccoService.class);

	public enum Operation {
		OPEN, INIT, FORK, CLOSE, COMMIT, CHECKOUT, FETCH, PULL, PUSH, SERVER, OTHER
	}

	public static final String ORIGIN_REMOTE_NAME = "origin";

	public static final String ECCO_PROPERTIES_FILE = "ecco.properties";
	public static final String ECCO_PROPERTIES_DATA = "plugin.data";
	public static final String ECCO_PROPERTIES_ARTIFACT = "plugin.artifact";

	public static final Path REPOSITORY_DIR_NAME = Paths.get(".ecco");
	public static final Path DEFAULT_BASE_DIR = Paths.get("");
	public static final Path DEFAULT_REPOSITORY_DIR = DEFAULT_BASE_DIR.resolve(REPOSITORY_DIR_NAME);
	public static final Path CONFIG_FILE_NAME = Paths.get(".config");
	public static final Path WARNINGS_FILE_NAME = Paths.get(".warnings");
	public static final Path HASHES_FILE_NAME = Paths.get(".hashes");
	public static final Path IGNORES_FILE_NAME = Paths.get(".ignores");


	private Properties properties = new Properties();

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


	// TODO: set current operation. update progress during operations (instead of just relaying the progress from input and output streams) and notify listeners.
	private Operation currentOperation;
	private int maxAbsoluteProgress;


	/**
	 * Creates the service and tries to detect an existing repository automatically using {@link #detectRepository(Path path) detectRepository}. If no existing repository was found the base directory (directory from which files are committed and checked out) and repository directory (directory at which the repository data is stored) are set to their defaults:
	 * <p>
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

//		this.ignoredFiles.add(REPOSITORY_DIR_NAME);
//		this.ignoredFiles.add(CONFIG_FILE_NAME);

		this.defaultIgnorePatterns.add("glob:" + REPOSITORY_DIR_NAME.toString());
		this.defaultIgnorePatterns.add("glob:" + CONFIG_FILE_NAME.toString());
		this.defaultIgnorePatterns.add("glob:" + WARNINGS_FILE_NAME.toString());
		this.defaultIgnorePatterns.add("glob:" + HASHES_FILE_NAME.toString());


		// load properties file
		InputStream inputStream = getClass().getClassLoader().getResourceAsStream(ECCO_PROPERTIES_FILE);
		if (inputStream != null) {
			try {
				this.properties.load(inputStream);
			} catch (IOException e) {
				LOGGER.error("Could not load properties from file '" + ECCO_PROPERTIES_FILE + "'.", e);
			}
		} else {
			throw new EccoException("Property file '" + ECCO_PROPERTIES_FILE + "' not found in the classpath.");
		}
		LOGGER.debug("PROPERTIES: " + this.properties);
	}


	private Collection<ArtifactPlugin> artifactPlugins;
	private Collection<DataPlugin> dataPlugins;
	private DataPlugin dataPlugin;

	private Injector injector;

	private boolean initialized = false;

	public boolean isInitialized() {
		return this.initialized;
	}

	private MemEntityFactory memEntityFactory = new MemEntityFactory();

	@Inject
	private DispatchReader reader;
	@Inject
	private DispatchWriter writer;

//	public ArtifactReader getReader() {
//		return this.reader;
//	}
//
//	public ArtifactWriter getWriter() {
//		return this.writer;
//	}

	@Inject
	private EntityFactory entityFactory;

	@Inject
	private TransactionStrategy transactionStrategy;

	// DAOs
	@Inject
	private RepositoryDao repositoryDao;
	@Inject
	private SettingsDao settingsDao;
	@Inject
	private CommitDao commitDao;


	// # IGNORE ########################################################################################################

	private Set<String> defaultIgnorePatterns = new HashSet<>();
	private Set<String> customIgnorePatterns = new HashSet<>(); // TODO: set this

	public Set<String> getIgnorePatterns() {
		//return this.settingsDao.loadIgnorePatterns();
		return Collections.unmodifiableSet(this.customIgnorePatterns);
	}

	public void addIgnorePattern(String ignorePattern) {
//		try {
//			this.transactionStrategy.begin();
//
//			this.settingsDao.addIgnorePattern(ignorePattern);
//
//			this.transactionStrategy.end();
//		} catch (Exception e) {
//			this.transactionStrategy.rollback();
//
//			throw new EccoException("Error adding ignore pattern.", e);
//		}
		this.customIgnorePatterns.add(ignorePattern);
	}

	public void removeIgnorePattern(String ignorePattern) {
//		try {
//			this.transactionStrategy.begin();
//
//			this.settingsDao.removeIgnorePattern(ignorePattern);
//
//			this.transactionStrategy.end();
//		} catch (Exception e) {
//			this.transactionStrategy.rollback();
//
//			throw new EccoException("Error removing ignore pattern.", e);
//		}
		this.customIgnorePatterns.remove(ignorePattern);
	}


	// # LISTENERS #####################################################################################################

	private Collection<EccoListener> listeners = new ArrayList<>();

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

	protected void fireStatusChangedEvent() {
		for (EccoListener listener : this.listeners) {
			listener.statusChangedEvent(this);
		}
	}

	protected void fireOperationProgressEvent(String operationString, double progress) {
		for (EccoListener listener : this.listeners) {
			listener.operationProgressEvent(this, operationString, progress);
		}
	}

	protected void fireReadEvent(Path path, ArtifactReader reader) {
		for (ReadListener listener : this.listeners) {
			listener.fileReadEvent(path, reader);
		}
	}

	protected void fireWriteEvent(Path path, ArtifactWriter writer) {
		for (WriteListener listener : this.listeners) {
			listener.fileWriteEvent(path, writer);
		}
	}


	// repository events

	protected void fireCommitsChangedEvent(Commit commit) {
		for (EccoListener listener : this.listeners) {
			listener.commitsChangedEvent(this, commit);
		}
	}

	protected void fireAssociationSelectedEvent(Association association) {
		for (EccoListener listener : this.listeners) {
			listener.associationSelectedEvent(this, association);
		}
	}


	// server events

	protected void fireServerEvent(String message) {
		for (ServerListener listener : this.listeners) {
			listener.serverEvent(this, message);
		}
	}

	protected void fireServerStartedEvent(int port) {
		for (ServerListener listener : this.listeners) {
			listener.serverStartEvent(this, port);
		}
	}

	protected void fireServerStoppedEvent() {
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

	protected Collection<Module> inititializeService() {
		if (this.isInitialized()) {
			LOGGER.error("Repository is already open.");
			throw new EccoException("Repository is already open.");
		}

		LOGGER.debug("PROPERTIES: " + this.properties);
		if (this.properties.getProperty(ECCO_PROPERTIES_DATA) == null) {
			throw new EccoException("No data plugin specified.");
		}
		Collection<String> artifactPluginsList = new ArrayList<>();
		if (this.properties.getProperty(ECCO_PROPERTIES_ARTIFACT) != null) {
			artifactPluginsList = Arrays.asList(this.properties.getProperty(ECCO_PROPERTIES_ARTIFACT).split(","));
			LOGGER.debug("Found optional property: " + ECCO_PROPERTIES_ARTIFACT);
		}

		// artifact modules
		List<Module> artifactModules = new ArrayList<>();
		List<Module> allArtifactModules = new ArrayList<>();
		this.artifactPlugins = new ArrayList<>();
		for (ArtifactPlugin ap : ArtifactPlugin.getArtifactPlugins()) {
			if (artifactPluginsList == null || artifactPluginsList.contains(ap.getPluginId())) {
				artifactModules.add(ap.getModule());
				this.artifactPlugins.add(ap);
			}
			allArtifactModules.add(ap.getModule());
		}
		LOGGER.debug("ARTIFACT PLUGINS: " + artifactModules.toString());
		LOGGER.debug("ALL ARTIFACT PLUGINS: " + allArtifactModules.toString());

		// data modules
		List<Module> dataModules = new ArrayList<>();
		List<Module> allDataModules = new ArrayList<>();
		for (DataPlugin dataPlugin : DataPlugin.getDataPlugins()) {
			if (dataPlugin.getPluginId().equals(this.properties.get(ECCO_PROPERTIES_DATA))) {
				dataModules.add(dataPlugin.getModule());
				this.dataPlugin = dataPlugin;
			}
			allDataModules.add(dataPlugin.getModule());
		}
		LOGGER.debug("DATA PLUGINS: " + dataModules.toString());
		LOGGER.debug("ALL DATA PLUGINS: " + allDataModules.toString());

		// put modules together
		List<Module> modules = new ArrayList<>();
		modules.add(new CoreModule());
		modules.addAll(artifactModules);
		modules.addAll(dataModules);

		return modules;
	}

	/**
	 * Initializes the service.
	 */
	public synchronized void open() {
		if (!this.repositoryDirectoryExists()) {
			LOGGER.error("Repository does not exist.");
			throw new EccoException("Repository does not exist.");
		}

		LOGGER.debug("BASE_DIR: " + this.baseDir);
		LOGGER.debug("REPOSITORY_DIR: " + this.repositoryDir);

		Collection<Module> modules = this.inititializeService();

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

		this.injector = injector;

		injector.injectMembers(this);

		this.transactionStrategy.open();

		this.repositoryDao.init();
		this.settingsDao.init();
		this.commitDao.init();


		// ignored file patterns
		try {
			Path ignoresFile = this.repositoryDir.resolve(IGNORES_FILE_NAME);
			if (!Files.exists(ignoresFile))
				Files.createFile(ignoresFile);
			this.defaultIgnorePatterns.addAll(Files.readAllLines(ignoresFile));
		} catch (IOException e) {
			throw new EccoException("Error creating or reading ignores file.", e);
		}
		this.reader.getIgnorePatterns().clear();
		this.reader.getIgnorePatterns().addAll(this.customIgnorePatterns);
		this.reader.getIgnorePatterns().addAll(this.defaultIgnorePatterns);

		this.reader.addListener(this);
		this.writer.addListener(this);

		this.initialized = true;

		this.fireStatusChangedEvent();

		LOGGER.debug("Repository opened.");
	}

	/**
	 * Properly shuts down the service.
	 */
	@Override
	public synchronized void close() {
		if (!this.initialized)
			return;

		this.initialized = false;

		this.reader.removeListener(this);
		this.writer.removeListener(this);

		this.repositoryDao.close();
		this.settingsDao.close();
		this.commitDao.close();

		this.transactionStrategy.close();

		this.fireStatusChangedEvent();

		LOGGER.debug("Repository closed.");
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
			this.transactionStrategy.begin();

			if (this.getRemote(name) != null)
				throw new EccoException("Remote with this name already exists.");

			//Remote.Type type = Remote.Type.valueOf(typeString);
			Remote remote = this.entityFactory.createRemote(name, address, type);
			remote = this.settingsDao.storeRemote(remote);

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
			this.transactionStrategy.begin();

			this.settingsDao.removeRemote(name);

			this.transactionStrategy.end();
		} catch (Exception e) {
			this.transactionStrategy.rollback();

			throw new EccoException("Error removing remote.", e);
		}
	}

	public synchronized Remote getRemote(String name) {
		this.checkInitialized();

		try {
			this.transactionStrategy.begin();

			Remote remote = this.settingsDao.loadRemote(name);

			this.transactionStrategy.end();

			return remote;
		} catch (Exception e) {
			this.transactionStrategy.rollback();

			throw new EccoException("Error retrieving remote.", e);
		} finally {

		}
	}

	public synchronized Collection<Remote> getRemotes() {
		this.checkInitialized();

		try {
			this.transactionStrategy.begin();

			Collection<Remote> remotes = this.settingsDao.loadAllRemotes();

			this.transactionStrategy.end();

			return remotes;
		} catch (Exception e) {
			this.transactionStrategy.rollback();

			throw new EccoException("Error retrieving remotes.", e);
		} finally {

		}
	}


	public synchronized Repository getRepository() {
		this.checkInitialized();

		try {
			this.repositoryDao.init();
			this.transactionStrategy.begin();
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
			this.commitDao.init();
			this.transactionStrategy.begin();
			List<Commit> commits = this.commitDao.loadAllCommits();
			this.transactionStrategy.end();
			return commits;
		} catch (EccoException e) {
			this.transactionStrategy.rollback();
			throw new EccoException("Error when retrieving commits.", e);
		}
	}


//	/**
//	 * Creates a presence condition from a given string. Uses existing features and feature versions from the repository. Adds new features and feature versions to the repository.
//	 *
//	 * @param pcString The presence condition string.
//	 * @return The parsed presence condition.
//	 */
//	public PresenceCondition parsePresenceConditionString(String pcString) {
//		if (pcString == null)
//			throw new EccoException("No presence condition string provided.");
//
//		if (!pcString.matches("\\([+-]?[a-zA-Z0-9_-]+(\\.((\\{([+-]?[0-9]+)(\\s*,\\s*([+-]?[0-9]+))*\\})|([+-]?[0-9]+))+)?(\\s*,\\s*[+-]?[a-zA-Z0-9_-]+(\\.((\\{([+-]?[0-9]+)(\\s*,\\s*([+-]?[0-9]+))*\\})|([+-]?[0-9]+))+)?)*\\)(\\s*,\\s*\\([+-]?[a-zA-Z0-9_-]+(\\.((\\{([+-]?[0-9]+)(\\s*,\\s*([+-]?[0-9]+))*\\})|([+-]?[0-9]+))+)?(\\s*,\\s*[+-]?[a-zA-Z0-9_-]+(\\.((\\{([+-]?[0-9]+)(\\s*,\\s*([+-]?[0-9]+))*\\})|([+-]?[0-9]+))+)?)*\\))*"))
//			throw new EccoException("Invalid presence condition string provided.");
//
//		try {
//			this.transactionStrategy.begin();
//
//			PresenceCondition pc = this.entityFactory.createPresenceCondition();
//
//
//			// modules
//			Pattern modulePattern = Pattern.compile("\\(([^()]*)\\)");
//			Matcher moduleMatcher = modulePattern.matcher(pcString);
//			while (moduleMatcher.find()) {
//				for (int i = 0; i < moduleMatcher.groupCount(); i++) {
//					String moduleString = moduleMatcher.group(i);
//
//					at.jku.isse.ecco.module.Module module = this.entityFactory.createModule();
//
//					// module features
//					Pattern moduleFeaturePattern = Pattern.compile("(\\+|\\-)?([a-zA-Z0-9_-]+)(.(\\{[+-]?[a-zA-Z0-9_-]+(\\s*,\\s*[+-]?[a-zA-Z0-9_-]+)*\\}|[+-]?[0-9]+))?");
//					Matcher moduleFeatureMatcher = moduleFeaturePattern.matcher(moduleString);
//					while (moduleFeatureMatcher.find()) {
//						String featureSignString = moduleFeatureMatcher.group(1);
//						boolean featureSign = (featureSignString != null && featureSignString.equals("-")) ? false : true;
//						String featureName = moduleFeatureMatcher.group(2);
//
//						Feature feature;
//						try {
//							feature = this.featureDao.load(featureName);
//						} catch (IllegalArgumentException e) {
//							feature = null;
//						}
//						if (feature == null) {
//							feature = this.entityFactory.createFeature(featureName);
////							feature = this.featureDao.save(feature);
//						}
//
//						Collection<FeatureVersion> featureVersions = new ArrayList<>();
//
//						// versions
//						//Pattern pattern3 = Pattern.compile("\\{([0-9]+)(\\s*,\\s*([0-9]+))*\\}");
//						Pattern versionPattern = Pattern.compile("[^+-0-9{}, ]*([+-]?[0-9]+)[^+-0-9{}, ]*");
//						if (moduleFeatureMatcher.group(3) != null) {
//							Matcher versionMatcher = versionPattern.matcher(moduleFeatureMatcher.group(4));
//
//							while (versionMatcher.find()) {
//								String id = versionMatcher.group(0);
//
////								FeatureVersion tempFeatureVersion = this.entityFactory.createFeatureVersion(feature, version);
////								FeatureVersion featureVersion = feature.getId(tempFeatureVersion);
//								FeatureVersion featureVersion = feature.getVersion(id);
//								if (featureVersion == null) {
////									feature.addVersion(tempFeatureVersion);
////									featureVersion = tempFeatureVersion;
//									featureVersion = feature.addVersion(id);
////									feature = this.featureDao.save(feature);
//								}
//
//								featureVersions.add(featureVersion);
//							}
//						} else {
//							//FeatureVersion tempFeatureVersion = this.entityFactory.createFeatureVersion(feature, FeatureVersion.NEWEST);
//							//FeatureVersion featureVersion = feature.getId(tempFeatureVersion);
//							FeatureVersion featureVersion = feature.getLatestVersion();
//							if (featureVersion == null) {
////								feature.addVersion(tempFeatureVersion);
////								featureVersion = tempFeatureVersion;
//								featureVersion = feature.createNewVersion();
//							}
//							featureVersions.add(featureVersion);
//						}
//
//						ModuleFeature mf = this.entityFactory.createModuleFeature(feature, featureVersions, featureSign);
//						module.add(mf);
//
//						feature = this.featureDao.save(feature);
//					}
//
//					pc.getMinModules().add(module);
//				}
//			}
//
//			this.transactionStrategy.end();
//
//			return pc;
//		} catch (Exception e) {
//			this.transactionStrategy.rollback();
//
//			throw new EccoException("Error parsing presence condition string: " + pcString, e);
//		}
//	}


	protected Configuration parseConfigurationString(String configurationString) {
		if (configurationString == null)
			throw new EccoException("No configuration string provided.");

		if (!configurationString.matches(Configuration.CONFIGURATION_STRING_REGULAR_EXPRESSION))
			throw new EccoException("Invalid configuration string provided.");

		try {
			this.transactionStrategy.begin();

			Configuration configuration = this.entityFactory.createConfiguration();

			if (configurationString.isEmpty()) {
				this.transactionStrategy.end();
				return configuration;
			}

			Repository.Op repository = this.repositoryDao.load();

			Set<FeatureVersion> newFeatureVersions = new HashSet<>();

			String[] featureInstanceStrings = configurationString.split(",");
			for (String featureInstanceString : featureInstanceStrings) {
				featureInstanceString = featureInstanceString.trim();

				if (featureInstanceString.contains(".")) { // use specified feature version
					String[] pair = featureInstanceString.split("\\.");
					String featureName = pair[0];
					if (featureName.startsWith("!") || featureName.startsWith("-") || featureName.startsWith("+"))
						featureName = featureName.substring(1);
					String id = pair[1];
					boolean featureSign = !(pair[0].startsWith("!") || pair[0].startsWith("-"));

					Feature feature;
					if (featureName.startsWith("[") && featureName.endsWith("]")) { // id
						feature = repository.getFeature(featureName);
					} else { // name
						Collection<Feature> features = repository.getFeaturesByName(featureName);
						if (features.isEmpty()) {
							feature = repository.addFeature(UUID.randomUUID().toString(), featureName, "");
						} else if (features.size() == 1) {
							feature = features.iterator().next();
						} else {
							throw new EccoException("Feature name is not unique. Use feature id instead.");
						}
					}

					FeatureVersion featureVersion = feature.getVersion(id);
					if (featureVersion == null) {
						featureVersion = feature.addVersion(id);
						newFeatureVersions.add(featureVersion);
					}

					//configuration.addFeatureInstance(this.entityFactory.createFeatureInstance(feature, featureVersion, featureSign));
					configuration.addFeatureInstance(featureVersion.getInstance(featureSign));
				} else if (featureInstanceString.endsWith("'")) { // create new feature version for feature
					String featureName = featureInstanceString.substring(0, featureInstanceString.length() - 1);
					if (featureName.startsWith("!") || featureName.startsWith("-") || featureName.startsWith("+"))
						featureName = featureName.substring(1);

					Feature feature;
					if (featureName.startsWith("[") && featureName.endsWith("]")) { // id
						feature = repository.getFeature(featureName);
					} else { // name
						Collection<Feature> features = repository.getFeaturesByName(featureName);
						if (features.isEmpty()) {
							feature = repository.addFeature(UUID.randomUUID().toString(), featureName, "");
						} else if (features.size() == 1) {
							feature = features.iterator().next();
						} else {
							throw new EccoException("Feature name is not unique. Use feature id instead.");
						}
					}

					//FeatureVersion featureVersion = feature.createNewVersion();
					FeatureVersion featureVersion = feature.addVersion(UUID.randomUUID().toString());
					newFeatureVersions.add(featureVersion);

					boolean featureSign = !(featureInstanceString.startsWith("!") || featureInstanceString.startsWith("-"));

					//configuration.addFeatureInstance(this.entityFactory.createFeatureInstance(feature, featureVersion, featureSign));
					configuration.addFeatureInstance(featureVersion.getInstance(featureSign));
				} else { // use most recent feature version of feature (or create a new one if none existed so far)
					String featureName = featureInstanceString;
					if (featureName.startsWith("!") || featureName.startsWith("-") || featureName.startsWith("+"))
						featureName = featureName.substring(1);

					Feature feature;
					if (featureName.startsWith("[") && featureName.endsWith("]")) { // id
						feature = repository.getFeature(featureName);
						if (feature == null) {
							feature = repository.addFeature(featureName, "", "");
						}
					} else { // name
						Collection<Feature> features = repository.getFeaturesByName(featureName);
						if (features.isEmpty()) {
							feature = repository.addFeature(UUID.randomUUID().toString(), featureName, "");
						} else if (features.size() == 1) {
							feature = features.iterator().next();
						} else {
							throw new EccoException("Feature name is not unique. Use feature id instead.");
						}
					}

					FeatureVersion featureVersion = feature.getLatestVersion();
					if (featureVersion == null) {
						//featureVersion = feature.createNewVersion();
						featureVersion = feature.addVersion(UUID.randomUUID().toString());
						newFeatureVersions.add(featureVersion);
					}

					boolean featureSign = !(featureInstanceString.startsWith("!") || featureInstanceString.startsWith("-"));

					//configuration.addFeatureInstance(this.entityFactory.createFeatureInstance(feature, featureVersion, featureSign));
					configuration.addFeatureInstance(featureVersion.getInstance(featureSign));
				}
			}

			// update existing associations with new (features and) feature versions. NOTE: update with negative features is not necessary if the configurations contain also all the negative features!
			Collection<? extends Association> associations = repository.getAssociations();
			for (Association association : associations) {
				for (FeatureVersion newFeatureVersion : newFeatureVersions) {
					association.getPresenceCondition().addFeatureVersion(newFeatureVersion);
					//association.getPresenceCondition().addFeatureInstance(this.entityFactory.createFeatureInstance(newFeatureVersion.getFeature(), newFeatureVersion, false), repository.getMaxOrder());
					association.getPresenceCondition().addFeatureInstance(newFeatureVersion.getInstance(false), repository.getMaxOrder());
				}
			}

			this.repositoryDao.store(repository);

			this.transactionStrategy.end();

			return configuration;

		} catch (Exception e) {
			this.transactionStrategy.rollback();

			throw new EccoException("Error parsing configuration string: " + configurationString, e);
		}
	}


	protected Collection<FeatureVersion> parseFeatureVersionsString(String featureVersionsString) {
		if (featureVersionsString == null)
			throw new EccoException("No feature versions string provided.");

		if (!featureVersionsString.matches("(((\\[[a-zA-Z0-9_-]+\\])|([a-zA-Z0-9_-]+))(\\.([a-zA-Z0-9_-])+)(\\s*,\\s*((\\[[a-zA-Z0-9_-]+\\])|([a-zA-Z0-9_-]+))(\\.([a-zA-Z0-9_-])+))*)?"))
			throw new EccoException("Invalid feature versions string provided.");

		try {
			this.transactionStrategy.begin();

			Collection<FeatureVersion> featureVersions = new ArrayList<>();

			if (featureVersionsString.isEmpty()) {
				this.transactionStrategy.end();
				return featureVersions;
			}

			Repository.Op repository = this.repositoryDao.load();

			String[] featureVersionStrings = featureVersionsString.split(",");
			for (String featureVersionString : featureVersionStrings) {
				featureVersionString = featureVersionString.trim();

				String[] pair = featureVersionString.split("\\.");
				String featureName = pair[0];
				String versionId = pair[1];

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

				FeatureVersion featureVersion = feature.getVersion(versionId);
				if (featureVersion != null) {
					featureVersions.add(featureVersion);
				}
			}

			this.transactionStrategy.end();

			return featureVersions;
		} catch (Exception e) {
			this.transactionStrategy.rollback();

			throw new EccoException("Error parsing feature versions string: " + featureVersionsString, e);
		}
	}


	// # CORE SERVICES #################################################################################################


	// DISTRIBUTED OPERATIONS //////////////////////////////////////////////////////////////////////////////////////////

	private ServerSocketChannel ssChannel = null;
	private boolean serverShutdown = false;
	private boolean serverRunning = false;
	private Lock serverLock = new ReentrantLock();

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

			LOGGER.debug("Server started on port " + port + ".");
			this.fireServerEvent("Server started on port " + port + ".");
			this.fireServerStartedEvent(port);

			while (!serverShutdown) {
				try (SocketChannel sChannel = ssChannel.accept()) {
					ObjectOutputStream oos = new ObjectOutputStream(sChannel.socket().getOutputStream());
					ObjectInputStream ois = new ObjectInputStream(sChannel.socket().getInputStream());


					// determine if it is a push (receive data) or a pull (send data)
					String command = (String) ois.readObject();
					LOGGER.debug("COMMAND: " + command);
					this.fireServerEvent("New connection from " + sChannel.getRemoteAddress() + " with command '" + command + "'.");

					switch (command) {
						case "FETCH": { // if fetch, send data
							// copy features using mem entity factory
							this.transactionStrategy.begin();
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
							//Collection<FeatureVersion> deselected = (Collection<FeatureVersion>) ois.readObject();
							String deselectedFeatureVersionsString = (String) ois.readObject();
							Collection<FeatureVersion> deselected = this.parseFeatureVersionsString(deselectedFeatureVersionsString);

							// compute subset repository using mem entity factory
							this.transactionStrategy.begin();
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
							this.transactionStrategy.begin();
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
					LOGGER.warn("Error receiving request.");
					this.fireServerEvent("Error receiving request: " + e.getMessage());
					e.printStackTrace();
				} catch (Exception e) {
					//throw new EccoException("Error receiving request.", e);
					LOGGER.warn("Error receiving request.");
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

		LOGGER.debug("Server stopped.");
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

				LOGGER.debug("Server stopped.");
			}
		} catch (IOException e) {
			throw new EccoException("Error stopping server.", e);
		}
		this.ssChannel = null;
	}


	public synchronized void fetch(String remoteName) {
		this.checkInitialized();

		try {
			this.transactionStrategy.begin();

			// load remote
			Remote remote = this.settingsDao.loadRemote(remoteName);
			if (remote == null) {
				throw new EccoException("Remote '" + remoteName + "' does not exist.");
			} else if (remote.getType() == Remote.Type.REMOTE) {

				try (SocketChannel sChannel = SocketChannel.open()) {
					sChannel.configureBlocking(true);
					String[] pair = remote.getAddress().split(":");
					if (sChannel.connect(new InetSocketAddress(pair[0], Integer.valueOf(pair[1])))) {
						ProgressInputStream progressInputStream = new ProgressInputStream(sChannel.socket().getInputStream());

						ObjectOutputStream oos = new ObjectOutputStream(sChannel.socket().getOutputStream());
						ObjectInputStream ois = new ObjectInputStream(progressInputStream);

						oos.writeObject("FETCH");


						int size = (Integer) ois.readObject();
						progressInputStream.setMaxBytes(size);
						progressInputStream.resetProgress();
						progressInputStream.addListener(this);

						// retrieve features
						Collection<Feature> features = (Collection<Feature>) ois.readObject();

						progressInputStream.removeListener(this);


						// copy it using this entity factory
						Collection<Feature> copiedFeatures = EccoUtil.deepCopyFeatures(features, this.entityFactory);

						// store with remote
						remote.getFeatures().clear();
						remote.getFeatures().addAll(copiedFeatures);
						this.settingsDao.storeRemote(remote);
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
				this.settingsDao.storeRemote(remote);
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

	public synchronized void fork(String hostname, int port, String deselectedFeatureVersionsString) {
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
				oos.writeObject(deselectedFeatureVersionsString);


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

			this.transactionStrategy.begin();

			// merge into this repository
			Repository.Op repository = this.repositoryDao.load();
			repository.merge(copiedRepository);
			this.repositoryDao.store(repository);

			// after fork add used remote as default origin remote
			Remote remote = this.entityFactory.createRemote(ORIGIN_REMOTE_NAME, hostname + ":" + Integer.toString(port), Remote.Type.REMOTE);
			this.settingsDao.storeRemote(remote);

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
	 * @param originRepositoryDir The directory of the repository from which to fork.
	 */
	public synchronized void fork(Path originRepositoryDir, String deselectedFeatureVersionsString) {
		// check that this service has not yet been initialized and that no repository already exists,
		if (this.isInitialized())
			throw new EccoException("Service must not be initialized for fork operation.");
		if (this.repositoryDirectoryExists())
			throw new EccoException("A repository already exists at the given location: " + this.repositoryDir);

		// create another ecco service and init it on the parent repository directory.
		EccoService originService = new EccoService();
		originService.setRepositoryDir(originRepositoryDir);
		// create subset repository
		Repository.Op subsetOriginRepository;
		try {
			originService.open(); // TODO: init read only! add read only mode for that (also useful for other read only services on a repository such as a read only web interface REST API service).

			originService.transactionStrategy.begin();

			Repository.Op originRepository = originService.repositoryDao.load();
			subsetOriginRepository = originRepository.subset(originService.parseFeatureVersionsString(deselectedFeatureVersionsString), originRepository.getMaxOrder(), this.entityFactory);

			originService.transactionStrategy.end();
		} catch (Exception e) {
			originService.transactionStrategy.rollback();

			throw new EccoException("Error during local fork.", e);
		} finally {
			// close parent repository
			originService.close();
		}

		try {
			this.init();
			this.open();

			this.transactionStrategy.begin();

			// merge into this repository
			Repository.Op repository = this.repositoryDao.load();
			repository.merge(subsetOriginRepository);
			this.repositoryDao.store(repository);

			// after fork add used remote as default origin remote
			Remote remote = this.entityFactory.createRemote(ORIGIN_REMOTE_NAME, originRepositoryDir.toString(), Remote.Type.LOCAL);
			this.settingsDao.storeRemote(remote);

			this.transactionStrategy.end();
		} catch (Exception e) {
			this.transactionStrategy.rollback();

			throw new EccoException("Error during local fork.", e);
		}
	}


	public synchronized void pull(String remoteName) {
		this.pull("", remoteName);
	}

	/**
	 * Pulls the changes from the parent repository to this repository.
	 */
	public synchronized void pull(String remoteName, String deselectedFeatureVersionsString) {
		this.checkInitialized();

		try {
			this.transactionStrategy.begin();

			// load remote
			Remote remote = this.settingsDao.loadRemote(remoteName);
			if (remote == null) {
				throw new EccoException("Remote '" + remoteName + "' does not exist.");
			} else if (remote.getType() == Remote.Type.REMOTE) {

				try (SocketChannel sChannel = SocketChannel.open()) {
					sChannel.configureBlocking(true);
					String[] pair = remote.getAddress().split(":");
					if (sChannel.connect(new InetSocketAddress(pair[0], Integer.valueOf(pair[1])))) {
						ProgressInputStream progressInputStream = new ProgressInputStream(sChannel.socket().getInputStream());

						ObjectOutputStream oos = new ObjectOutputStream(sChannel.socket().getOutputStream());
						ObjectInputStream ois = new ObjectInputStream(progressInputStream);

						oos.writeObject("PULL");
						oos.writeObject(deselectedFeatureVersionsString);


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
					parentService.transactionStrategy.begin();

					Repository.Op parentRepository = parentService.repositoryDao.load();
					subsetParentRepository = parentRepository.subset(parentService.parseFeatureVersionsString(deselectedFeatureVersionsString), parentRepository.getMaxOrder(), this.entityFactory);

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
	 */
	public synchronized void push(String remoteName, String deselectedFeatureVersionsString) {
		this.checkInitialized();

		try {
			this.transactionStrategy.begin();

			// load remote
			Remote remote = this.settingsDao.loadRemote(remoteName);
			if (remote == null) {
				throw new EccoException("Remote " + remoteName + " does not exist");
			} else if (remote.getType() == Remote.Type.REMOTE) {

				try (SocketChannel sChannel = SocketChannel.open()) {
					sChannel.configureBlocking(true);
					String[] pair = remote.getAddress().split(":");
					if (sChannel.connect(new InetSocketAddress(pair[0], Integer.valueOf(pair[1])))) {
						ObjectOutputStream oos = new ObjectOutputStream(sChannel.socket().getOutputStream());
						ObjectInputStream ois = new ObjectInputStream(sChannel.socket().getInputStream());

						oos.writeObject("PUSH");

						// compute subset repository using mem entity factory
						this.transactionStrategy.begin();
						Repository.Op repository = this.repositoryDao.load();
						Repository.Op subsetRepository = repository.subset(this.parseFeatureVersionsString(deselectedFeatureVersionsString), repository.getMaxOrder(), this.memEntityFactory);
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
				Repository.Op subsetRepository = repository.subset(this.parseFeatureVersionsString(deselectedFeatureVersionsString), repository.getMaxOrder(), parentService.entityFactory);

				// merge into parent repository
				try {
					parentService.transactionStrategy.begin();

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
	 * Creates a repository at the current location if no repository already exists at the current location or any of its parents.
	 *
	 * @return True if the repository was created, false otherwise.
	 */
	public synchronized boolean init() {
		if (this.isInitialized())
			throw new EccoException("Service must not be initialized for init operation.");

		if (this.repositoryDirectoryExists())
			throw new EccoException("Repository already exists at this location.");

		try {
			if (!this.repositoryDirectoryExists())
				Files.createDirectory(this.repositoryDir);

			this.open();

			// TODO: do some initialization in backend like generating root object, etc.?

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
		Path configFile = this.baseDir.resolve(CONFIG_FILE_NAME);
		try {
			String configurationString = "";
			if (Files.exists(configFile))
				configurationString = new String(Files.readAllBytes(configFile)).trim();
			return this.commit(configurationString);
		} catch (IOException e) {
			throw new EccoException("Error during commit: '.config' file existed but could not be read.", e);
		}
	}

	/**
	 * Commits the files in the base directory using the given configuration string.
	 *
	 * @param configurationString The configuration string.
	 * @return The resulting commit object.
	 */
	public synchronized Commit commit(String configurationString) {
		return this.commit(this.parseConfigurationString(configurationString));
	}

	/**
	 * Commits the files in the base directory as the given configuration and returns the resulting commit object, or null in case of an error.
	 *
	 * @param configuration The configuration to be commited.
	 * @return The resulting commit object or null in case of an error.
	 */
	public synchronized Commit commit(Configuration configuration) {
		this.checkInitialized();

		checkNotNull(configuration);

		try {
			this.transactionStrategy.begin();

			Set<Node.Op> nodes = this.reader.read(this.baseDir, new Path[]{Paths.get("")});
			Repository.Op repository = this.repositoryDao.load();
			Commit commit = repository.extract(configuration, nodes);
			this.repositoryDao.store(repository);

			this.transactionStrategy.end();

			return commit;
		} catch (Exception e) {
			this.transactionStrategy.rollback();

			throw new EccoException("Error during commit.", e);
		}
	}


	// CHECKOUT ////////////////////////////////////////////////////////////////////////////////////////////////////////

	/**
	 * Checks out the implementation of the configuration (given as configuration string) into the base directory.
	 *
	 * @param configurationString The configuration string representing the configuration that shall be checked out.
	 */
	public synchronized Checkout checkout(String configurationString) {
		return this.checkout(this.parseConfigurationString(configurationString));
	}

	/**
	 * Checks out the implementation of the given configuration into the base directory.
	 *
	 * @param configuration The configuration to be checked out.
	 */
	public synchronized Checkout checkout(Configuration configuration) {
		this.checkInitialized();

		checkNotNull(configuration);


		Repository.Op repository = this.repositoryDao.load();
		Checkout checkout = repository.compose(configuration);


		for (Association selectedAssociation : checkout.getSelectedAssociations()) {
			this.fireAssociationSelectedEvent(selectedAssociation);
		}

		// write artifacts to files
		Set<Node> nodes = new HashSet<>(checkout.getNode().getChildren());
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

				for (at.jku.isse.ecco.module.Module m : checkout.getMissing()) {
					sb.append("MISSING: " + m + System.lineSeparator());
				}
				for (at.jku.isse.ecco.module.Module m : checkout.getSurplus()) {
					sb.append("SURPLUS: " + m + System.lineSeparator());
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
					sb.append("ORDER: " + pathList.stream().collect(Collectors.joining()) + System.lineSeparator());
				}
				for (Association association : checkout.getUnresolvedAssociations()) {
					sb.append("UNRESOLVED: " + association + System.lineSeparator());
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


	// OTHERS //////////////////////////////////////////////////////////////////////////////////////////////////////////

	/**
	 * Gets the list of loaded artifact plugins.
	 *
	 * @return The list of artifact plugins.
	 */
	public Collection<ArtifactPlugin> getArtifactPlugins() {
		return new ArrayList<>(this.artifactPlugins);
	}

	/**
	 * Get the injector that can be used to retreive arbitrary artifact readers, writers, viewers, etc.
	 * This is a lower level functionality that should not be used if not really necessary.
	 *
	 * @return The injector object.
	 */
	public Injector getInjector() {
		return this.injector;
	}


}
