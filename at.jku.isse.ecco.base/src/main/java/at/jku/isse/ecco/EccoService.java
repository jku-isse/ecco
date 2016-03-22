package at.jku.isse.ecco;

import at.jku.isse.ecco.composition.LazyCompositionRootNode;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Checkout;
import at.jku.isse.ecco.core.Commit;
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
import com.google.inject.*;
import com.google.inject.name.Names;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * TODO: deal with locking better. leave service thread unsafe or make it thread safe?
 */
public class EccoService {

	private static final Logger LOGGER = LoggerFactory.getLogger(EccoService.class);

	public static final String ECCO_PROPERTIES = "ecco.properties";
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


	// # SETTINGS ######################################################################################################

	// TODO: load these in init() via SettingsDao

	private Set<Path> customIgnoredFiles = new HashSet<>();

	private int maxOrder = 4;
	private String committer = "";

	public int getMaxOrder() {
		return this.maxOrder;
	}

	public void setMaxOrder(int maxOrder) {
		this.maxOrder = maxOrder;

		// TODO: set via settings dao
	}

	public String getCommitter() {
		return this.committer;
	}

	public void setCommitter(String committer) {
		this.committer = committer;

		// TODO: set via settings dao
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
			InputStream inputStream = getClass().getClassLoader().getResourceAsStream(ECCO_PROPERTIES);
			Properties eccoProperties = new Properties();
			List<String> artifactPluginsList = null;
			if (inputStream != null) {
				try {
					eccoProperties.load(inputStream);
				} catch (IOException e) {
					throw new EccoException("Could not load properties from file '" + ECCO_PROPERTIES + "'.");
				}
			} else {
				throw new EccoException("Property file '" + ECCO_PROPERTIES + "' not found in the classpath.");
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

	/**
	 * Creates a configuration from a given configuration string.
	 *
	 * @param configurationString The configuration string.
	 * @return The configuration object.
	 * @throws EccoException
	 */
	public Configuration parseConfigurationString(String configurationString) throws EccoException {
		if (configurationString == null || configurationString.isEmpty())
			throw new EccoException("No configuration string provided.");

		if (!configurationString.matches(Configuration.CONFIGURATION_STRING_REGULAR_EXPRESSION))
			throw new EccoException("Invalid configuration string provided.");

		Configuration configuration = this.entityFactory.createConfiguration();

		String[] featureInstanceStrings = configurationString.split(",");
		for (String featureInstanceString : featureInstanceStrings) {
			featureInstanceString = featureInstanceString.trim();
			if (featureInstanceString.contains(".")) {
				String[] pair = featureInstanceString.split("\\.");
				//String featureName = pair[0].replace("!", "").replace("+", "").replace("-", "");
				String featureName = pair[0];
				if (featureName.startsWith("!") || featureName.startsWith("-") || featureName.startsWith("+"))
					featureName = featureName.substring(1);
				int version = Integer.parseInt(pair[1]);
				boolean featureSign = !(pair[0].startsWith("!") || pair[0].startsWith("-"));

				Feature feature = this.entityFactory.createFeature(featureName);
				FeatureVersion featureVersion = this.entityFactory.createFeatureVersion(feature, version);

				configuration.addFeatureInstance(this.entityFactory.createFeatureInstance(feature, featureVersion, featureSign));
			} else {
				String featureName = featureInstanceString;
				if (featureName.startsWith("!") || featureName.startsWith("-") || featureName.startsWith("+"))
					featureName = featureName.substring(1);

				int version = -1; // TODO: how to deal with this? always use newest?
				boolean featureSign = !(featureInstanceString.startsWith("!") || featureInstanceString.startsWith("-"));

				Feature feature = this.entityFactory.createFeature(featureName);
				FeatureVersion featureVersion = this.entityFactory.createFeatureVersion(feature, version);

				configuration.addFeatureInstance(this.entityFactory.createFeatureInstance(feature, featureVersion, featureSign));
			}
		}

		return configuration;
	}


	// # CORE SERVICES #################################################################################################


	// COMMIT //////////////////////////////////////////////////////////////////////////////////////////////////////////

	/**
	 * Commits the files in the base directory using the configuration string given in {@link #CONFIG_FILE_NAME}.
	 *
	 * @return The resulting commit object.
	 * @throws EccoException When the configuration file does not exist or cannot be read.
	 */
	public Commit commit() throws EccoException {
		Path configFile = this.baseDir.resolve(CONFIG_FILE_NAME);
		if (!Files.exists(configFile)) {
			throw new EccoException("No configuration string was given and no configuration file (" + CONFIG_FILE_NAME.toString() + ") exists in base directory.");
		} else {
			try {
				String configurationString = new String(Files.readAllBytes(configFile));
				return this.commit(configurationString);
			} catch (IOException e) {
				throw new EccoException(e.getMessage());
			}
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
	public Commit commit(Configuration configuration, Set<Node> nodes) throws EccoException {
		// TODO: this is where i need to compute the updated configuration for the presence condition and update the existing associations
		this.addConfiguration(configuration);

		PresenceCondition presenceCondition = this.entityFactory.createPresenceCondition(configuration, this.maxOrder);

		Association association = this.entityFactory.createAssociation(presenceCondition, nodes);


		// SIMPLE MODULES
		association.getModules().addAll(configuration.computeModules(this.maxOrder));


		// TODO: set the correct configuration (i.e. the one with the replaced feature instances) here!
		Commit commit = this.commit(association);
		commit.setConfiguration(configuration);


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
		}


		return commit;
	}

	/**
	 * When an association is committed directly then the corresponding configuration must be added manually first!
	 *
	 * @param association The association to be committed.
	 * @return The resulting commit object or null in case of an error.
	 */
	public Commit commit(Association association) throws EccoException {
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
	public Commit commit(Collection<Association> inputAs) throws EccoException {
		// TODO: make sure the feature instances used in the given associations are the ones from the repository

		synchronized (this) {
			checkNotNull(inputAs);

			System.out.println("COMMIT");

			Commit persistedCommit = null;

			try {
				this.transactionStrategy.begin();

				List<Association> originalAssociations = this.associationDao.loadAllAssociations();
				List<Association> newAssociations = new ArrayList<>();

				// process each new association individually
				for (Association inputA : inputAs) {
					List<Association> toAdd = new ArrayList<>();

					// slice new association with every original association
					for (Association origA : originalAssociations) {

						// ASSOCIATION
						// slice the associations. the order matters here! the "left" association's featuers and artifacts are maintained. the "right" association's features and artifacts are replaced by the "left" association's.
						//Association intA = origA.slice(inputA);
						Association intA = this.entityFactory.createAssociation();


						// SIMPLE MODULES
						intA.getModules().addAll(origA.getModules());
						intA.getModules().retainAll(inputA.getModules());
						origA.getModules().removeAll(intA.getModules());
						inputA.getModules().removeAll(origA.getModules());


						// PRESENCE CONDITION
						intA.setPresenceCondition(origA.getPresenceCondition().slice(inputA.getPresenceCondition())); // TODO: do this in module util
						//intA.setPresenceCondition(FeatureUtil.slice(origA.getPresenceCondition(), inputA.getPresenceCondition()));


						// ARTIFACT TREE
						//intA.setRootNode((origA.getRootNode().slice(inputA.getRootNode())));
						intA.setRootNode((RootNode) Trees.slice(origA.getRootNode(), inputA.getRootNode()));


						// if the intersection association has artifacts or a not empty presence condition store it
						if (intA.getRootNode().getChildren().size() > 0 || !intA.getPresenceCondition().isEmpty()) {
							// set parents for intersection association (and child for parents)
							intA.addParent(origA);
							intA.addParent(inputA);
							intA.setName(origA.getId() + " INT " + inputA.getId());

							// store association
							toAdd.add(intA);
						}

						Trees.checkConsistency(origA.getRootNode());
						Trees.checkConsistency(intA.getRootNode());
					}
					originalAssociations.addAll(toAdd); // add new associations to original associations so that they can be sliced with the next input association
					newAssociations.addAll(toAdd);

					// if the remainder is not empty store it
					if (inputA.getRootNode().getChildren().size() > 0 || !inputA.getPresenceCondition().isEmpty()) {
						Trees.sequence(inputA.getRootNode());
						Trees.updateArtifactReferences(inputA.getRootNode());
						Trees.checkConsistency(inputA.getRootNode());

						originalAssociations.add(inputA);
						newAssociations.add(inputA);
					}
				}

				// save associations
				for (Association origA : originalAssociations) {
					this.associationDao.save(origA);
				}

				// put together commit
				Commit commit = this.entityFactory.createCommit();
				commit.setCommitter(this.committer); // TODO: get this value from the client config. maybe pass it as a parameter to this commit method.
				for (Association newA : newAssociations) {
					commit.addAssociation(newA);
				}
				persistedCommit = this.commitDao.save(commit);

				this.transactionStrategy.commit();
			} catch (Exception e) {
				this.transactionStrategy.rollback();

				//throw new EccoException(e.getMessage());
				throw e;
			}

			// fire event
			if (persistedCommit != null)
				this.fireCommitsChangedEvent(persistedCommit);

			return persistedCommit;
		}
	}


	/**
	 * This method first adds new features and feature versions to the repository.
	 * Then it updates the presence condition of every association to include the new feature versions.
	 *
	 * @param configuration
	 * @return
	 */
	public void addConfiguration(Configuration configuration) throws EccoException {
//		// first replace features and feature versions in configuration with existing ones in repository (and add negative features)
//		Configuration newConfiguration = this.entityFactory.createConfiguration();
//		for (FeatureInstance featureInstance : configuration.getFeatureInstances()) {
//
//			// first check if feature is contained in repository, if so use version from repository, if not add it to repository
//			Feature repoFeature = this.featureDao.load(featureInstance.getFeature().getName());
//			if (repoFeature != null) { // feature is contained in repo
//				// check if versions are also contained
//				for (FeatureVersion featureVersion : featureInstance.getFeatureVersions()) {
//					FeatureVersion repoFeatureVersion = null;
//					for (FeatureVersion tempRepoFeatureVersion : repoFeature.getVersions()) {
//						if (tempRepoFeatureVersion.equals(featureVersion))
//							repoFeatureVersion = tempRepoFeatureVersion;
//					}
//					if (repoFeatureVersion != null) {
//						// use repo version
//
//					} else {
//						// use version from config
//						newConfiguration.addFeatureInstance(featureInstance);
//						// add version to feature
//
//					}
//				}
//			} else { // feature is not contained in repo
//
//			}
//			// then check if feature instance is contained in repository, if so use version from repository, if not add it to repository
//
//		}

		// collect new features and feature versions and add them to the repository
		Set<Feature> newFeatures = new HashSet<Feature>();
		Set<FeatureVersion> newFeatureVersions = new HashSet<FeatureVersion>();
		for (FeatureInstance featureInstance : configuration.getFeatureInstances()) {
			Feature repoFeature = this.featureDao.load(featureInstance.getFeature().getName());
			if (repoFeature == null) {
				newFeatures.add(featureInstance.getFeature());
				this.featureDao.save(featureInstance.getFeature()); // save new feature including all its versions
			} else {
				if (!repoFeature.getVersions().contains(featureInstance.getFeatureVersion())) {
					FeatureVersion repoFeatureVersion = this.entityFactory.createFeatureVersion(repoFeature, featureInstance.getFeatureVersion().getVersion());
					newFeatureVersions.add(repoFeatureVersion);
					repoFeature.addVersion(repoFeatureVersion);
					this.featureDao.save(repoFeature); // save repo feature now containing new version
				}
			}
		}

		// update existing associations with new (features and) feature versions. NOTE: update with negative features is not necessary if the configurations contain also all the negative features!
		Collection<Association> associations = this.associationDao.loadAllAssociations();
		for (Association association : associations) {
			for (FeatureVersion newFeatureVersion : newFeatureVersions) {
				association.getPresenceCondition().addFeatureVersion(newFeatureVersion);
			}
		}

//		// return new configuration containing features and versions from the repository where they existed
//		return newConfiguration;
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


			Set<at.jku.isse.ecco.module.Module> desiredModules = configuration.computeModules(this.maxOrder);
			Set<at.jku.isse.ecco.module.Module> missingModules = new HashSet<>();
			Set<at.jku.isse.ecco.module.Module> surplusModules = new HashSet<>();


			LazyCompositionRootNode compRootNode = new LazyCompositionRootNode();
			for (Association association : this.getAssociations()) {
				System.out.println("Checking: " + association.getId());
				if (association.getPresenceCondition().holds(configuration)) {
					compRootNode.addOrigNode(association.getRootNode());
					System.out.println("Selected: " + association.getId());


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


	// OTHERS //////////////////////////////////////////////////////////////////////////////////////////////////////////

	/**
	 * Get all commit objects.
	 *
	 * @return Collection containing all commit objects.
	 */
	public Collection<Commit> getCommits() {
		try {
			this.commitDao.init();
			return this.commitDao.loadAllCommits();
		} catch (EccoException e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * Get all associations.
	 *
	 * @return Collection containing all associations.
	 */
	public Collection<Association> getAssociations() {
		try {
			this.associationDao.init();
			return this.associationDao.loadAllAssociations();
		} catch (EccoException e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * Get all features.
	 *
	 * @return Collection containing all features.
	 */
	public Collection<Feature> getFeatures() {
		try {
			this.featureDao.init();
			return this.featureDao.loadAllFeatures();
		} catch (EccoException e) {
			e.printStackTrace();
		}
		return null;
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

			throw e;
		}


	}


}
