package at.jku.isse.ecco;

import at.jku.isse.ecco.composition.BaseCompRootNode;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.dao.AssociationDao;
import at.jku.isse.ecco.dao.CommitDao;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.dao.FeatureDao;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureInstance;
import at.jku.isse.ecco.feature.FeatureVersion;
import at.jku.isse.ecco.listener.EccoListener;
import at.jku.isse.ecco.module.PresenceCondition;
import at.jku.isse.ecco.plugin.CoreModule;
import at.jku.isse.ecco.plugin.artifact.*;
import at.jku.isse.ecco.plugin.data.DataPlugin;
import at.jku.isse.ecco.tree.Node;
import com.google.inject.*;
import com.google.inject.name.Names;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * TODO: base and repository directories may not be changed anymore after the service has been initialized.
 * TODO: deal with locking better. leave service threat unsafe or make it thread safe?
 */
public class EccoService {

	private static final Logger LOGGER = LoggerFactory.getLogger(EccoService.class);

	public static final Path REPOSITORY_DIR_NAME = Paths.get(".ecco");
	public static final Path DEFAULT_BASE_DIR = Paths.get(".");
	public static final Path DEFAULT_REPOSITORY_DIR = DEFAULT_BASE_DIR.resolve(REPOSITORY_DIR_NAME);

	private Path baseDir;
	private Path repositoryDir;

	public EccoService() {
		this(DEFAULT_BASE_DIR, DEFAULT_REPOSITORY_DIR);

		this.detectRepository();
	}

	public EccoService(Path baseDir, Path repositoryDir) {
		this.baseDir = baseDir;
		this.repositoryDir = repositoryDir;

		this.ignoredFiles.add(REPOSITORY_DIR_NAME);
	}

	public Path getBaseDir() {
		return this.baseDir;
	}

	private void setBaseDir(Path baseDir) {
		if (!this.baseDir.equals(baseDir)) {
			this.baseDir = baseDir;
			this.fireStatusChangedEvent();
		}
	}

	public Path getRepositoryDir() {
		return this.repositoryDir;
	}

	private void setRepositoryDir(Path repositoryDir) {
		if (!this.repositoryDir.equals(repositoryDir)) {
			this.repositoryDir = repositoryDir;
			this.fireStatusChangedEvent();
		}
	}

	private Injector injector;

	private boolean initialized = false;

	public boolean isInitialized() {
		return this.initialized;
	}

	Set<Path> ignoredFiles = new HashSet<Path>(); // TODO: set this in dao

	// TODO: set these in dao
	//private int maxOrder = 4;
	//private String committer = "";

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

	// DAOs
	@Inject
	private AssociationDao associationDao;
	@Inject
	private CommitDao commitDao;
	@Inject
	private FeatureDao featureDao;


	// # LISTENERS #########################################

	private Collection<EccoListener> listeners = new ArrayList<EccoListener>();

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


	// # REPOSITORY SERVICES #########################################

	/**
	 * Checks if the repository directory (either given as a constructor parameter or detected using {@link #detectRepository(Path current) detectRepository}) exists.
	 *
	 * @return True if a repository exists, false otherwise.
	 */
	public boolean repositoryDirectoryExists() {
		if (!Files.exists(this.repositoryDir))
			return false;
		else
			return true;
	}

	/**
	 * Checks if a repository exists at the current directory or any of its parents.
	 *
	 * @param current The current directory.
	 * @return True if a repository was found, false otherwise.
	 */
	public boolean repositoryExists(Path current) {
		if (!Files.exists(current.resolve(REPOSITORY_DIR_NAME))) { // repository was not found
			try {
				Path parent = current.toRealPath().getParent();
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

	public boolean repositoryExists() {
		return this.repositoryExists(Paths.get(""));
	}

	/**
	 * Detects the repository directory automatically by checking the current directory and all its parents for the existence of a repository.
	 *
	 * @param current The current directory.
	 * @return True if a repository was found, false otherwise.
	 */
	public boolean detectRepository(Path current) {
		if (!Files.exists(current.resolve(REPOSITORY_DIR_NAME))) { // repository was not found
			//Path parent = current.normalize().getParent();
			try {
				Path parent = current.toRealPath().getParent();
				if (parent != null) // if the current directory has a parent
					return this.detectRepository(parent); // try to find a repository in the parent
			} catch (IOException e) {
				e.printStackTrace();
			}
			return false;
		} else { // repository was found
			//this.baseDir = current;
			this.setBaseDir(current);
			//this.repositoryDir = current.resolve(REPOSITORY_DIR_NAME);
			this.setRepositoryDir(current.resolve(REPOSITORY_DIR_NAME));
			return true;
		}
	}

	public boolean detectRepository() {
		return this.detectRepository(Paths.get(""));
	}

	/**
	 * Creates a repository at the current location if no repository already exists at the current location or any of its parents.
	 *
	 * @return True if the repository was created, false otherwise.
	 */
	public boolean createRepository() {
		try {
			if (!this.repositoryDirectoryExists())
				Files.createDirectory(this.repositoryDir);

			this.init();

			// TODO: do some initialization in database like generating root object, creating noArtifactAssoc, etc.?

			return true;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return false;
	}

	/**
	 * Initializes the service.
	 */
	public void init() {
		synchronized (this) {
			if (!this.repositoryDirectoryExists()) {
				LOGGER.debug("Repository does not exist.");
				return;
			}
			if (this.isInitialized()) {
				LOGGER.debug("Repository is already initialized.");
				return;
			}

			LOGGER.debug("BASE_DIR: " + this.baseDir);
			LOGGER.debug("REPOSITORY_DIR: " + this.repositoryDir);

			Properties properties = new Properties();
			properties.setProperty("module.dal", "at.jku.isse.ecco.perst");
			properties.setProperty("baseDir", this.baseDir.toString());
			properties.setProperty("repositoryDir", this.repositoryDir.toString());
			properties.setProperty("connectionString", this.repositoryDir.resolve("ecco.db").toString());
			properties.setProperty("clientConnectionString", this.repositoryDir.resolve("client.db").toString());
			properties.setProperty("serverConnectionString", this.repositoryDir.resolve("server.db").toString());

			// create modules
			final Module settingsModule = new AbstractModule() {
				@Override
				protected void configure() {
					bind(String.class).annotatedWith(Names.named("baseDir")).toInstance(properties.getProperty("baseDir"));
					bind(String.class).annotatedWith(Names.named("repositoryDir")).toInstance(properties.getProperty("repositoryDir"));

					bind(String.class).annotatedWith(Names.named("connectionString")).toInstance(properties.getProperty("connectionString"));
					bind(String.class).annotatedWith(Names.named("clientConnectionString")).toInstance(properties.getProperty("clientConnectionString"));
					bind(String.class).annotatedWith(Names.named("serverConnectionString")).toInstance(properties.getProperty("serverConnectionString"));
				}
			};
			// artifact modules
			List<Module> artifactModules = new ArrayList<Module>();
			for (ArtifactPlugin ap : ArtifactPlugin.getArtifactPlugins()) {
				artifactModules.add(ap.getModule());
			}
			LOGGER.debug("ARTIFACT PLUGINS: " + artifactModules.toString());
			// data modules
			List<Module> dataModules = new ArrayList<Module>();
			for (DataPlugin ap : DataPlugin.getDataPlugins()) {
				if (ap.getPluginId().equals(properties.get("module.dal")))
					dataModules.add(ap.getModule());
			}
			LOGGER.debug("DATA PLUGINS: " + dataModules.toString());
			// put them together
			List<Module> modules = new ArrayList<Module>();
			modules.addAll(Arrays.asList(new CoreModule(), settingsModule));
			modules.addAll(artifactModules);
			modules.addAll(dataModules);

			// create injector
			Injector injector = Guice.createInjector(modules);

			this.injector = injector;

			injector.injectMembers(this);

			this.associationDao.init();
			this.commitDao.init();
			this.featureDao.init();

			this.initialized = true;

			this.fireStatusChangedEvent();

			LOGGER.debug("Repository initialized.");
		}
	}


	// # UTILS #########################################

	public Configuration parseConfigurationString(String value) throws EccoException {
		if (value == null || value.isEmpty()) throw new EccoException("No configuration string provided.");

		if (!value.matches("(\\+|\\-)?[a-zA-Z]+('?|(\\.([0-9])+)?)(|,[a-zA-Z]+('?|(\\.([0-9])+)?))"))
			throw new EccoException("Invalid configuration string provided.");

		Configuration configuration = this.entityFactory.createConfiguration();

		String[] featureInstanceStrings = value.split(",");
		for (String featureInstanceString : featureInstanceStrings) {
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

	public String createConfigurationString(Configuration configuration) {
		String configurationString = configuration.getFeatureInstances().stream().map((FeatureInstance fi) -> {
			StringBuffer sb = new StringBuffer();
			if (fi.getSign())
				sb.append("+");
			else
				sb.append("-");
			sb.append(fi.getFeatureVersion().getFeature().getName());
			sb.append(".");
			sb.append(fi.getFeatureVersion());
			return sb.toString();
		}).collect(Collectors.joining(", "));
		return configurationString;
	}


	// # CORE SERVICES #########################################


	// COMMIT

	public Commit commit(String configurationString) throws EccoException {
		return this.commit(this.parseConfigurationString(configurationString));
	}

	/**
	 * Commits the set of artifacts residing in the base directory as the given configuration and returns the resulting commit object, or null in case of an error.
	 *
	 * @param configuration The configuration to be commited.
	 * @return The resulting commit object or null in case of an error.
	 */
	public Commit commit(Configuration configuration) throws EccoException {
		Set<Path> files = new HashSet<Path>();
		this.addAllFiles(this.baseDir, this.baseDir, files);
		files.removeAll(this.ignoredFiles);
		System.out.println(this.ignoredFiles);
		System.out.println(files);

		Set<Node> nodes = this.reader.read(this.baseDir, files.toArray(new Path[files.size()]));
		return this.commit(configuration, nodes);
	}

	private void addAllFiles(Path base, Path dir, Set<Path> files) {
		try {
			Files.list(dir).forEach(p -> {
				Path file = base.relativize(p);
				if (!this.ignoredFiles.contains(file)) {
					files.add(file);
					if (Files.isDirectory(p)) this.addAllFiles(base, p, files);
				}
			});
		} catch (IOException e) {
			e.printStackTrace();
		}
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

		PresenceCondition presenceCondition = this.entityFactory.createPresenceCondition(configuration);

		Association association = this.entityFactory.createAssociation(presenceCondition, nodes);

		return this.commit(association);
	}

	/**
	 * When an association is committed directly then the corresponding configuration must be added manually first!
	 *
	 * @param association
	 * @return
	 */
	public Commit commit(Association association) throws EccoException {
		List<Association> associations = new ArrayList<Association>(1);
		associations.add(association);
		return this.commit(associations);
	}

	/**
	 * When associations are committed directly then the corresponding configuration must be added manually first!
	 *
	 * @param inputAs
	 * @return
	 */
	public Commit commit(List<Association> inputAs) throws EccoException {
		synchronized (this) {
			checkNotNull(inputAs);

			System.out.println("COMMIT");

			List<Association> originalAssociations = this.associationDao.loadAllAssociations();
			List<Association> newAssociations = new ArrayList<Association>();

			// process each new association individually
			for (Association inputA : inputAs) {
				List<Association> toAdd = new ArrayList<Association>();

				// slice new association with every original association
				for (Association origA : originalAssociations) {

					// slice the associations. the order matters here! the "left" association's featuers and artifacts are maintained. the "right" association's features and artifacts are replaced by the "left" association's.
					//Association intA = origA.slice(inputA);
					Association intA = this.entityFactory.createAssociation();
					intA.setPresenceCondition(origA.getPresenceCondition().slice(inputA.getPresenceCondition()));
					intA.setArtifactRoot((origA.getArtifactTreeRoot().slice(inputA.getArtifactTreeRoot())));
					// set parents for intersection association
					intA.addParent(origA);
					intA.addParent(inputA);
					intA.setName(origA.getId() + " INT " + inputA.getId());

					// if the intersection association has artifacts or a not empty presence condition store it
					if (intA.getArtifactTreeRoot().getAllChildren().size() > 0 || !intA.getPresenceCondition().isEmpty()) {
						toAdd.add(intA);
					}
				}
				originalAssociations.addAll(toAdd); // add new associations to original associations so that they can be sliced with the next input association
				newAssociations.addAll(toAdd);

				// if the remainder is not empty store it
				if (inputA.getArtifactTreeRoot().getAllChildren().size() > 0 || !inputA.getPresenceCondition().isEmpty()) {
					// TODO: update references to features and artifacts here!
					// update artifact references
//					UpdateReferences updateReferences = new UpdateReferences();
//					updateReferences.apply(inputA.getArtifactTreeRoot());
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
			commit.setCommitter(""); // TODO: get this value from the client config. maybe pass it as a parameter to this commit method.
			for (Association newA : newAssociations) {
				commit.addAssociation(newA);
			}
			Commit persistedCommit = this.commitDao.save(commit);

			// fire event
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
	public void addConfiguration(Configuration configuration) {
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


	// CHECKOUT

	public void checkout(String configurationString) throws EccoException {
		this.checkout(this.parseConfigurationString(configurationString));
	}

	/**
	 * Checks out the implementation of the given configuration into the base directory.
	 *
	 * @param configuration The configuration to be checked out.
	 */
	public void checkout(Configuration configuration) {
		synchronized (this) {
			System.out.println("CHECKOUT");
			BaseCompRootNode compRootNode = new BaseCompRootNode();
			for (Association association : this.getAssociations()) {
				System.out.println("Checking: " + association.getId());
				if (true || association.getPresenceCondition().holds(configuration)) { // TODO: the "hold" method seems to not work correctly!
					compRootNode.addOrigNode(association.getArtifactTreeRoot());
					System.out.println("Selected: " + association.getId());
				}
			}

			Set<Node> nodes = new HashSet<>(compRootNode.getAllChildren());
			this.writer.write(this.baseDir, nodes);
		}
	}


	// OTHERS

	public Collection<Commit> getCommits() {
		this.commitDao.init();
		return this.commitDao.loadAllCommits();
	}

	public void deleteCommit(Commit commit) {
		// TODO
	}

	public Collection<Association> getAssociations() {
		this.associationDao.init();
		return this.associationDao.loadAllAssociations();
	}

	public Collection<Feature> getFeatures() {
		this.featureDao.init();
		return this.featureDao.loadAllFeatures();
	}


	public Injector getInjector() {
		return this.injector;
	}

}
