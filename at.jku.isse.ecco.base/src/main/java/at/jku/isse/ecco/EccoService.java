package at.jku.isse.ecco;

import at.jku.isse.ecco.composition.LazyCompositionRootNode;
import at.jku.isse.ecco.core.*;
import at.jku.isse.ecco.dao.*;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureInstance;
import at.jku.isse.ecco.feature.FeatureVersion;
import at.jku.isse.ecco.listener.EccoListener;
import at.jku.isse.ecco.module.ModuleFeature;
import at.jku.isse.ecco.module.PresenceCondition;
import at.jku.isse.ecco.plugin.CoreModule;
import at.jku.isse.ecco.plugin.artifact.*;
import at.jku.isse.ecco.plugin.data.DataPlugin;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.tree.RootNode;
import at.jku.isse.ecco.util.Trees;
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
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * TODO: deal with locking better. leave service thread unsafe or make it thread safe?
 */
public class EccoService {

	protected static final boolean MERGE_EMPTY_ASSOCIATIONS = false;

	protected static final Logger LOGGER = LoggerFactory.getLogger(EccoService.class);

	public static final String ECCO_PROPERTIES_FILE = "ecco.properties";
	public static final String ECCO_PROPERTIES_DATA = "plugin.data";
	public static final String ECCO_PROPERTIES_ARTIFACT = "plugin.artifact";

	public static final Path REPOSITORY_DIR_NAME = Paths.get(".ecco");
	public static final Path DEFAULT_BASE_DIR = Paths.get("");
	public static final Path DEFAULT_REPOSITORY_DIR = DEFAULT_BASE_DIR.resolve(REPOSITORY_DIR_NAME);
	public static final Path CONFIG_FILE_NAME = Paths.get(".config");

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

		this.ignoredFiles.add(REPOSITORY_DIR_NAME);
		this.ignoredFiles.add(CONFIG_FILE_NAME);
	}

	private Set<Path> ignoredFiles = new HashSet<>();

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
	private AssociationDao associationDao;
	@Inject
	private CommitDao commitDao;
	@Inject
	private FeatureDao featureDao;
	@Inject
	private SettingsDao settingsDao;


	// # LISTENERS #####################################################################################################

	private Collection<EccoListener> listeners = new ArrayList<>();

	public void addListener(EccoListener listener) {
		this.listeners.add(listener);
	}

	public void removeListener(EccoListener listener) {
		this.listeners.remove(listener);
	}

	private void fireStatusChangedEvent() {
		for (EccoListener listener : this.listeners) {
			listener.statusChangedEvent(this);
		}
	}

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


	// # SETTINGS ######################################################################################################

	// TODO: load these in init() via SettingsDao

	private Set<Path> customIgnoredFiles = new HashSet<>();

	private int maxOrder = 4;
	private String committer = "";
	private boolean manualMode = false;

	public int getMaxOrder() {
		return this.maxOrder;
	}

	public void setMaxOrder(int maxOrder) {
		this.maxOrder = maxOrder;
	}

	public String getCommitter() {
		return this.committer;
	}

	public void setCommitter(String committer) {
		this.committer = committer;
	}

	public boolean isManualMode() {
		// TODO: set via settings dao?
//		return this.manualMode;
		return this.settingsDao.isManualMode();
	}

	public void setManualMode(boolean manualMode) {
		// TODO: set via settings dao?
		try {
			this.transactionStrategy.begin();

			if (!this.settingsDao.isManualMode())
//			this.manualMode = manualMode;
				this.settingsDao.setManualMode(manualMode);
			else if (!manualMode) {
				throw new EccoException("Once manual mode has been activated it cannot be turned off anymore.");
			}

			this.transactionStrategy.commit();
		} catch (Exception e) {
			this.transactionStrategy.rollback();

			throw new EccoException("Error retrieving settings.", e);
		}
	}

	public void addIgnoreFile(Path path) {
		this.customIgnoredFiles.add(path);
		this.ignoredFiles.add(path);

		// TODO: save via dao
	}

	public void removeIgnoreFile(Path path) {
		this.customIgnoredFiles.remove(path);
		this.ignoredFiles.remove(path);

		// TODO: save via dao
	}

	public Collection<Path> getIgnoreFiles() {
		return new ArrayList<>(this.ignoredFiles);
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
	public boolean createRepository() throws EccoException, IOException {
		if (!this.repositoryDirectoryExists())
			Files.createDirectory(this.repositoryDir);

		this.init();

		// TODO: do some initialization in database like generating root object, etc.?

		return true;
	}

	/**
	 * Initializes the service.
	 */
	public void init() throws EccoException {
		if (this.initialized)
			return;

		synchronized (this) {
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

			this.transactionStrategy.init();

			this.associationDao.init();
			this.commitDao.init();
			this.featureDao.init();
			this.settingsDao.init();

			this.reader.setIgnoredFiles(this.ignoredFiles);

			this.initialized = true;

			this.fireStatusChangedEvent();

			LOGGER.debug("Repository initialized.");
		}
	}

	/**
	 * Properly shuts down the service.
	 */
	public void destroy() throws EccoException {
		if (!this.initialized)
			return;

		this.transactionStrategy.close();
	}


	// # UTILS #########################################################################################################


	public void addRemote(String name, String address, String typeString) {
		Remote.Type type = Remote.Type.valueOf(typeString);
		Remote remote = this.entityFactory.createRemote(name, address, type);
		this.settingsDao.storeRemote(remote);
	}


	/**
	 * TODO: i don't like this solution. try to find a better one.
	 */
	public void updateAssociation(Association association) {
		try {
			this.transactionStrategy.begin();

			this.associationDao.save(association);

			this.transactionStrategy.commit();
		} catch (Exception e) {
			this.transactionStrategy.rollback();

			throw new EccoException("Error updating association.", e);
		}
	}


	/**
	 * Creates a presence condition from a given string. Uses existing features and feature versions from the repository. Adds new features and feature versions to the repository.
	 *
	 * @param pcString The presence condition string.
	 * @return The parsed presence condition.
	 */
	public PresenceCondition parsePresenceConditionString(String pcString) {
		if (pcString == null)
			throw new EccoException("No presence condition string provided.");

		if (!pcString.matches("\\([+-]?[a-zA-Z0-9_-]+(\\.((\\{([+-]?[0-9]+)(\\s*,\\s*([+-]?[0-9]+))*\\})|([+-]?[0-9]+))+)?(\\s*,\\s*[+-]?[a-zA-Z0-9_-]+(\\.((\\{([+-]?[0-9]+)(\\s*,\\s*([+-]?[0-9]+))*\\})|([+-]?[0-9]+))+)?)*\\)(\\s*,\\s*\\([+-]?[a-zA-Z0-9_-]+(\\.((\\{([+-]?[0-9]+)(\\s*,\\s*([+-]?[0-9]+))*\\})|([+-]?[0-9]+))+)?(\\s*,\\s*[+-]?[a-zA-Z0-9_-]+(\\.((\\{([+-]?[0-9]+)(\\s*,\\s*([+-]?[0-9]+))*\\})|([+-]?[0-9]+))+)?)*\\))*"))
			throw new EccoException("Invalid presence condition string provided.");

		try {
			this.transactionStrategy.begin();

			PresenceCondition pc = this.entityFactory.createPresenceCondition();


			// modules
			Pattern modulePattern = Pattern.compile("\\(([^()]*)\\)");
			Matcher moduleMatcher = modulePattern.matcher(pcString);
			while (moduleMatcher.find()) {
				for (int i = 0; i < moduleMatcher.groupCount(); i++) {
					String moduleString = moduleMatcher.group(i);

					at.jku.isse.ecco.module.Module module = this.entityFactory.createModule();

					// module features
					Pattern moduleFeaturePattern = Pattern.compile("(\\+|\\-)?([a-zA-Z0-9_-]+)(.(\\{[+-]?[a-zA-Z0-9_-]+(\\s*,\\s*[+-]?[a-zA-Z0-9_-]+)*\\}|[+-]?[0-9]+))?");
					Matcher moduleFeatureMatcher = moduleFeaturePattern.matcher(moduleString);
					while (moduleFeatureMatcher.find()) {
						String featureSignString = moduleFeatureMatcher.group(1);
						boolean featureSign = (featureSignString != null && featureSignString.equals("-")) ? false : true;
						String featureName = moduleFeatureMatcher.group(2);

						Feature feature;
						try {
							feature = this.featureDao.load(featureName);
						} catch (IllegalArgumentException e) {
							feature = null;
						}
						if (feature == null) {
							feature = this.entityFactory.createFeature(featureName);
//							feature = this.featureDao.save(feature);
						}

						Collection<FeatureVersion> featureVersions = new ArrayList<>();

						// versions
						//Pattern pattern3 = Pattern.compile("\\{([0-9]+)(\\s*,\\s*([0-9]+))*\\}");
						Pattern versionPattern = Pattern.compile("[^+-0-9{}, ]*([+-]?[0-9]+)[^+-0-9{}, ]*");
						if (moduleFeatureMatcher.group(3) != null) {
							Matcher versionMatcher = versionPattern.matcher(moduleFeatureMatcher.group(4));

							while (versionMatcher.find()) {
								int version = Integer.parseInt(versionMatcher.group(0));

//								FeatureVersion tempFeatureVersion = this.entityFactory.createFeatureVersion(feature, version);
//								FeatureVersion featureVersion = feature.getId(tempFeatureVersion);
								FeatureVersion featureVersion = feature.getVersion(version);
								if (featureVersion == null) {
//									feature.addVersion(tempFeatureVersion);
//									featureVersion = tempFeatureVersion;
									featureVersion = feature.addVersion(version);
//									feature = this.featureDao.save(feature);
								}

								featureVersions.add(featureVersion);
							}
						} else {
							//FeatureVersion tempFeatureVersion = this.entityFactory.createFeatureVersion(feature, FeatureVersion.NEWEST);
							//FeatureVersion featureVersion = feature.getId(tempFeatureVersion);
							FeatureVersion featureVersion = feature.getLatestVersion();
							if (featureVersion == null) {
//								feature.addVersion(tempFeatureVersion);
//								featureVersion = tempFeatureVersion;
								featureVersion = feature.createNewVersion();
							}
							featureVersions.add(featureVersion);
						}

						ModuleFeature mf = this.entityFactory.createModuleFeature(feature, featureVersions, featureSign);
						module.add(mf);

						feature = this.featureDao.save(feature);
					}

					pc.getMinModules().add(module);
				}
			}

			this.transactionStrategy.commit();

			return pc;
		} catch (Exception e) {
			this.transactionStrategy.rollback();

			throw new EccoException("Error parsing presence condition.", e);
		}
	}


	public Configuration parseConfigurationString(String configurationString) {
		//if (configurationString == null || configurationString.isEmpty())
		if (configurationString == null)
			throw new EccoException("No configuration string provided.");

		if (!configurationString.matches(Configuration.CONFIGURATION_STRING_REGULAR_EXPRESSION))
			throw new EccoException("Invalid configuration string provided.");

		try {
			this.transactionStrategy.begin();

			Configuration configuration = this.entityFactory.createConfiguration();

			if (configurationString.isEmpty()) {
				this.transactionStrategy.commit();
				return configuration;
			}

			Set<FeatureVersion> newFeatureVersions = new HashSet<>();

			String[] featureInstanceStrings = configurationString.split(",");
			for (String featureInstanceString : featureInstanceStrings) {
				featureInstanceString = featureInstanceString.trim();
				if (featureInstanceString.contains(".")) { // use specified feature version
					String[] pair = featureInstanceString.split("\\.");
					String featureName = pair[0];
					if (featureName.startsWith("!") || featureName.startsWith("-") || featureName.startsWith("+"))
						featureName = featureName.substring(1);
					int version = Integer.parseInt(pair[1]);
					boolean featureSign = !(pair[0].startsWith("!") || pair[0].startsWith("-"));


					Feature feature = this.featureDao.load(featureName);
					if (feature == null) {
						feature = this.entityFactory.createFeature(featureName);
					}
					FeatureVersion featureVersion = feature.getVersion(version);
					if (featureVersion == null) {
						featureVersion = feature.addVersion(version);
						newFeatureVersions.add(featureVersion);
					}
					this.featureDao.save(feature); // save repo feature now containing new version

					configuration.addFeatureInstance(this.entityFactory.createFeatureInstance(feature, featureVersion, featureSign));
				} else if (featureInstanceString.endsWith("'")) { // create new feature version for feature
					String featureName = featureInstanceString.substring(0, featureInstanceString.length() - 1);
					if (featureName.startsWith("!") || featureName.startsWith("-") || featureName.startsWith("+"))
						featureName = featureName.substring(1);

					Feature feature = this.featureDao.load(featureName);
					if (feature == null) {
						feature = this.entityFactory.createFeature(featureName);
					}
					FeatureVersion featureVersion = feature.createNewVersion();
					newFeatureVersions.add(featureVersion);
					this.featureDao.save(feature); // save repo feature now containing new version

					boolean featureSign = !(featureInstanceString.startsWith("!") || featureInstanceString.startsWith("-"));

					configuration.addFeatureInstance(this.entityFactory.createFeatureInstance(feature, featureVersion, featureSign));
				} else { // use most recent feature version of feature (or create a new one if none existed so far)
					String featureName = featureInstanceString;
					if (featureName.startsWith("!") || featureName.startsWith("-") || featureName.startsWith("+"))
						featureName = featureName.substring(1);

					Feature feature = this.featureDao.load(featureName);
					if (feature == null) {
						feature = this.entityFactory.createFeature(featureName);
					}
					FeatureVersion featureVersion = feature.getLatestVersion();
					if (featureVersion == null) {
						featureVersion = feature.createNewVersion();
						newFeatureVersions.add(featureVersion);
					}
					this.featureDao.save(feature); // save new feature including all its versions

					boolean featureSign = !(featureInstanceString.startsWith("!") || featureInstanceString.startsWith("-"));

					configuration.addFeatureInstance(this.entityFactory.createFeatureInstance(feature, featureVersion, featureSign));
				}
			}

			// update existing associations with new (features and) feature versions. NOTE: update with negative features is not necessary if the configurations contain also all the negative features!
			Collection<Association> associations = this.associationDao.loadAllAssociations();
			for (Association association : associations) {
				for (FeatureVersion newFeatureVersion : newFeatureVersions) {
					association.getPresenceCondition().addFeatureVersion(newFeatureVersion);
					this.associationDao.save(association);
				}
			}

			this.transactionStrategy.commit();

			return configuration;
		} catch (Exception e) {
			this.transactionStrategy.rollback();

			throw new EccoException("Error parsing configuration string.", e);
		}
	}


//	/**
//	 * Creates a configuration from a given configuration string.
//	 *
//	 * @param configurationString The configuration string.
//	 * @return The configuration object.
//	 * @throws EccoException
//	 */
//	public Configuration parseConfigurationString2(String configurationString) throws EccoException {
//		if (configurationString == null)
//			throw new EccoException("No configuration string provided.");
//
//		if (!configurationString.matches(Configuration.CONFIGURATION_STRING_REGULAR_EXPRESSION))
//			throw new EccoException("Invalid configuration string provided.");
//
//		Configuration configuration = this.entityFactory.createConfiguration();
//
//		if (configurationString.isEmpty())
//			return configuration;
//
//		String[] featureInstanceStrings = configurationString.split(",");
//		for (String featureInstanceString : featureInstanceStrings) {
//			featureInstanceString = featureInstanceString.trim();
//			if (featureInstanceString.contains(".")) {
//				String[] pair = featureInstanceString.split("\\.");
//				//String featureName = pair[0].replace("!", "").replace("+", "").replace("-", "");
//				String featureName = pair[0];
//				if (featureName.startsWith("!") || featureName.startsWith("-") || featureName.startsWith("+"))
//					featureName = featureName.substring(1);
//				int version = Integer.parseInt(pair[1]);
//				boolean featureSign = !(pair[0].startsWith("!") || pair[0].startsWith("-"));
//
//				Feature feature = this.entityFactory.createFeature(featureName);
//				FeatureVersion featureVersion = this.entityFactory.createFeatureVersion(feature, version);
//
//				configuration.addFeatureInstance(this.entityFactory.createFeatureInstance(feature, featureVersion, featureSign));
//			} else {
//				String featureName = featureInstanceString;
//				if (featureName.startsWith("!") || featureName.startsWith("-") || featureName.startsWith("+"))
//					featureName = featureName.substring(1);
//
//				int version = -1; // TODO: how to deal with this? always use newest?
//				boolean featureSign = !(featureInstanceString.startsWith("!") || featureInstanceString.startsWith("-"));
//
//				Feature feature = this.entityFactory.createFeature(featureName);
//				FeatureVersion featureVersion = this.entityFactory.createFeatureVersion(feature, version);
//
//				configuration.addFeatureInstance(this.entityFactory.createFeatureInstance(feature, featureVersion, featureSign));
//			}
//		}
//
//		return configuration;
//	}


	// # CORE SERVICES #################################################################################################


	/**
	 * Maps the given tree (e.g. result from a reader) to the repository without modifying the repository by replacing the artifacts in the given tree.
	 * With this way a reader could keep reading a file after it was changed, map it to the repository, and have the trace information again.
	 * The nodes contain the updated line/col information from the reader, and the marking can still be done on the artifacts in the repository.
	 * This also enables highlighting of selected associations in changed files.
	 *
	 * @param nodes The tree to be mapped.
	 */
	public void map(Collection<Node> nodes) {
		// TODO
	}


	/**
	 * Diffs the current working copy against the repository and returns a diff object containing all affected associations (and thus all affected features and artifacts).
	 *
	 * @return The diff object.
	 */
	public Diff diff() {
		// TODO
		return null;
	}


	/**
	 * Extracts all marked artifacts in the repository from their previous association into a new one.
	 *
	 * @return The commit object containing the affected associations.
	 */
	public Commit extract() {
		Commit persistedCommit = null;

		try {
			this.transactionStrategy.begin();

			Commit commit = this.entityFactory.createCommit();
			commit.setCommitter(this.committer); // TODO: get this value from the client config. maybe pass it as a parameter to this commit method.

			List<Association> originalAssociations = this.associationDao.loadAllAssociations();
			List<Association> newAssociations = new ArrayList<>();

			// extract from every  original association
			for (Association origA : originalAssociations) {


				// ASSOCIATION
				Association extractedA = this.entityFactory.createAssociation();


				// PRESENCE CONDITION
				extractedA.setPresenceCondition(this.entityFactory.createPresenceCondition(origA.getPresenceCondition()));


				// ARTIFACT TREE
				RootNode extractedTree = (RootNode) Trees.extractMarked(origA.getRootNode());
				if (extractedTree != null)
					extractedA.setRootNode(extractedTree);


				// if the intersection association has artifacts or a not empty presence condition store it
				if (extractedA.getRootNode() != null && (extractedA.getRootNode().getChildren().size() > 0 || !extractedA.getPresenceCondition().isEmpty())) {
					// set parents for intersection association (and child for parents)
					extractedA.addParent(origA);
					extractedA.setName("EXTRACTED " + origA.getId());

					// store association
					newAssociations.add(extractedA);

					commit.addNew(extractedA);
				}

				Trees.checkConsistency(origA.getRootNode());
				if (extractedA.getRootNode() != null)
					Trees.checkConsistency(extractedA.getRootNode());
			}
			originalAssociations.addAll(newAssociations);


			// save associations
			for (Association origA : originalAssociations) {
				this.associationDao.save(origA);
			}

			// put together commit
			for (Association newA : newAssociations) {
				commit.addAssociation(newA);
			}
			persistedCommit = this.commitDao.save(commit);

			this.transactionStrategy.commit();
		} catch (Exception e) {
			this.transactionStrategy.rollback();

			throw new EccoException("Error during extraction.", e);
		}

		// fire event
		if (persistedCommit != null)
			this.fireCommitsChangedEvent(persistedCommit);

		return persistedCommit;
	}


	// DISTRIBUTED OPERATIONS //////////////////////////////////////////////////////////////////////////////////////////


	public void fork(URL url, String configurationString) {
		throw new EccoException("Remote fork not yet implemented!");
	}

	/**
	 * This operation clones/forks a repository.
	 *
	 * @param parentRepositoryDir The parent repository directory from which to fork.
	 * @param configurationString The configuration string based on which the selection of features and traces from the parent repository is performed.
	 */
	//public void fork(Path parentRepositoryDir, String remoteName, String configurationString) {
	public void fork(Path parentRepositoryDir, String configurationString) {
		// step 1: (set repository dir,) check that this service has not yet been initialized. check that no repository already exists, create a new empty repository, and init this repository.
		if (this.isInitialized())
			throw new EccoException("ECCO Service must not be initialized for fork operation.");
		if (this.repositoryDirectoryExists())
			throw new EccoException("A repository already exists at the given location.");
		try {
			this.createRepository();
		} catch (IOException e) {
			throw new EccoException("Error while creating repository for fork.", e);
		}
		this.init();

//		Configuration configuration = this.parseConfigurationString(configurationString);


		// step 2: create another ecco service and init it on the parent repository directory.
		EccoService parentService = new EccoService();
		parentService.setRepositoryDir(parentRepositoryDir);
		parentService.init(); // TODO: init read only! add read only mode for that (also useful for other read only services on a repository such as a read only web interface REST API service).

		Collection<Feature> features = parentService.getFeatures();
		Collection<Association> associations = parentService.getAssociations();

		parentService.destroy();


		// step 3: selected features and associations from parent, clone them, and add them to this repository. (do not transfer commit objects for now as they may reference not needed associations!)
		try {
			this.transactionStrategy.begin();

			this.merge(features, associations);

			// after fork add used remote as default origin remote
			Remote remote = this.entityFactory.createRemote("origin", parentRepositoryDir.toString(), Remote.Type.LOCAL);
			this.settingsDao.storeRemote(remote);

			this.transactionStrategy.commit();
		} catch (Exception e) {
			this.transactionStrategy.rollback();

			throw new EccoException("Error during fork.", e);
		}
	}


	/**
	 * Pulls the changes from the parent repository to this repository.
	 */
	public void pull(String remoteName) {
		try {
			this.transactionStrategy.begin();

			// load remote
			Remote remote = this.settingsDao.loadRemote(remoteName);
			if (remote == null) {
				throw new EccoException("Remote " + remoteName + " does not exist");
			} else if (remote.getType() == Remote.Type.REMOTE) {
				throw new EccoException("Remote pull is not yet implemented");
			} else if (remote.getType() == Remote.Type.LOCAL) {
				// init this repo
				this.init();

				// open parent repo
				EccoService parentService = new EccoService();
				parentService.setRepositoryDir(Paths.get(remote.getAddress()));
				parentService.init(); // TODO: init read only! add read only mode for that (also useful for other read only services on a repository such as a read only web interface REST API service).

				Collection<Feature> features = parentService.getFeatures();
				Collection<Association> associations = parentService.getAssociations();

				parentService.destroy();


				this.merge(features, associations);
			}

			this.transactionStrategy.commit();
		} catch (Exception e) {
			this.transactionStrategy.rollback();

			throw new EccoException("Error during pull.", e);
		}
	}

	/**
	 * Pushes the changes from this repository to its parent repository.
	 */
	public void push(String remoteName) {
		try {
			this.transactionStrategy.begin();

			// load remote
			Remote remote = this.settingsDao.loadRemote(remoteName);
			if (remote == null) {
				throw new EccoException("Remote " + remoteName + " does not exist");
			} else if (remote.getType() == Remote.Type.REMOTE) {
				throw new EccoException("Remote pull is not yet implemented");
			} else if (remote.getType() == Remote.Type.LOCAL) {
				// init this repo
				this.init();

				// open parent repo
				EccoService parentService = new EccoService();
				parentService.setRepositoryDir(Paths.get(remote.getAddress()));
				parentService.init(); // TODO: init read only! add read only mode for that (also useful for other read only services on a repository such as a read only web interface REST API service).


				// pass all features and versions and associations from child to parent repo to merge them into parent repo because child has no permissions there
				parentService.merge(this.featureDao.loadAllFeatures(), this.associationDao.loadAllAssociations());


				// close parent repository
				parentService.destroy();
			}

			this.transactionStrategy.commit();
		} catch (Exception e) {
			this.transactionStrategy.rollback();

			throw new EccoException("Error during push.", e);
		}
	}

	protected void merge(Collection<Feature> features, Collection<Association> associations) {
		try {
			this.transactionStrategy.begin();

			// step 1: add new features and versions to associations in this repository
			Map<Feature, Feature> featureReplacementMap = new HashMap<>();
			Map<FeatureVersion, FeatureVersion> featureVersionReplacementMap = new HashMap<>();
			Collection<FeatureVersion> newChildFeatureVersions = new ArrayList<>();
			for (Feature parentFeature : features) {
				Feature childFeature = this.featureDao.load(parentFeature.getName()); // TODO: what to do when parent and child feature have different description? e.g. because it was changed on one of the two before the pull.
				if (childFeature == null) {
					childFeature = this.entityFactory.createFeature(parentFeature.getName(), parentFeature.getDescription());
				}
				featureReplacementMap.put(parentFeature, childFeature);

				for (FeatureVersion parentFeatureVersion : parentFeature.getVersions()) {
					FeatureVersion childFeatureVersion = childFeature.getVersion(parentFeatureVersion.getId());
					if (childFeatureVersion == null) {
						childFeatureVersion = childFeature.addVersion(parentFeatureVersion.getId());
						childFeatureVersion.setDescription(parentFeatureVersion.getDescription());
						newChildFeatureVersions.add(childFeatureVersion);
					}
					featureVersionReplacementMap.put(parentFeatureVersion, childFeatureVersion);
				}

				this.featureDao.save(childFeature);
			}
			for (Association childAssociation : this.associationDao.loadAllAssociations()) {
				for (FeatureVersion newChildFeatureVersion : newChildFeatureVersions) {
					childAssociation.getPresenceCondition().addFeatureVersion(newChildFeatureVersion);
				}
				for (FeatureVersion newChildFeatureVersion : newChildFeatureVersions) {
					childAssociation.getPresenceCondition().addFeatureInstance(this.entityFactory.createFeatureInstance(newChildFeatureVersion.getFeature(), newChildFeatureVersion, false));
				}
			}


			// step 2: copy input associations (just like during fork/clone)
			Collection<Association> copiedParentAssociations = new ArrayList<>();
			for (Association parentAssociation : associations) {
				Association copiedParentAssociation = this.entityFactory.createAssociation();
//				copiedParentAssociation.setId(parentAssociation.getId());
//				copiedParentAssociation.setName(parentAssociation.getName());

				PresenceCondition parentPresenceCondition = parentAssociation.getPresenceCondition();


				// copy presence condition
				PresenceCondition childPresenceCondition = this.entityFactory.createPresenceCondition();
				copiedParentAssociation.setPresenceCondition(childPresenceCondition);

				Set<at.jku.isse.ecco.module.Module>[][] moduleSetPairs = new Set[][]{{parentPresenceCondition.getMinModules(), childPresenceCondition.getMinModules()}, {parentPresenceCondition.getMaxModules(), childPresenceCondition.getMaxModules()}, {parentPresenceCondition.getNotModules(), childPresenceCondition.getNotModules()}, {parentPresenceCondition.getAllModules(), childPresenceCondition.getAllModules()}};

				for (Set<at.jku.isse.ecco.module.Module>[] moduleSetPair : moduleSetPairs) {
					Set<at.jku.isse.ecco.module.Module> parentModuleSet = moduleSetPair[0];
					Set<at.jku.isse.ecco.module.Module> childModuleSet = moduleSetPair[1];

					for (at.jku.isse.ecco.module.Module fromModule : parentModuleSet) {
						at.jku.isse.ecco.module.Module toModule = this.entityFactory.createModule();
						for (ModuleFeature fromModuleFeature : fromModule) {
							Feature fromFeature = fromModuleFeature.getFeature();
							Feature toFeature;
							if (featureReplacementMap.containsKey(fromFeature)) {
								toFeature = featureReplacementMap.get(fromFeature);
							} else {
								toFeature = fromFeature;

								throw new EccoException("This should not happen!");
							}

							ModuleFeature toModuleFeature = this.entityFactory.createModuleFeature(toFeature, fromModuleFeature.getSign());

							// feature versions
							for (FeatureVersion fromFeatureVersion : fromModuleFeature) {
								FeatureVersion toFeatureVersion;
								if (featureVersionReplacementMap.containsKey(fromFeatureVersion)) {
									toFeatureVersion = featureVersionReplacementMap.get(fromFeatureVersion);
								} else {
									toFeatureVersion = fromFeatureVersion;

									throw new EccoException("This should not happen!");
								}
								toModuleFeature.add(toFeatureVersion);
							}

							toModule.add(toModuleFeature);
						}
						childModuleSet.add(toModule);
					}
				}


				// copy artifact tree
				RootNode copiedRootNode = this.entityFactory.createRootNode();
				copiedParentAssociation.setRootNode(copiedRootNode);
				// clone tree
				for (Node parentChildNode : parentAssociation.getRootNode().getChildren()) {
					Node copiedChildNode = Trees.copy(parentChildNode, this.entityFactory);
					copiedRootNode.addChild(copiedChildNode);
					copiedChildNode.setParent(copiedRootNode);
				}
				Trees.checkConsistency(copiedRootNode);


				copiedParentAssociations.add(copiedParentAssociation);
			}


			// step 3: add new features in child repo to copied parent associations
			Collection<FeatureVersion> newParentFeatureVersions = new ArrayList<>();
			for (Feature childFeature : this.featureDao.loadAllFeatures()) {
				Feature parentFeature = null;
				for (Feature tempParentFeature : features) {
					if (tempParentFeature.equals(childFeature)) {
						parentFeature = tempParentFeature;
						break;
					}
				}
				if (parentFeature == null) {
					// add all its versions to list
					for (FeatureVersion childFeatureVersion : childFeature.getVersions()) {
						newParentFeatureVersions.add(childFeatureVersion);
					}
				} else {
					// compare versions and add new ones to list
					for (FeatureVersion childFeatureVersion : childFeature.getVersions()) {
						FeatureVersion parentFeatureVersion = parentFeature.getVersion(childFeatureVersion.getId());
						if (parentFeatureVersion == null) {
							newParentFeatureVersions.add(childFeatureVersion);
						}
					}
				}
			}
			for (Association copiedAssociation : copiedParentAssociations) {
				for (FeatureVersion newParentFeatureVersion : newParentFeatureVersions) {
					copiedAssociation.getPresenceCondition().addFeatureVersion(newParentFeatureVersion);
				}
				for (FeatureVersion newParentFeatureVersion : newParentFeatureVersions) {
					copiedAssociation.getPresenceCondition().addFeatureInstance(this.entityFactory.createFeatureInstance(newParentFeatureVersion.getFeature(), newParentFeatureVersion, false), this.getMaxOrder());
				}
			}


			// step 4: commit copied associations to this repository
			this.commit(copiedParentAssociations);

			this.transactionStrategy.commit();
		} catch (Exception e) {
			this.transactionStrategy.rollback();

			throw new EccoException("Error during merge.", e);
		}
	}


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
		Set<Node> nodes = this.reader.read(this.baseDir, new Path[]{Paths.get("")});
		return this.commit(configuration, nodes);
	}

	/**
	 * Commits a set of artifact nodes as a given configuration to the repository and returns the resulting commit object, or null in case of an error.
	 *
	 * @param configuration The configuration that is committed.
	 * @param nodes         The artifact nodes that implement the given configuration.
	 * @return The resulting commit object or null in case of an error.
	 */
	protected Commit commit(Configuration configuration, Set<Node> nodes) throws EccoException {
		try {
			this.transactionStrategy.begin();

			// add new features and versions from configuration to this repository
			Collection<FeatureVersion> newFeatureVersions = new ArrayList<>();
			Configuration newConfiguration = this.entityFactory.createConfiguration();
			for (FeatureInstance featureInstance : configuration.getFeatureInstances()) {
				Feature feature = featureInstance.getFeature();
				Feature repoFeature = this.featureDao.load(featureInstance.getFeature().getName());
				if (repoFeature == null) {
					repoFeature = this.entityFactory.createFeature(feature.getName(), feature.getDescription());
				}
				FeatureVersion featureVersion = featureInstance.getFeatureVersion();
				FeatureVersion repoFeatureVersion = repoFeature.getVersion(featureVersion.getId());
				if (repoFeatureVersion == null) {
					repoFeatureVersion = repoFeature.addVersion(featureVersion.getId());
					repoFeatureVersion.setDescription(featureVersion.getDescription());
					newFeatureVersions.add(repoFeatureVersion);
				}
				FeatureInstance newFeatureInstance = this.entityFactory.createFeatureInstance(repoFeature, repoFeatureVersion, featureInstance.getSign());
				newConfiguration.addFeatureInstance(newFeatureInstance);
				this.featureDao.save(repoFeature);
			}
			for (Association childAssociation : this.associationDao.loadAllAssociations()) {
				for (FeatureVersion newFeatureVersion : newFeatureVersions) {
					childAssociation.getPresenceCondition().addFeatureVersion(newFeatureVersion);
				}
				for (FeatureVersion newFeatureVersion : newFeatureVersions) {
					childAssociation.getPresenceCondition().addFeatureInstance(this.entityFactory.createFeatureInstance(newFeatureVersion.getFeature(), newFeatureVersion, false), this.getMaxOrder());
				}
			}
//			Configuration newConfiguration = configuration;

			// create presence condition
			PresenceCondition presenceCondition = this.entityFactory.createPresenceCondition(newConfiguration, this.getMaxOrder());

			// create association
			Association association = this.entityFactory.createAssociation(presenceCondition, nodes);


			// SIMPLE MODULES
			association.getModules().addAll(configuration.computeModules(this.getMaxOrder())); // TODO: this is obsolete. remove at some point.


			// commit association
			Commit commit = this.commit(association);
			commit.setConfiguration(configuration);

			commit = this.commitDao.save(commit);


			if (!this.isManualMode()) { // TODO: bring this up to date.
				// PRESENCE TABLE
				for (Association commitAssociation : commit.getAssociations()) {
					for (FeatureInstance featureInstance : configuration.getFeatureInstances()) {
						// find module feature in the map that has same feature and sign
						ModuleFeature moduleFeature = null;
						int count = 0;
						Iterator<Map.Entry<ModuleFeature, Integer>> iterator = commitAssociation.getPresenceTable().entrySet().iterator();
						while (iterator.hasNext()) {
							Map.Entry<ModuleFeature, Integer> entry = iterator.next();

							if (entry.getKey().getSign() == featureInstance.getSign() && entry.getKey().getFeature().equals(featureInstance.getFeature())) {
								moduleFeature = entry.getKey();
								count = entry.getValue();

								iterator.remove();
								break;
							}
						}
						if (moduleFeature == null) {
							moduleFeature = this.entityFactory.createModuleFeature(featureInstance.getFeature(), featureInstance.getSign());
						}
						moduleFeature.add(featureInstance.getFeatureVersion());
						count++;
						commitAssociation.getPresenceTable().put(moduleFeature, count);
					}
					commitAssociation.incPresenceCount();
					//this.updateAssociation(commitAssociation);
				}
			}


			this.transactionStrategy.commit();

			return commit;
		} catch (Exception e) {
			this.transactionStrategy.rollback();

			throw new EccoException("Error during commit.", e);
		}
	}

	/**
	 * When an association is committed directly then the corresponding configuration must be added manually first!
	 *
	 * @param association The association to be committed.
	 * @return The resulting commit object or null in case of an error.
	 */
	protected Commit commit(Association association) throws EccoException {
		checkNotNull(association);

		List<Association> associations = new ArrayList<>(1);
		associations.add(association);
		return this.commit(associations);
	}

	/**
	 * When associations are committed directly then the corresponding configuration must be added manually first!
	 *
	 * @param inputAs The collection of associations to be committed.
	 * @return The resulting commit object or null in case of an error.
	 */
	protected Commit commit(Collection<Association> inputAs) throws EccoException {
		synchronized (this) {
			checkNotNull(inputAs);

			LOGGER.debug("COMMIT");


			Commit persistedCommit = null;

			try {
				this.transactionStrategy.begin();

				Commit commit = this.entityFactory.createCommit();
				commit.setCommitter(this.getCommitter());

				List<Association> originalAssociations = this.associationDao.loadAllAssociations();
				List<Association> newAssociations = new ArrayList<>();
				List<Association> removedAssociations = new ArrayList<>();

				Association emptyAssociation = null;
				// find initial empty association if there is any
				for (Association origA : originalAssociations) {
					if (origA.getRootNode().getChildren().isEmpty()) {
						emptyAssociation = origA;
						break;
					}
				}

				// process each new association individually
				for (Association inputA : inputAs) {
					List<Association> toAdd = new ArrayList<>();
					List<Association> toRemove = new ArrayList<>();

					// slice new association with every original association
					for (Association origA : originalAssociations) {

						// ASSOCIATION
						// slice the associations. the order matters here! the "left" association's featuers and artifacts are maintained. the "right" association's features and artifacts are replaced by the "left" association's.
						//Association intA = origA.slice(inputA);
						Association intA = this.entityFactory.createAssociation();


						// SIMPLE MODULES
						intA.getModules().addAll(origA.getModules());
						if (!this.isManualMode()) {
							intA.getModules().retainAll(inputA.getModules());
							origA.getModules().removeAll(intA.getModules());
						}
						inputA.getModules().removeAll(origA.getModules());


						// PRESENCE CONDITION
						if (!this.isManualMode()) {
							//intA.setPresenceCondition(FeatureUtil.slice(origA.getPresenceCondition(), inputA.getPresenceCondition()));
							intA.setPresenceCondition(origA.getPresenceCondition().slice(inputA.getPresenceCondition()));
						} else {
							// clone original presence condition for intersection association and leave it unchanged.
							intA.setPresenceCondition(this.entityFactory.createPresenceCondition(origA.getPresenceCondition()));
						}


						// ARTIFACT TREE
						//intA.setRootNode((origA.getRootNode().slice(inputA.getRootNode())));
						intA.setRootNode((RootNode) Trees.slice(origA.getRootNode(), inputA.getRootNode()));


						// INTERSECTION
						if (!intA.getRootNode().getChildren().isEmpty()) { // if the intersection association has artifacts store it
							// set parents for intersection association (and child for parents)
							intA.addParent(origA);
							intA.addParent(inputA);
							intA.setName(origA.getId() + " INT " + inputA.getId());

							toAdd.add(intA);

							commit.addUnmodified(intA);

							Trees.checkConsistency(intA.getRootNode());
						} else if (!intA.getPresenceCondition().isEmpty()) { // if it has no artifacts but a not empty presence condition merge it with other empty associations
							if (emptyAssociation == null) {
								emptyAssociation = intA;
								emptyAssociation.setName("EMPTY");
								toAdd.add(intA);
							} else if (emptyAssociation != intA) {
								emptyAssociation.getPresenceCondition().merge(intA.getPresenceCondition());
							}
						}

						// ORIGINAL
						if (origA.getRootNode().getChildren().isEmpty()) { // if the original association has no artifacts left
							if (!origA.getPresenceCondition().isEmpty()) { // if presence condition is not empty merge it
								if (emptyAssociation == null) {
									emptyAssociation = origA;
									emptyAssociation.setName("EMPTY");
								} else if (emptyAssociation != origA) {
									emptyAssociation.getPresenceCondition().merge(origA.getPresenceCondition());
									toRemove.add(origA);
								}
							} else {
								toRemove.add(origA);
							}
						} else {
							commit.addRemoved(origA);

							Trees.checkConsistency(origA.getRootNode());
						}


						// add negated input presence condition to original association if intersection has artifacts (i.e. original association contains artifacts that were removed) and manual mode is activated
						if (this.isManualMode() && !intA.getRootNode().getChildren().isEmpty()) {
							for (at.jku.isse.ecco.module.Module m : inputA.getPresenceCondition().getMinModules()) {
								if (m.size() == 1) { // only consider base modules
									for (ModuleFeature f : m) {

										Set<at.jku.isse.ecco.module.Module> new_modules = new HashSet<>();
										Iterator<at.jku.isse.ecco.module.Module> it = origA.getPresenceCondition().getMinModules().iterator();
										while (it.hasNext()) {
											at.jku.isse.ecco.module.Module m2 = it.next();
											at.jku.isse.ecco.module.Module m_new = this.entityFactory.createModule();
											m_new.addAll(m2);
											m_new.add(this.entityFactory.createModuleFeature(f.getFeature(), f, false));
											new_modules.add(m_new);
											it.remove();
										}
										origA.getPresenceCondition().getMinModules().addAll(new_modules);

									}
								}
							}
						}

					}

					// REMAINDER
					// if the remainder is not empty store it
					if (!inputA.getRootNode().getChildren().isEmpty()) {
						Trees.sequence(inputA.getRootNode());
						Trees.updateArtifactReferences(inputA.getRootNode());
						Trees.checkConsistency(inputA.getRootNode());

						toAdd.add(inputA);

						commit.addNew(inputA);
					} else if (!inputA.getPresenceCondition().isEmpty()) {
						if (emptyAssociation == null) {
							emptyAssociation = inputA;
							emptyAssociation.setName("EMPTY");
							toAdd.add(inputA);
						} else if (emptyAssociation != inputA) {
							emptyAssociation.getPresenceCondition().merge(inputA.getPresenceCondition());
						}
					}

					originalAssociations.removeAll(toRemove);
					originalAssociations.addAll(toAdd); // add new associations to original associations so that they can be sliced with the next input association
					newAssociations.addAll(toAdd);
					removedAssociations.addAll(toRemove);
				}

				// remove associations
				for (Association origA : removedAssociations) {
					this.associationDao.remove(origA);
				}

				// save associations
				for (Association origA : originalAssociations) {
					this.associationDao.save(origA);
				}

				// put together commit
				for (Association newA : newAssociations) {
					commit.addAssociation(newA);
				}
				persistedCommit = this.commitDao.save(commit);

				this.transactionStrategy.commit();
			} catch (Exception e) {
				this.transactionStrategy.rollback();

				throw new EccoException("Error during commit.", e);
			}

			// fire event
			if (persistedCommit != null)
				this.fireCommitsChangedEvent(persistedCommit);

			return persistedCommit;
		}
	}


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
		synchronized (this) {
			checkNotNull(configuration);

			System.out.println("CHECKOUT");


			Set<at.jku.isse.ecco.module.Module> desiredModules = configuration.computeModules(this.getMaxOrder());
			Set<at.jku.isse.ecco.module.Module> missingModules = new HashSet<>();
			Set<at.jku.isse.ecco.module.Module> surplusModules = new HashSet<>();

			LazyCompositionRootNode compRootNode = new LazyCompositionRootNode();
			for (Association association : this.getAssociations()) {
				System.out.println("Checking: " + association.getId());
				if (association.getPresenceCondition().holds(configuration)) {
					compRootNode.addOrigNode(association.getRootNode());
					System.out.println("Selected: " + association.getId());

					this.fireAssociationSelectedEvent(association);

					// compute missing
					for (at.jku.isse.ecco.module.Module desiredModule : desiredModules) {
						if (!association.getPresenceCondition().getMinModules().contains(desiredModule)) {
							missingModules.add(desiredModule);
						}
					}
					// compute surplus
					for (at.jku.isse.ecco.module.Module existingModule : association.getPresenceCondition().getMinModules()) {
						if (!desiredModules.contains(existingModule)) {
							surplusModules.add(existingModule);
						}
					}
				}
			}

			Checkout checkout = this.checkout(compRootNode);

			checkout.getSurplus().addAll(surplusModules);
			checkout.getMissing().addAll(missingModules);

			return checkout;
		}
	}

	public Checkout checkout(Node node) {
		Checkout checkout = this.entityFactory.createCheckout();

		Set<Node> nodes = new HashSet<>(node.getChildren());
		this.writer.write(this.baseDir, nodes);

		return checkout;
	}


	// COMPOSE /////////////////////////////////////////////////////////////////////////////////////////////////////////

	public Node compose(String configurationString) {
		return this.compose(this.parseConfigurationString(configurationString));
	}

	public Node compose(Configuration configuration) {
		// TODO: use eager composition here and not lazy!

		Set<at.jku.isse.ecco.module.Module> desiredModules = configuration.computeModules(this.getMaxOrder());
		Set<at.jku.isse.ecco.module.Module> missingModules = new HashSet<>();
		Set<at.jku.isse.ecco.module.Module> surplusModules = new HashSet<>();

		LazyCompositionRootNode compRootNode = new LazyCompositionRootNode();
		for (Association association : this.getAssociations()) {
			System.out.println("Checking: " + association.getId());
			if (association.getPresenceCondition().holds(configuration)) {
				compRootNode.addOrigNode(association.getRootNode());
				System.out.println("Selected: " + association.getId());

				this.fireAssociationSelectedEvent(association);

				// compute missing
				for (at.jku.isse.ecco.module.Module desiredModule : desiredModules) {
					if (!association.getPresenceCondition().getMinModules().contains(desiredModule)) {
						missingModules.add(desiredModule);
					}
				}
				// compute surplus
				for (at.jku.isse.ecco.module.Module existingModule : association.getPresenceCondition().getMinModules()) {
					if (!desiredModules.contains(existingModule)) {
						surplusModules.add(existingModule);
					}
				}
			}
		}
		return compRootNode;
	}


	// OTHERS //////////////////////////////////////////////////////////////////////////////////////////////////////////

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
			this.transactionStrategy.commit();
			return commits;
		} catch (EccoException e) {
			this.transactionStrategy.rollback();
			throw new EccoException("Error when retrieving commits.", e);
		}
	}

	/**
	 * Get all associations.
	 *
	 * @return Collection containing all associations.
	 */
	public Collection<Association> getAssociations() {
		try {
			this.associationDao.init();
			this.transactionStrategy.begin();
			List<Association> associations = this.associationDao.loadAllAssociations();
			this.transactionStrategy.commit();
			return associations;
		} catch (EccoException e) {
			this.transactionStrategy.rollback();
			throw new EccoException("Error when retrieving associations.", e);
		}
	}

	/**
	 * Get all features.
	 *
	 * @return Collection containing all features.
	 */
	public Collection<Feature> getFeatures() {
		try {
			this.featureDao.init();
			this.transactionStrategy.begin();
			Set<Feature> features = this.featureDao.loadAllFeatures();
			this.transactionStrategy.commit();
			return features;
		} catch (EccoException e) {
			this.transactionStrategy.rollback();
			throw new EccoException("Error when retrieving features.", e);
		}
	}

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


	// # TODO


	public void deleteCommit(Commit commit) {
		// TODO

		try {
			this.transactionStrategy.begin();

			// get association unique to commit
			Association uniqueAssociation = null;
			for (Association association : commit.getAssociations()) {
				if (association.getParents().isEmpty()) {
					uniqueAssociation = association;
					break;
				}
			}

			// remove new feature versions from all associations
			for (Association association : this.getAssociations()) {
				// TODO!
				//association.getPresenceCondition().removeFeatureVersion(commit.getNewFeatureVersions());
			}

			// remove new modules (the min modules from unique association) from all associations
			for (Association association : this.getAssociations()) {
				if (association != uniqueAssociation)
					association.getPresenceCondition().removeModules(uniqueAssociation.getPresenceCondition().getMinModules());
			}


			// go through every association (except for unique association) from commit and merge with parent (that is not the unique association)
			for (Association association : commit.getAssociations()) {
				if (association != uniqueAssociation) {
					// merge with parent that is NOT the unique association
					Association parent = null;
					for (Association tempParent : association.getParents()) {
						if (tempParent != uniqueAssociation) {
							parent = tempParent;
							break;
						}
					}

					// merge trees
					Trees.merge(parent.getRootNode(), association.getRootNode());

					// merge modules
					// TODO!
					//parent.getPresenceCondition().add(association.getPresenceCondition());

					// remove association
					this.associationDao.remove(association);
				}
			}
			// remove unique association
			this.associationDao.remove(uniqueAssociation);


			this.transactionStrategy.commit();
		} catch (Exception e) {
			this.transactionStrategy.rollback();

			throw new EccoException("Error during deletion of commit.", e);
		}


	}


}
