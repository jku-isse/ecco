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
import at.jku.isse.ecco.listener.RepositoryListener;
import at.jku.isse.ecco.plugin.CoreModule;
import at.jku.isse.ecco.plugin.artifact.*;
import at.jku.isse.ecco.plugin.data.DataPlugin;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.repository.RepositoryOperand;
import at.jku.isse.ecco.tree.Node;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.name.Names;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A service class that gives access to high level operations like init, fork, pull, push, etc.
 */
public class EccoService {

	protected static final Logger LOGGER = LoggerFactory.getLogger(EccoService.class);

	public static final String ECCO_PROPERTIES_FILE = "ecco.properties";
	public static final String ECCO_PROPERTIES_DATA = "plugin.data";
	public static final String ECCO_PROPERTIES_ARTIFACT = "plugin.artifact";

	public static final Path REPOSITORY_DIR_NAME = Paths.get(".ecco");
	public static final Path DEFAULT_BASE_DIR = Paths.get("");
	public static final Path DEFAULT_REPOSITORY_DIR = DEFAULT_BASE_DIR.resolve(REPOSITORY_DIR_NAME);
	public static final Path CONFIG_FILE_NAME = Paths.get(".config");
	public static final Path WARNINGS_FILE_NAME = Paths.get(".warnings");

	private Path baseDir;
	private Path repositoryDir;


	public Path getBaseDir() {
		return this.baseDir;
	}

	public void setBaseDir(Path baseDir) {
		if (!this.baseDir.equals(baseDir)) {
			this.baseDir = baseDir;
			this.fireStatusChangedEvent();
		}
	}

	public Path getRepositoryDir() {
		return this.repositoryDir;
	}

	public void setRepositoryDir(Path repositoryDir) {
		if (this.initialized)
			throw new EccoException("The repository directory cannot be changed after the service has been initialized.");

		if (!this.repositoryDir.equals(repositoryDir)) {
			this.repositoryDir = repositoryDir;
			this.fireStatusChangedEvent();
		}
	}


	/**
	 * Creates the service and tries to detect an existing repository automatically using {@link #detectRepository(Path path) detectRepository}. If no existing repository was found the base directory (directory from which files are committed and checked out) and repository directory (directory at which the repository data is stored) are set to their defaults:
	 * <p>
	 * <br/>Base Directory (baseDir) Default: current directory
	 * <br/>Repository Directory (repoDir) Default: .ecco
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
	}


	private Collection<ArtifactPlugin> artifactPlugins;
	private Collection<DataPlugin> dataPlugins;
	private DataPlugin dataPlugin;

	private Injector injector;

	private boolean initialized = false;

	public boolean isInitialized() {
		return this.initialized;
	}

	@Inject
	private DispatchReader reader;
	@Inject
	private DispatchWriter writer;

	public ArtifactReader getReader() {
		return this.reader;
	}

	public ArtifactWriter getWriter() {
		return this.writer;
	}

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
//	@Inject
//	private AssociationDao associationDao;
//	@Inject
//	private FeatureDao featureDao;


	// # IGNORE ########################################################################################################

	private Set<String> defaultIgnorePatterns = new HashSet<>();
	private Set<String> customIgnorePatterns = new HashSet<>(); // TODO: set this

	public Set<String> getIgnorePatterns() {
		return this.settingsDao.loadIgnorePatterns();
	}

	public void addIgnorePattern(String ignorePattern) {
		try {
			this.transactionStrategy.begin();

			this.settingsDao.addIgnorePattern(ignorePattern);

			this.transactionStrategy.end();
		} catch (Exception e) {
			this.transactionStrategy.rollback();

			throw new EccoException("Error adding ignore pattern.", e);
		}
	}

	public void removeIgnorePattern(String ignorePattern) {
		try {
			this.transactionStrategy.begin();

			this.settingsDao.removeIgnorePattern(ignorePattern);

			this.transactionStrategy.end();
		} catch (Exception e) {
			this.transactionStrategy.rollback();

			throw new EccoException("Error removing ignore pattern.", e);
		}
	}


	// # LISTENERS #####################################################################################################

	private Collection<RepositoryListener> listeners = new ArrayList<>();

	public void addListener(RepositoryListener listener) {
		this.listeners.add(listener);
	}

	public void removeListener(RepositoryListener listener) {
		this.listeners.remove(listener);
	}

	private void fireStatusChangedEvent() {
		for (RepositoryListener listener : this.listeners) {
			listener.statusChangedEvent(this);
		}
	}

	private void fireCommitsChangedEvent(Commit commit) {
		for (RepositoryListener listener : this.listeners) {
			listener.commitsChangedEvent(this, commit);
		}
	}

	private void fireAssociationSelectedEvent(Association association) {
		for (RepositoryListener listener : this.listeners) {
			listener.associationSelectedEvent(this, association);
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

//	public boolean isManualMode() { // TODO: come up with a better way! this is a read only operation. it does not make sense to start a transaction here!
//		try {
//			this.transactionStrategy.begin();
//
//			boolean temp = this.settingsDao.loadManualMode();
//
//			this.transactionStrategy.end();
//
//			return temp;
//		} catch (Exception e) {
//			this.transactionStrategy.rollback();
//
//			throw new EccoException("Error changing settings.", e);
//		}
//	}
//
//	public void setManualMode(boolean manualMode) {
//		try {
//			this.transactionStrategy.begin();
//
//			if (!this.settingsDao.loadManualMode())
//				this.settingsDao.storeManualMode(manualMode);
//			else if (!manualMode) {
//				throw new EccoException("Once manual mode has been activated it cannot be turned off anymore.");
//			}
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
	 * Checks if a repository exists at the current path (the working directory from which ecco was started) or any of its parents.
	 *
	 * @return True if a repository was found, false otherwise.
	 */
	public boolean repositoryExists() {
		return this.repositoryExists(Paths.get(""));
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

	/**
	 * Creates a repository at the current location if no repository already exists at the current location or any of its parents.
	 *
	 * @return True if the repository was created, false otherwise.
	 */
	public boolean createRepository() throws EccoException {
		try {
			if (!this.repositoryDirectoryExists())
				Files.createDirectory(this.repositoryDir);

			this.init();

			// TODO: do some initialization in database like generating root object, etc.?

		} catch (IOException e) {
			throw new EccoException("Error while creating repository.", e);
		}

		return true;
	}

	/**
	 * Initializes the service.
	 */
	public void init() throws EccoException {
		if (this.initialized)
			return;

		if (!this.repositoryDirectoryExists()) {
			LOGGER.debug("Repository does not exist.");
			throw new EccoException("Repository does not exist.");
			//return;
		}
		if (this.isInitialized()) {
			LOGGER.debug("Repository is already initialized.");
			throw new EccoException("Repository is already initialized.");
			//return;
		}

		LOGGER.debug("BASE_DIR: " + this.baseDir);
		LOGGER.debug("REPOSITORY_DIR: " + this.repositoryDir);


		// load properties file
		InputStream inputStream = getClass().getClassLoader().getResourceAsStream(ECCO_PROPERTIES_FILE);
		Properties eccoProperties = new Properties();
		List<String> artifactPluginsList = null;
		if (inputStream != null) {
			try {
				eccoProperties.load(inputStream);
			} catch (IOException e) {
				throw new EccoException("Could not load properties from file '" + ECCO_PROPERTIES_FILE + "'.", e);
			}
		} else {
			throw new EccoException("Property file '" + ECCO_PROPERTIES_FILE + "' not found in the classpath.");
		}
		LOGGER.debug("PROPERTIES: " + eccoProperties);
		if (eccoProperties.getProperty(ECCO_PROPERTIES_DATA) == null) {
			throw new EccoException("No data plugin specified.");
		}
		if (eccoProperties.getProperty(ECCO_PROPERTIES_ARTIFACT) != null) {
			artifactPluginsList = Arrays.asList(eccoProperties.getProperty(ECCO_PROPERTIES_ARTIFACT).split(","));
			LOGGER.debug("Found optional property: " + ECCO_PROPERTIES_ARTIFACT);
		}


		Properties properties = new Properties();
		//properties.setProperty("module.data", "at.jku.isse.ecco.perst");
		//properties.setProperty("baseDir", this.baseDir.toString());
		properties.setProperty("repositoryDir", this.repositoryDir.toString());
		properties.setProperty("connectionString", this.repositoryDir.resolve("ecco.db").toString());
		properties.setProperty("clientConnectionString", this.repositoryDir.resolve("client.db").toString());
		properties.setProperty("serverConnectionString", this.repositoryDir.resolve("server.db").toString());

		// create modules
		final Module settingsModule = new AbstractModule() {
			@Override
			protected void configure() {
				bind(String.class).annotatedWith(Names.named("repositoryDir")).toInstance(properties.getProperty("repositoryDir"));

				bind(String.class).annotatedWith(Names.named("connectionString")).toInstance(properties.getProperty("connectionString"));
				bind(String.class).annotatedWith(Names.named("clientConnectionString")).toInstance(properties.getProperty("clientConnectionString"));
				bind(String.class).annotatedWith(Names.named("serverConnectionString")).toInstance(properties.getProperty("serverConnectionString"));
			}
		};
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
			if (dataPlugin.getPluginId().equals(eccoProperties.get(ECCO_PROPERTIES_DATA))) {
				dataModules.add(dataPlugin.getModule());
				this.dataPlugin = dataPlugin;
			}
			allDataModules.add(dataPlugin.getModule());
		}
		LOGGER.debug("DATA PLUGINS: " + dataModules.toString());
		LOGGER.debug("ALL DATA PLUGINS: " + allDataModules.toString());
		// put them together
		List<Module> modules = new ArrayList<>();
		modules.addAll(Arrays.asList(new CoreModule(), settingsModule));
		modules.addAll(artifactModules);
		modules.addAll(dataModules);

		// create injector
		Injector injector = Guice.createInjector(modules);

		this.injector = injector;

		injector.injectMembers(this);

		this.transactionStrategy.open();

		this.repositoryDao.init();
		this.settingsDao.init();
		this.commitDao.init();
//			this.associationDao.init();
//			this.featureDao.init();

//			this.reader.setIgnoredFiles(this.ignoredFiles);
		this.reader.getIgnorePatterns().clear();
		this.reader.getIgnorePatterns().addAll(this.customIgnorePatterns);
		this.reader.getIgnorePatterns().addAll(this.defaultIgnorePatterns);

		this.initialized = true;

		this.fireStatusChangedEvent();

		LOGGER.debug("Repository initialized.");
	}

	/**
	 * Properly shuts down the service.
	 */
	public void close() throws EccoException {
		if (!this.initialized)
			return;

		this.initialized = false;

		this.repositoryDao.close();
		this.settingsDao.close();
		this.commitDao.close();
//		this.associationDao.close();
//		this.featureDao.close();

		this.transactionStrategy.close();
	}


	// # UTILS #########################################################################################################

	public void addRemote(String name, String address) {
		URI uri = URI.create(address);

		try {
			this.transactionStrategy.begin();

			Remote.Type type;
			if (uri.getScheme() == null || uri.getScheme().equals("file")) {
				type = Remote.Type.LOCAL;
			} else {
				type = Remote.Type.REMOTE;
			}
			Remote remote = this.entityFactory.createRemote(name, address, type);

			this.settingsDao.storeRemote(remote);

			this.transactionStrategy.end();
		} catch (Exception e) {
			this.transactionStrategy.rollback();

			throw new EccoException("Error adding remote.", e);
		}
	}

	public void addRemote(String name, String address, String typeString) {
		Remote.Type type = Remote.Type.valueOf(typeString);
		Remote remote = this.entityFactory.createRemote(name, address, type);

		try {
			this.transactionStrategy.begin();

			this.settingsDao.storeRemote(remote);

			this.transactionStrategy.end();
		} catch (Exception e) {
			this.transactionStrategy.rollback();

			throw new EccoException("Error adding remote.", e);
		}
	}

	public void removeRemote(String name) {
		try {
			this.transactionStrategy.begin();

			this.settingsDao.removeRemote(name);

			this.transactionStrategy.end();
		} catch (Exception e) {
			this.transactionStrategy.rollback();

			throw new EccoException("Error removing remote.", e);
		}
	}

	public Remote getRemote(String name) {
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

	public Collection<Remote> getRemotes() {
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


//	/**
//	 * TODO: i don't like this solution. try to find a better one.
//	 */
//	public void updateAssociation(Association association) {
//		try {
//			this.transactionStrategy.begin();
//
//			this.associationDao.save(association);
//
//			this.transactionStrategy.end();
//		} catch (Exception e) {
//			this.transactionStrategy.rollback();
//
//			throw new EccoException("Error updating association.", e);
//		}
//	}


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

			RepositoryOperand repository = this.repositoryDao.load();

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
							feature = repository.addFeature(featureName);
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

					configuration.addFeatureInstance(this.entityFactory.createFeatureInstance(feature, featureVersion, featureSign));
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
							feature = repository.addFeature(featureName);
						} else if (features.size() == 1) {
							feature = features.iterator().next();
						} else {
							throw new EccoException("Feature name is not unique. Use feature id instead.");
						}
					}

					FeatureVersion featureVersion = feature.createNewVersion();
					newFeatureVersions.add(featureVersion);

					boolean featureSign = !(featureInstanceString.startsWith("!") || featureInstanceString.startsWith("-"));

					configuration.addFeatureInstance(this.entityFactory.createFeatureInstance(feature, featureVersion, featureSign));
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
							feature = repository.addFeature(featureName, "");
						} else if (features.size() == 1) {
							feature = features.iterator().next();
						} else {
							throw new EccoException("Feature name is not unique. Use feature id instead.");
						}
					}

					FeatureVersion featureVersion = feature.getLatestVersion();
					if (featureVersion == null) {
						featureVersion = feature.createNewVersion();
						newFeatureVersions.add(featureVersion);
					}

					boolean featureSign = !(featureInstanceString.startsWith("!") || featureInstanceString.startsWith("-"));

					configuration.addFeatureInstance(this.entityFactory.createFeatureInstance(feature, featureVersion, featureSign));
				}
			}

			// update existing associations with new (features and) feature versions. NOTE: update with negative features is not necessary if the configurations contain also all the negative features!
			Collection<? extends Association> associations = repository.getAssociations();
			for (Association association : associations) {
				for (FeatureVersion newFeatureVersion : newFeatureVersions) {
					association.getPresenceCondition().addFeatureVersion(newFeatureVersion);
					association.getPresenceCondition().addFeatureInstance(this.entityFactory.createFeatureInstance(newFeatureVersion.getFeature(), newFeatureVersion, false), repository.getMaxOrder());
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


//	protected Configuration parseConfigurationString(String configurationString) {
//		//if (configurationString == null || configurationString.isEmpty())
//		if (configurationString == null)
//			throw new EccoException("No configuration string provided.");
//
//		if (!configurationString.matches(Configuration.CONFIGURATION_STRING_REGULAR_EXPRESSION))
//			throw new EccoException("Invalid configuration string provided.");
//
//		try {
//			this.transactionStrategy.begin();
//
//			Configuration configuration = this.entityFactory.createConfiguration();
//
//			if (configurationString.isEmpty()) {
//				this.transactionStrategy.end();
//				return configuration;
//			}
//
//			Set<FeatureVersion> newFeatureVersions = new HashSet<>();
//
//			String[] featureInstanceStrings = configurationString.split(",");
//			for (String featureInstanceString : featureInstanceStrings) {
//				featureInstanceString = featureInstanceString.trim();
//				if (featureInstanceString.contains(".")) { // use specified feature version
//					String[] pair = featureInstanceString.split("\\.");
//					String featureName = pair[0];
//					if (featureName.startsWith("!") || featureName.startsWith("-") || featureName.startsWith("+"))
//						featureName = featureName.substring(1);
//					String id = pair[1];
//					boolean featureSign = !(pair[0].startsWith("!") || pair[0].startsWith("-"));
//
//
//					Feature feature = this.featureDao.load(featureName);
//					if (feature == null) {
//						feature = this.entityFactory.createFeature(featureName);
//					}
//					FeatureVersion featureVersion = feature.getVersion(id);
//					if (featureVersion == null) {
//						featureVersion = feature.addVersion(id);
//						newFeatureVersions.add(featureVersion);
//					}
//					this.featureDao.save(feature); // save repo feature now containing new version
//
//					configuration.addFeatureInstance(this.entityFactory.createFeatureInstance(feature, featureVersion, featureSign));
//				} else if (featureInstanceString.endsWith("'")) { // create new feature version for feature
//					String featureName = featureInstanceString.substring(0, featureInstanceString.length() - 1);
//					if (featureName.startsWith("!") || featureName.startsWith("-") || featureName.startsWith("+"))
//						featureName = featureName.substring(1);
//
//					Feature feature = this.featureDao.load(featureName);
//					if (feature == null) {
//						feature = this.entityFactory.createFeature(featureName);
//					}
//					FeatureVersion featureVersion = feature.createNewVersion();
//					newFeatureVersions.add(featureVersion);
//					this.featureDao.save(feature); // save repo feature now containing new version
//
//					boolean featureSign = !(featureInstanceString.startsWith("!") || featureInstanceString.startsWith("-"));
//
//					configuration.addFeatureInstance(this.entityFactory.createFeatureInstance(feature, featureVersion, featureSign));
//				} else { // use most recent feature version of feature (or create a new one if none existed so far)
//					String featureName = featureInstanceString;
//					if (featureName.startsWith("!") || featureName.startsWith("-") || featureName.startsWith("+"))
//						featureName = featureName.substring(1);
//
//					Feature feature = this.featureDao.load(featureName);
//					if (feature == null) {
//						feature = this.entityFactory.createFeature(featureName);
//					}
//					FeatureVersion featureVersion = feature.getLatestVersion();
//					if (featureVersion == null) {
//						featureVersion = feature.createNewVersion();
//						newFeatureVersions.add(featureVersion);
//					}
//					this.featureDao.save(feature); // save new feature including all its versions
//
//					boolean featureSign = !(featureInstanceString.startsWith("!") || featureInstanceString.startsWith("-"));
//
//					configuration.addFeatureInstance(this.entityFactory.createFeatureInstance(feature, featureVersion, featureSign));
//				}
//			}
//
//			// update existing associations with new (features and) feature versions. NOTE: update with negative features is not necessary if the configurations contain also all the negative features!
//			Collection<Association> associations = this.associationDao.loadAllAssociations();
//			for (Association association : associations) {
//				for (FeatureVersion newFeatureVersion : newFeatureVersions) {
//					association.getPresenceCondition().addFeatureVersion(newFeatureVersion);
//					association.getPresenceCondition().addFeatureInstance(this.entityFactory.createFeatureInstance(newFeatureVersion.getFeature(), newFeatureVersion, false), this.getMaxOrder());
//					this.associationDao.save(association);
//				}
//			}
//
//			this.transactionStrategy.end();
//
//			return configuration;
//
//		} catch (Exception e) {
//			this.transactionStrategy.rollback();
//
//			throw new EccoException("Error parsing configuration string: " + configurationString, e);
//		}
//	}

	protected Collection<FeatureVersion> parseFeatureVersionString(String featureVersionsString) {
		if (featureVersionsString == null)
			throw new EccoException("No feature versions string provided.");

		if (!featureVersionsString.matches("(((\\[[a-zA-Z0-9_-]+\\])|([a-zA-Z0-9_-]+))(\\.([a-zA-Z0-9_-])+)(\\s*,\\s*((\\[[a-zA-Z0-9_-]+\\])|([a-zA-Z0-9_-]+))(\\.([a-zA-Z0-9_-])+))*)?"))
			throw new EccoException("Invalid configuration string provided.");

		try {
			this.transactionStrategy.begin();

			Collection<FeatureVersion> featureVersions = new ArrayList<>();

			if (featureVersionsString.isEmpty()) {
				this.transactionStrategy.end();
				return featureVersions;
			}

			RepositoryOperand repository = this.repositoryDao.load();

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


	public void server(int port) {
		boolean shutdown = false;

		try (ServerSocketChannel ssChannel = ServerSocketChannel.open()) {
			ssChannel.configureBlocking(true);
			ssChannel.socket().bind(new InetSocketAddress(port));

			while (!shutdown) {
				try (SocketChannel sChannel = ssChannel.accept()) {
					ObjectOutputStream oos = new ObjectOutputStream(sChannel.socket().getOutputStream());
					ObjectInputStream ois = new ObjectInputStream(sChannel.socket().getInputStream());

					// determine if it is a push (receive data) or a pull (send data)
					String command = (String) ois.readObject();
					System.out.println("COMMAND: " + command);

					if (command.equals("PULL")) { // if pull, send data
						// retrieve deselection
						Collection<FeatureVersion> deselected = (Collection<FeatureVersion>) ois.readObject();

						// compute subset repository using mem entity factory
						this.transactionStrategy.begin();
						RepositoryOperand repository = this.repositoryDao.load();
						RepositoryOperand subsetRepository = repository.subset(deselected, repository.getMaxOrder(), this.entityFactory); // TODO: change entity factory to mem
						this.transactionStrategy.end();

						// send subset repository
						oos.writeObject(subsetRepository);
					} else if (command.equals("PUSH")) { // if push, receive data
						// retrieve repository
						RepositoryOperand subsetRepository = (RepositoryOperand) ois.readObject();

						// copy it using this entity factory
						RepositoryOperand copiedRepository = subsetRepository.copy(this.entityFactory);

						// merge into this repository
						this.transactionStrategy.begin();
						RepositoryOperand repository = this.repositoryDao.load();
						repository.merge(copiedRepository);
						this.repositoryDao.store(repository);
						this.transactionStrategy.end();
					}
				} catch (Exception e) {
					throw new EccoException("Error receiving request.", e);
				}
			}

		} catch (Exception e) {
			throw new EccoException("Error starting server.", e);
		}
	}


//	public void fork(URI uri) {
//		this.fork(new ArrayList<>(), uri);
//	}
//
//	public void fork(Collection<FeatureVersion> deselected, URI uri) {
//		if (uri.getScheme() == null || uri.getScheme().equals("file")) {
//			//if (uri.getScheme().equals("file")) {
//			Path remotePath = Paths.get(uri.getPath());
//			this.fork(deselected, remotePath);
//		} else if (uri.getScheme().equals("ecco")) {
//			try {
//				this.fork(deselected, uri.toURL());
//			} catch (MalformedURLException e) {
//				throw new EccoException("Error during remote fork.", e);
//			}
//		}
//	}

	public void fork(String hostname, int port) {
		this.fork("", hostname, port);
	}

	public void fork(String deselectedFeatureVersionsString, String hostname, int port) {
		if (this.isInitialized())
			throw new EccoException("ECCO Service must not be initialized for fork operation.");
		if (this.repositoryDirectoryExists())
			throw new EccoException("A repository already exists at the given location: " + this.repositoryDir);

		RepositoryOperand copiedRepository;
		try (SocketChannel sChannel = SocketChannel.open()) {
			sChannel.configureBlocking(true);
			if (sChannel.connect(new InetSocketAddress(hostname, port))) {
				ObjectOutputStream oos = new ObjectOutputStream(sChannel.socket().getOutputStream());
				ObjectInputStream ois = new ObjectInputStream(sChannel.socket().getInputStream());

				oos.writeObject("PULL");
				oos.writeObject(deselectedFeatureVersionsString);

				// retrieve remote repository
				RepositoryOperand subsetRepository = (RepositoryOperand) ois.readObject();

				// copy it using this entity factory
				copiedRepository = subsetRepository.copy(this.entityFactory);
			} else {
				throw new EccoException("Error connecting to remote: " + hostname + ":" + port);
			}
		} catch (Exception e) {
			throw new EccoException("Error during remote fork.", e);
		}

		try {
			this.createRepository();
			this.init();

			this.transactionStrategy.begin();

			// merge into this repository
			RepositoryOperand repository = this.repositoryDao.load();
			repository.merge(copiedRepository);
			this.repositoryDao.store(repository);

			// after fork add used remote as default origin remote
			Remote remote = this.entityFactory.createRemote("origin", hostname + ":" + Integer.toString(port), Remote.Type.REMOTE);
			this.settingsDao.storeRemote(remote);

			this.transactionStrategy.end();
		} catch (Exception e) {
			throw new EccoException("Error during remote fork.", e);
		}
	}

	public void fork(Path originRepositoryDir) {
		this.fork("", originRepositoryDir);
	}

	/**
	 * This operation clones/forks a complete repository, i.e. all of its features, artifacts and traces.
	 * It can only be executed on a not initialized repository.
	 * After the operation the repository is initialized and ready to use.
	 * The fork operation is like the init operation in the sense that it creates a new repository at a given location.
	 *
	 * @param originRepositoryDir The directory of the repository from which to fork.
	 */
	public void fork(String deselectedFeatureVersionsString, Path originRepositoryDir) {
		// check that this service has not yet been initialized and that no repository already exists,
		if (this.isInitialized())
			throw new EccoException("ECCO Service must not be initialized for fork operation.");
		if (this.repositoryDirectoryExists())
			throw new EccoException("A repository already exists at the given location: " + this.repositoryDir);

		// create another ecco service and init it on the parent repository directory.
		EccoService originService = new EccoService();
		originService.setRepositoryDir(originRepositoryDir);
		// create subset repository
		RepositoryOperand subsetOriginRepository;
		try {
			originService.init(); // TODO: init read only! add read only mode for that (also useful for other read only services on a repository such as a read only web interface REST API service).

			originService.transactionStrategy.begin();

			RepositoryOperand originRepository = originService.repositoryDao.load();
			subsetOriginRepository = originRepository.subset(this.parseFeatureVersionString(deselectedFeatureVersionsString), originRepository.getMaxOrder(), this.entityFactory);

			originService.transactionStrategy.end();
		} catch (Exception e) {
			originService.transactionStrategy.rollback();

			throw new EccoException("Error during fork.", e);
		} finally {
			// close parent repository
			originService.close();
		}

		try {
			this.createRepository();
			this.init();

			this.transactionStrategy.begin();

			// merge into this repository
			RepositoryOperand repository = this.repositoryDao.load();
			repository.merge(subsetOriginRepository);
			this.repositoryDao.store(repository);

			// after fork add used remote as default origin remote
			Remote remote = this.entityFactory.createRemote("origin", originRepositoryDir.toString(), Remote.Type.LOCAL);
			this.settingsDao.storeRemote(remote);

			this.transactionStrategy.end();
		} catch (Exception e) {
			this.transactionStrategy.rollback();

			throw new EccoException("Error during fork.", e);
		}
	}


	public void pull(String remoteName) {
		this.pull("", remoteName);
	}

	/**
	 * Pulls the changes from the parent repository to this repository.
	 */
	public void pull(String deselectedFeatureVersionsString, String remoteName) {
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
						ObjectOutputStream oos = new ObjectOutputStream(sChannel.socket().getOutputStream());
						ObjectInputStream ois = new ObjectInputStream(sChannel.socket().getInputStream());

						oos.writeObject("PULL");
						oos.writeObject(deselectedFeatureVersionsString);

						// retrieve remote repository
						RepositoryOperand subsetRepository = (RepositoryOperand) ois.readObject();

						// copy it using this entity factory
						RepositoryOperand copiedRepository = subsetRepository.copy(this.entityFactory);

						// merge into this repository
						RepositoryOperand repository = this.repositoryDao.load();
						repository.merge(copiedRepository);
						this.repositoryDao.store(repository);
					} else {
						throw new EccoException("Error connecting to remote: " + remote.getName() + ": " + pair[0] + ":" + pair[1]);
					}
				} catch (Exception e) {
					throw new EccoException("Error during remote pull.", e);
				}

			} else if (remote.getType() == Remote.Type.LOCAL) {
				// init this repository
				this.init();

				// open parent repository
				EccoService parentService = new EccoService();
				parentService.setRepositoryDir(Paths.get(remote.getAddress()));
				parentService.init(); // TODO: init read only! add read only mode for that (also useful for other read only services on a repository such as a read only web interface REST API service).

				// create subset repository
				RepositoryOperand subsetParentRepository;
				try {
					parentService.transactionStrategy.begin();

					RepositoryOperand parentRepository = parentService.repositoryDao.load();
					subsetParentRepository = parentRepository.subset(this.parseFeatureVersionString(deselectedFeatureVersionsString), parentRepository.getMaxOrder(), this.entityFactory);

					parentService.transactionStrategy.end();
				} catch (Exception e) {
					parentService.transactionStrategy.rollback();

					throw new EccoException("Error during pull.", e);
				}

				// close parent repository
				parentService.close();

				// merge into this repository
				RepositoryOperand repository = this.repositoryDao.load();
				repository.merge(subsetParentRepository);
				this.repositoryDao.store(repository);
			}

			this.transactionStrategy.end();
		} catch (Exception e) {
			this.transactionStrategy.rollback();

			throw new EccoException("Error during pull.", e);
		}
	}


	public void push(String remoteName) {
		this.push("", remoteName);
	}

	/**
	 * Pushes the changes from this repository to its parent repository.
	 */
	public void push(String deselectedFeatureVersionsString, String remoteName) {
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
						RepositoryOperand repository = this.repositoryDao.load();
						RepositoryOperand subsetRepository = repository.subset(this.parseFeatureVersionString(deselectedFeatureVersionsString), repository.getMaxOrder(), this.entityFactory); // TODO: change entity factory to mem
						this.transactionStrategy.end();

						// send subset repository
						oos.writeObject(subsetRepository);
					} else {
						throw new EccoException("Error connecting to remote: " + pair[0] + ":" + pair[1]);
					}
				} catch (Exception e) {
					throw new EccoException("Error during remote fork.", e);
				}

			} else if (remote.getType() == Remote.Type.LOCAL) {
				// init this repo
				this.init();

				// open parent repo
				EccoService parentService = new EccoService();
				parentService.setRepositoryDir(Paths.get(remote.getAddress()));
				parentService.init(); // TODO: init read only! add read only mode for that (also useful for other read only services on a repository such as a read only web interface REST API service).

				// create subset repository
				RepositoryOperand repository = this.repositoryDao.load();
				RepositoryOperand subsetRepository = repository.subset(this.parseFeatureVersionString(deselectedFeatureVersionsString), repository.getMaxOrder(), parentService.entityFactory);

				// merge into parent repository
				try {
					parentService.transactionStrategy.begin();

					RepositoryOperand parentRepository = parentService.repositoryDao.load();
					parentRepository.merge(subsetRepository);
					parentService.repositoryDao.store(parentRepository);

					parentService.transactionStrategy.end();
				} catch (Exception e) {
					parentService.transactionStrategy.rollback();

					throw new EccoException("Error during push.", e);
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

//	protected void merge(Collection<Feature> features, Collection<Association> associations) {
//		this.merge(new ArrayList<>(), features, associations);
//	}
//
//	protected void merge(Collection<FeatureVersion> deselected, Collection<Feature> features, Collection<Association> associations) {
//		/**
//		 * 1.) deselect feature versions that are not desired by providing a set (configuration?) of negative feature instances. <- INPUT
//		 * 1.1) add new feature versions/instances that have NO fixed value to "this" repository. those that do have a fixed value AND ARE NOT NEW should NOT be added to the featureVersionReplacementMap!
//		 * 2.) clone all associations.
//		 * 3.) remove (fixate) all negative feature instances in the presence conditions of the cloned associations.
//		 * 4.) remove cloned associations with empty PCs. merge cloned associations with equal PCs.
//		 * 5.) do ordinary full push/pull/fork.
//		 *
//		 * fork/push/pull() --- selection=emptyset ---> fork/push/pull(Set<FeatureInstance> selection) -> merge(selection, features, associations) -> merge(features, associations) -> commit
//		 */
//		try {
//			this.transactionStrategy.begin();
//
//			// step 1: add new features and versions to associations in this repository, excluding the deselected feature versions
//			Map<Feature, Feature> featureReplacementMap = new HashMap<>();
//			Map<FeatureVersion, FeatureVersion> featureVersionReplacementMap = new HashMap<>();
//			Collection<FeatureVersion> newChildFeatureVersions = new ArrayList<>();
//			for (Feature parentFeature : features) {
//				Feature childFeature = this.featureDao.load(parentFeature.getName()); // TODO: what to do when parent and child feature have different description? e.g. because it was changed on one of the two before the pull.
//				if (childFeature == null) {
//					childFeature = this.entityFactory.createFeature(parentFeature.getName(), parentFeature.getDescription());
//				}
//
//				for (FeatureVersion parentFeatureVersion : parentFeature.getVersions()) {
//					if (!deselected.contains(parentFeatureVersion)) {
//						FeatureVersion childFeatureVersion = childFeature.getVersion(parentFeatureVersion.getId());
//						if (childFeatureVersion == null) {
//							childFeatureVersion = childFeature.addVersion(parentFeatureVersion.getId());
//							childFeatureVersion.setDescription(parentFeatureVersion.getDescription());
//							newChildFeatureVersions.add(childFeatureVersion);
//						}
//						featureVersionReplacementMap.put(parentFeatureVersion, childFeatureVersion);
//					}
//				}
//
//				if (!childFeature.getVersions().isEmpty()) {
//					featureReplacementMap.put(parentFeature, childFeature);
//					this.featureDao.save(childFeature);
//				}
//			}
//			for (Association childAssociation : this.associationDao.loadAllAssociations()) {
//				for (FeatureVersion newChildFeatureVersion : newChildFeatureVersions) {
//					childAssociation.getPresenceCondition().addFeatureVersion(newChildFeatureVersion);
//					childAssociation.getPresenceCondition().addFeatureInstance(this.entityFactory.createFeatureInstance(newChildFeatureVersion.getFeature(), newChildFeatureVersion, false), this.getMaxOrder());
//				}
//			}
//
//
//			// step 2: copy input associations, but exclude modules or module features that evaluate to false given the deselected feature versions
//			Collection<Association> copiedParentAssociations = new ArrayList<>();
//			for (Association parentAssociation : associations) {
//				Association copiedParentAssociation = this.entityFactory.createAssociation();
////				copiedParentAssociation.setId(parentAssociation.getId());
////				copiedParentAssociation.setName(parentAssociation.getName());
//
//				PresenceCondition parentPresenceCondition = parentAssociation.getPresenceCondition();
//
//
//				// copy presence condition
//				PresenceCondition childPresenceCondition = this.entityFactory.createPresenceCondition();
//				copiedParentAssociation.setPresenceCondition(childPresenceCondition);
//
//				Set<at.jku.isse.ecco.module.Module>[][] moduleSetPairs = new Set[][]{{parentPresenceCondition.getMinModules(), childPresenceCondition.getMinModules()}, {parentPresenceCondition.getMaxModules(), childPresenceCondition.getMaxModules()}, {parentPresenceCondition.getNotModules(), childPresenceCondition.getNotModules()}, {parentPresenceCondition.getAllModules(), childPresenceCondition.getAllModules()}};
//
//				for (Set<at.jku.isse.ecco.module.Module>[] moduleSetPair : moduleSetPairs) {
//					Set<at.jku.isse.ecco.module.Module> parentModuleSet = moduleSetPair[0];
//					Set<at.jku.isse.ecco.module.Module> childModuleSet = moduleSetPair[1];
//
//					for (at.jku.isse.ecco.module.Module fromModule : parentModuleSet) {
//						at.jku.isse.ecco.module.Module toModule = this.entityFactory.createModule();
//						for (ModuleFeature fromModuleFeature : fromModule) {
//
//							// feature
//							Feature fromFeature = fromModuleFeature.getFeature();
//							Feature toFeature;
//							if (featureReplacementMap.containsKey(fromFeature)) {
//								toFeature = featureReplacementMap.get(fromFeature);
//							} else {
//								toFeature = fromFeature;
//
//								throw new EccoException("This should not happen!");
//							}
//
//
//							// ------------
//
//							// if a deselected feature version is contained in module feature:
//							//  if module feature is positive: remove / do not add feature version from module feature
//							//   if module feature is empty: remove it / do not add it
//							//  else if module feature is negative: remove module feature from module
//							//   if module is empty (should not happen?) then leave it! module is always TRUE (again: should not happen, because at least one positive module feature should be in every module, but that might currently not be the case)
//
//							ModuleFeature toModuleFeature = this.entityFactory.createModuleFeature(toFeature, fromModuleFeature.getSign());
//							boolean addToModule = true;
//							for (FeatureVersion fromFeatureVersion : fromModuleFeature) {
//								if (deselected.contains(fromFeatureVersion)) { // if a deselected feature version is contained in module feature
//
//									if (fromModuleFeature.getSign()) {  // if module feature is positive
//										// do not add feature version to module feature
//									} else {
//										// do not add module feature to module because it is always true
//										addToModule = false;
//										break;
//									}
//
//								} else { // ordinary copy
//									FeatureVersion toFeatureVersion;
//									if (featureVersionReplacementMap.containsKey(fromFeatureVersion)) {
//										toFeatureVersion = featureVersionReplacementMap.get(fromFeatureVersion);
//									} else {
//										toFeatureVersion = fromFeatureVersion;
//
//										throw new EccoException("This should not happen!");
//									}
//									toModuleFeature.add(toFeatureVersion);
//								}
//							}
//							if (!toModuleFeature.isEmpty() && addToModule) { // if module feature is empty: do not add it
//								toModule.add(toModuleFeature);
//							}
//
//							// ------------
//
//
////							// feature versions
////							ModuleFeature toModuleFeature = this.entityFactory.createModuleFeature(toFeature, fromModuleFeature.getSign());
////							for (FeatureVersion fromFeatureVersion : fromModuleFeature) {
////								FeatureVersion toFeatureVersion;
////								if (featureVersionReplacementMap.containsKey(fromFeatureVersion)) {
////									toFeatureVersion = featureVersionReplacementMap.get(fromFeatureVersion);
////								} else {
////									toFeatureVersion = fromFeatureVersion;
////
////									throw new EccoException("This should not happen!");
////								}
////								toModuleFeature.add(toFeatureVersion);
////							}
////
////							toModule.add(toModuleFeature);
//
//
//						}
//						childModuleSet.add(toModule);
//					}
//				}
//
//
//				// copy artifact tree
//				RootNode copiedRootNode = this.entityFactory.createRootNode();
//				copiedParentAssociation.setRootNode(copiedRootNode);
//				// clone tree
//				for (Node parentChildNode : parentAssociation.getRootNode().getChildren()) {
//					Node copiedChildNode = EccoUtil.deepCopyTree(parentChildNode, this.entityFactory);
//					copiedRootNode.addChild(copiedChildNode);
//					copiedChildNode.setParent(copiedRootNode);
//				}
//				Trees.checkConsistency(copiedRootNode);
//
//
//				copiedParentAssociations.add(copiedParentAssociation);
//			}
//
//
//			// step 2.1: remove (fixate) all provided (selected) feature instances in the presence conditions of the cloned associations.
//			// this is done in the previous step 2
//
//
//			// step 2.2: remove cloned associations with empty PCs.
//			Iterator<Association> associationIterator = copiedParentAssociations.iterator();
//			while (associationIterator.hasNext()) {
//				Association association = associationIterator.next();
//				if (association.getPresenceCondition().isEmpty())
//					associationIterator.remove();
//			}
//
//
//			// step 2.3: compute dependency graph for selected associations and check if there are any unresolved dependencies
//			DependencyGraph dg = new DependencyGraph(copiedParentAssociations, DependencyGraph.ReferencesResolveMode.LEAVE_REFERENCES_UNRESOLVED); // we do not trim unresolved references. instead we abort.
//			if (!dg.getUnresolvedDependencies().isEmpty()) {
//				throw new EccoException("Unresolved dependencies in selection.");
//			}
//
//
//			// step 2.4: merge cloned associations with equal PCs.
//			Associations.consolidate(copiedParentAssociations);
//
//
//			// step 2.5: trim sequence graphs to only contain artifacts from the selected associations
//			EccoUtil.trimSequenceGraph(copiedParentAssociations);
//
//
//			// #### this is where the transition from one repo to the other should happen! ^A  Bv
//
//
//			// step 3: add new features in this repo to copied input associations
//			Collection<FeatureVersion> newParentFeatureVersions = new ArrayList<>();
//			for (Feature childFeature : this.featureDao.loadAllFeatures()) {
//				Feature parentFeature = null;
//				for (Feature tempParentFeature : features) {
//					if (tempParentFeature.equals(childFeature)) {
//						parentFeature = tempParentFeature;
//						break;
//					}
//				}
//				if (parentFeature == null) {
//					// add all its versions to list
//					for (FeatureVersion childFeatureVersion : childFeature.getVersions()) {
//						newParentFeatureVersions.add(childFeatureVersion);
//					}
//				} else {
//					// compare versions and add new ones to list
//					for (FeatureVersion childFeatureVersion : childFeature.getVersions()) {
//						FeatureVersion parentFeatureVersion = parentFeature.getVersion(childFeatureVersion.getId());
//						if (parentFeatureVersion == null) {
//							newParentFeatureVersions.add(childFeatureVersion);
//						}
//					}
//				}
//			}
//			for (Association copiedAssociation : copiedParentAssociations) {
//				for (FeatureVersion newParentFeatureVersion : newParentFeatureVersions) {
//					copiedAssociation.getPresenceCondition().addFeatureVersion(newParentFeatureVersion);
//					copiedAssociation.getPresenceCondition().addFeatureInstance(this.entityFactory.createFeatureInstance(newParentFeatureVersion.getFeature(), newParentFeatureVersion, false), this.getMaxOrder());
//				}
//			}
//
//
//			// step 4: commit copied associations to this repository
//			this.commit(copiedParentAssociations);
//
//			this.transactionStrategy.end();
//		} catch (Exception e) {
//			this.transactionStrategy.rollback();
//
//			throw new EccoException("Error during merge.", e);
//		}
//	}


	// COMMIT //////////////////////////////////////////////////////////////////////////////////////////////////////////

	/**
	 * Commits the files in the base directory using the configuration string given in file {@link #CONFIG_FILE_NAME} or an empty configuration string if the file does not exist.
	 *
	 * @return The resulting commit object.
	 * @throws EccoException When the configuration file does not exist or cannot be read.
	 */
	public Commit commit() throws EccoException {
		Path configFile = this.baseDir.resolve(CONFIG_FILE_NAME);
		try {
			String configurationString = "";
			if (Files.exists(configFile))
				configurationString = new String(Files.readAllBytes(configFile));
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
	 * @throws EccoException
	 */
	public Commit commit(String configurationString) throws EccoException {
		return this.commit(this.parseConfigurationString(configurationString));
	}

	/**
	 * Commits the files in the base directory as the given configuration and returns the resulting commit object, or null in case of an error.
	 *
	 * @param configuration The configuration to be commited.
	 * @return The resulting commit object or null in case of an error.
	 */
	public Commit commit(Configuration configuration) throws EccoException {
		try {
			this.transactionStrategy.begin();

			Set<Node> nodes = this.reader.read(this.baseDir, new Path[]{Paths.get("")});
			RepositoryOperand repository = this.repositoryDao.load();
			Commit commit = repository.extract(configuration, nodes);
			this.repositoryDao.store(repository);

			this.transactionStrategy.end();

			return commit;
		} catch (Exception e) {
			this.transactionStrategy.rollback();

			throw new EccoException("Error during commit.", e);
		}
	}

//	/**
//	 * Commits a set of artifact nodes as a given configuration to the repository and returns the resulting commit object, or null in case of an error.
//	 *
//	 * @param configuration The configuration that is committed.
//	 * @param nodes         The artifact nodes that implement the given configuration.
//	 * @return The resulting commit object or null in case of an error.
//	 */
//	protected Commit commit(Configuration configuration, Set<Node> nodes) throws EccoException {
//		try {
//			this.transactionStrategy.begin();
//
//			// add new features and versions from configuration to this repository
//			Collection<FeatureVersion> newFeatureVersions = new ArrayList<>();
//			Configuration newConfiguration = this.entityFactory.createConfiguration();
//			for (FeatureInstance featureInstance : configuration.getFeatureInstances()) {
//				Feature feature = featureInstance.getFeature();
//				Feature repoFeature = this.featureDao.load(featureInstance.getFeature().getName());
//				if (repoFeature == null) {
//					repoFeature = this.entityFactory.createFeature(feature.getName(), feature.getDescription());
//				}
//				FeatureVersion featureVersion = featureInstance.getFeatureVersion();
//				FeatureVersion repoFeatureVersion = repoFeature.getVersion(featureVersion.getId());
//				if (repoFeatureVersion == null) {
//					repoFeatureVersion = repoFeature.addVersion(featureVersion.getId());
//					repoFeatureVersion.setDescription(featureVersion.getDescription());
//					newFeatureVersions.add(repoFeatureVersion);
//				}
//				FeatureInstance newFeatureInstance = this.entityFactory.createFeatureInstance(repoFeature, repoFeatureVersion, featureInstance.getSign());
//				newConfiguration.addFeatureInstance(newFeatureInstance);
//				this.featureDao.save(repoFeature);
//			}
//			for (Association childAssociation : this.associationDao.loadAllAssociations()) {
//				for (FeatureVersion newFeatureVersion : newFeatureVersions) {
//					childAssociation.getPresenceCondition().addFeatureVersion(newFeatureVersion);
//					childAssociation.getPresenceCondition().addFeatureInstance(this.entityFactory.createFeatureInstance(newFeatureVersion.getFeature(), newFeatureVersion, false), this.getMaxOrder());
//				}
//			}
////			Configuration newConfiguration = configuration;
//
//			// create presence condition
//			PresenceCondition presenceCondition = this.entityFactory.createPresenceCondition(newConfiguration, this.getMaxOrder());
//
//			// create association
//			Association association = this.entityFactory.createAssociation(presenceCondition, nodes);
//
//
//			// SIMPLE MODULES
//			association.getModules().addAll(configuration.computeModules(this.getMaxOrder())); // TODO: this is obsolete. remove at some point.
//
//
//			// commit association
//			Commit commit = this.commit(association);
//			commit.setConfiguration(configuration);
//
//			commit = this.commitDao.save(commit);
//
//
//			if (!this.isManualMode()) { // TODO: bring this up to date.
//				// PRESENCE TABLE
//				for (Association commitAssociation : commit.getAssociations()) {
//					for (FeatureInstance featureInstance : configuration.getFeatureInstances()) {
//						// find module feature in the map that has same feature and sign
//						ModuleFeature moduleFeature = null;
//						int count = 0;
//						Iterator<Map.Entry<ModuleFeature, Integer>> iterator = commitAssociation.getPresenceTable().entrySet().iterator();
//						while (iterator.hasNext()) {
//							Map.Entry<ModuleFeature, Integer> entry = iterator.next();
//
//							if (entry.getKey().getSign() == featureInstance.getSign() && entry.getKey().getFeature().equals(featureInstance.getFeature())) {
//								moduleFeature = entry.getKey();
//								count = entry.getValue();
//
//								iterator.remove();
//								break;
//							}
//						}
//						if (moduleFeature == null) {
//							moduleFeature = this.entityFactory.createModuleFeature(featureInstance.getFeature(), featureInstance.getSign());
//						}
//						moduleFeature.add(featureInstance.getFeatureVersion());
//						count++;
//						commitAssociation.getPresenceTable().put(moduleFeature, count);
//					}
//					commitAssociation.incPresenceCount();
//					//this.updateAssociation(commitAssociation);
//				}
//			}
//
//
//			this.transactionStrategy.end();
//
//			return commit;
//		} catch (Exception e) {
//			this.transactionStrategy.rollback();
//
//			throw new EccoException("Error during commit.", e);
//		}
//	}
//
//	/**
//	 * When an association is committed directly then the corresponding configuration must be added manually first!
//	 *
//	 * @param association The association to be committed.
//	 * @return The resulting commit object or null in case of an error.
//	 */
//	protected Commit commit(Association association) throws EccoException {
//		checkNotNull(association);
//
//		List<Association> associations = new ArrayList<>(1);
//		associations.add(association);
//		return this.commit(associations);
//	}
//
//	/**
//	 * When associations are committed directly then the corresponding configuration must be added manually first!
//	 *
//	 * @param inputAs The collection of associations to be committed.
//	 * @return The resulting commit object or null in case of an error.
//	 */
//	protected Commit commit(Collection<Association> inputAs) throws EccoException {
//		synchronized (this) {
//			checkNotNull(inputAs);
//
//			LOGGER.debug("COMMIT");
//
//
//			Commit persistedCommit = null;
//
//			try {
//				this.transactionStrategy.begin();
//
//				Commit commit = this.entityFactory.createCommit();
//
//				List<Association> originalAssociations = this.associationDao.loadAllAssociations();
//				List<Association> newAssociations = new ArrayList<>();
//				List<Association> removedAssociations = new ArrayList<>();
//
//				Association emptyAssociation = null;
//				// find initial empty association if there is any
//				for (Association origA : originalAssociations) {
//					if (origA.getRootNode().getChildren().isEmpty()) {
//						emptyAssociation = origA;
//						break;
//					}
//				}
//
//				// process each new association individually
//				for (Association inputA : inputAs) {
//					List<Association> toAdd = new ArrayList<>();
//					List<Association> toRemove = new ArrayList<>();
//
//					// slice new association with every original association
//					for (Association origA : originalAssociations) {
//
//						// ASSOCIATION
//						// slice the associations. the order matters here! the "left" association's featuers and artifacts are maintained. the "right" association's features and artifacts are replaced by the "left" association's.
//						//Association intA = origA.slice(inputA);
//						Association intA = this.entityFactory.createAssociation();
//
//
//						// SIMPLE MODULES
//						intA.getModules().addAll(origA.getModules());
//						if (!this.isManualMode()) {
//							intA.getModules().retainAll(inputA.getModules());
//							origA.getModules().removeAll(intA.getModules());
//						}
//						inputA.getModules().removeAll(origA.getModules());
//
//
//						// PRESENCE CONDITION
//						if (!this.isManualMode()) {
//							//intA.setPresenceCondition(FeatureUtil.slice(origA.getPresenceCondition(), inputA.getPresenceCondition()));
//							intA.setPresenceCondition(origA.getPresenceCondition().slice(inputA.getPresenceCondition()));
//						} else {
//							// clone original presence condition for intersection association and leave it unchanged.
//							intA.setPresenceCondition(this.entityFactory.createPresenceCondition(origA.getPresenceCondition()));
//						}
//
//
//						// ARTIFACT TREE
//						//intA.setRootNode((origA.getRootNode().slice(inputA.getRootNode())));
//						intA.setRootNode((RootNode) Trees.slice(origA.getRootNode(), inputA.getRootNode()));
//
//
//						// INTERSECTION
//						if (!intA.getRootNode().getChildren().isEmpty()) { // if the intersection association has artifacts store it
//							// set parents for intersection association (and child for parents)
//							intA.addParent(origA);
//							intA.addParent(inputA);
//							intA.setName(origA.getId() + " INT " + inputA.getId());
//
//							toAdd.add(intA);
//
//							commit.addUnmodified(intA);
//
//							Trees.checkConsistency(intA.getRootNode());
//						} else if (!intA.getPresenceCondition().isEmpty()) { // if it has no artifacts but a not empty presence condition merge it with other empty associations
//							if (emptyAssociation == null) {
//								emptyAssociation = intA;
//								emptyAssociation.setName("EMPTY");
//								toAdd.add(intA);
//							} else if (emptyAssociation != intA) {
//								emptyAssociation.getPresenceCondition().merge(intA.getPresenceCondition());
//							}
//						}
//
//						// ORIGINAL
//						if (origA.getRootNode().getChildren().isEmpty()) { // if the original association has no artifacts left
//							if (!origA.getPresenceCondition().isEmpty()) { // if presence condition is not empty merge it
//								if (emptyAssociation == null) {
//									emptyAssociation = origA;
//									emptyAssociation.setName("EMPTY");
//								} else if (emptyAssociation != origA) {
//									emptyAssociation.getPresenceCondition().merge(origA.getPresenceCondition());
//									toRemove.add(origA);
//								}
//							} else {
//								toRemove.add(origA);
//							}
//						} else {
//							commit.addRemoved(origA);
//
//							Trees.checkConsistency(origA.getRootNode());
//						}
//
//
//						// add negated input presence condition to original association if intersection has artifacts (i.e. original association contains artifacts that were removed) and manual mode is activated
//						if (this.isManualMode() && !intA.getRootNode().getChildren().isEmpty()) {
//							for (at.jku.isse.ecco.module.Module m : inputA.getPresenceCondition().getMinModules()) {
//								if (m.size() == 1) { // only consider base modules
//									for (ModuleFeature f : m) {
//
//										Set<at.jku.isse.ecco.module.Module> new_modules = new HashSet<>();
//										Iterator<at.jku.isse.ecco.module.Module> it = origA.getPresenceCondition().getMinModules().iterator();
//										while (it.hasNext()) {
//											at.jku.isse.ecco.module.Module m2 = it.next();
//											at.jku.isse.ecco.module.Module m_new = this.entityFactory.createModule();
//											m_new.addAll(m2);
//											m_new.add(this.entityFactory.createModuleFeature(f.getFeature(), f, false));
//											new_modules.add(m_new);
//											it.remove();
//										}
//										origA.getPresenceCondition().getMinModules().addAll(new_modules);
//
//									}
//								}
//							}
//						}
//
//					}
//
//					// REMAINDER
//					// if the remainder is not empty store it
//					if (!inputA.getRootNode().getChildren().isEmpty()) {
//						Trees.sequence(inputA.getRootNode());
//						Trees.updateArtifactReferences(inputA.getRootNode());
//						Trees.checkConsistency(inputA.getRootNode());
//
//						toAdd.add(inputA);
//
//						commit.addNew(inputA);
//					} else if (!inputA.getPresenceCondition().isEmpty()) {
//						if (emptyAssociation == null) {
//							emptyAssociation = inputA;
//							emptyAssociation.setName("EMPTY");
//							toAdd.add(inputA);
//						} else if (emptyAssociation != inputA) {
//							emptyAssociation.getPresenceCondition().merge(inputA.getPresenceCondition());
//						}
//					}
//
//					originalAssociations.removeAll(toRemove);
//					originalAssociations.addAll(toAdd); // add new associations to original associations so that they can be sliced with the next input association
//					newAssociations.addAll(toAdd);
//					removedAssociations.addAll(toRemove);
//				}
//
//				// remove associations
//				for (Association origA : removedAssociations) {
//					this.associationDao.remove(origA);
//				}
//
//				// save associations
//				for (Association origA : originalAssociations) {
//					this.associationDao.save(origA);
//				}
//
//				// put together commit
//				for (Association newA : newAssociations) {
//					commit.addAssociation(newA);
//				}
//				persistedCommit = this.commitDao.save(commit);
//
//				this.transactionStrategy.end();
//			} catch (Exception e) {
//				this.transactionStrategy.rollback();
//
//				throw new EccoException("Error during commit.", e);
//			}
//
//			// fire event
//			if (persistedCommit != null)
//				this.fireCommitsChangedEvent(persistedCommit);
//
//			return persistedCommit;
//		}
//	}


	// CHECKOUT ////////////////////////////////////////////////////////////////////////////////////////////////////////

	/**
	 * Checks out the implementation of the configuration (given as configuration string) into the base directory.
	 *
	 * @param configurationString The configuration string representing the configuration that shall be checked out.
	 * @throws EccoException
	 */
	public Checkout checkout(String configurationString) throws EccoException {
		return this.checkout(this.parseConfigurationString(configurationString));
	}

	/**
	 * Checks out the implementation of the given configuration into the base directory.
	 *
	 * @param configuration The configuration to be checked out.
	 */
	public Checkout checkout(Configuration configuration) throws EccoException {
		checkNotNull(configuration);

//			System.out.println("CHECKOUT");
//
//
//			Set<Association> selectedAssociations = new HashSet<>();
//			LazyCompositionRootNode compRootNode = new LazyCompositionRootNode();
//			for (Association association : this.getAssociations()) {
//				System.out.println("Checking: " + association.getId());
//				if (association.getPresenceCondition().holds(configuration)) {
//					selectedAssociations.add(association);
//					System.out.println("Selected: " + association.getId());
//				}
//			}
//
//
//			DependencyGraph dg = new DependencyGraph(selectedAssociations, DependencyGraph.ReferencesResolveMode.INCLUDE_ALL_REFERENCED_ASSOCIATIONS);
//
////			if (INCLUDE_ALL_REFERENCED_ASSOCIATIONS) {
////				selectedAssociations.addAll(dg.getAssociations());
////			}
//
//
//			Set<at.jku.isse.ecco.module.Module> desiredModules = configuration.computeModules(this.getMaxOrder());
//			Set<at.jku.isse.ecco.module.Module> missingModules = new HashSet<>();
//			Set<at.jku.isse.ecco.module.Module> surplusModules = new HashSet<>();
//
//			for (Association association : selectedAssociations) {
//				compRootNode.addOrigNode(association.getRootNode());
//
//				this.fireAssociationSelectedEvent(association);
//
//				// compute missing
//				for (at.jku.isse.ecco.module.Module desiredModule : desiredModules) {
//					if (!association.getPresenceCondition().getMinModules().contains(desiredModule)) {
//						missingModules.add(desiredModule);
//					}
//				}
//				// compute surplus
//				for (at.jku.isse.ecco.module.Module existingModule : association.getPresenceCondition().getMinModules()) {
//					if (!desiredModules.contains(existingModule)) {
//						surplusModules.add(existingModule);
//					}
//				}
//			}
//
//			// compute unresolved dependencies
//			Set<Association> unresolvedAssociations = new HashSet<>(dg.getAssociations());
//			unresolvedAssociations.removeAll(selectedAssociations);
//
//
//			Checkout checkout = this.checkout(compRootNode);
//
//			checkout.getSurplus().addAll(surplusModules);
//			checkout.getMissing().addAll(missingModules);
//			checkout.getOrderWarnings().addAll(compRootNode.getOrderSelector().getUncertainOrders());
//			checkout.getUnresolvedAssociations().addAll(unresolvedAssociations);


		RepositoryOperand repository = this.repositoryDao.load();
		Checkout checkout = repository.compose(configuration);


		for (Association selectedAssociation : checkout.getSelectedAssociations()) {
			this.fireAssociationSelectedEvent(selectedAssociation);
		}

		// write config file into base directory
		if (Files.exists(this.baseDir.resolve(CONFIG_FILE_NAME))) {
			throw new EccoException("Configuration file already exists in base directory.");
		} else {
			try {
				Files.write(this.baseDir.resolve(CONFIG_FILE_NAME), configuration.toString().getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
			} catch (IOException e) {
				throw new EccoException("Could not create configuration file.", e);
			}
		}

		// write warnings file into base directory
		if (Files.exists(this.baseDir.resolve(WARNINGS_FILE_NAME))) {
			throw new EccoException("Warnings file already exists in base directory.");
		} else {
			try {
				StringBuffer sb = new StringBuffer();

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

				Files.write(this.baseDir.resolve(WARNINGS_FILE_NAME), sb.toString().getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
			} catch (IOException e) {
				throw new EccoException("Could not create warnings file.", e);
			}
		}

		return checkout;
	}

	public Checkout checkout(Node node) {
		Checkout checkout = new Checkout();

		Set<Node> nodes = new HashSet<>(node.getChildren());
		this.writer.write(this.baseDir, nodes);

		return checkout;
	}


	// OTHERS //////////////////////////////////////////////////////////////////////////////////////////////////////////

	public Repository getRepository() {
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
	public Collection<Commit> getCommits() {
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
//	 * Get all associations.
//	 *
//	 * @return Collection containing all associations.
//	 */
//	public Collection<Association> getAssociations() {
//		try {
//			this.associationDao.init();
//			this.transactionStrategy.begin();
//			List<Association> associations = this.associationDao.loadAllAssociations();
//			this.transactionStrategy.end();
//			return associations;
//		} catch (EccoException e) {
//			this.transactionStrategy.rollback();
//			throw new EccoException("Error when retrieving associations.", e);
//		}
//	}
//
//	/**
//	 * Get all features.
//	 *
//	 * @return Collection containing all features.
//	 */
//	public Collection<Feature> getFeatures() {
//		try {
//			this.featureDao.init();
//			this.transactionStrategy.begin();
//			Set<Feature> features = this.featureDao.loadAllFeatures();
//			this.transactionStrategy.end();
//			return features;
//		} catch (EccoException e) {
//			this.transactionStrategy.rollback();
//			throw new EccoException("Error when retrieving features.", e);
//		}
//	}

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
