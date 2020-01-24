package at.jku.isse.ecco.cli;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.adapter.ArtifactWriter;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.DependencyGraph;
import at.jku.isse.ecco.core.Remote;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.service.listener.EccoListener;
import at.jku.isse.ecco.util.Trees;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * This class implements all the CLI commands.
 */
public class EccoCli implements EccoListener {

	private EccoService eccoService;


	// # EVENTS ######################################

	@Override
	public void fileReadEvent(Path file, ArtifactReader reader) {
		System.out.println("READ: " + file);
	}

	@Override
	public void fileWriteEvent(Path file, ArtifactWriter writer) {
		System.out.println("WRITE: " + file);
	}

	@Override
	public void associationSelectedEvent(EccoService service, Association association) {
		System.out.println("SELECTED: [" + association.getId() + "] " + association.computeCondition().getModuleRevisionConditionString());
	}

	@Override
	public void operationProgressEvent(EccoService service, String operationString, double progress) {
		System.out.print("\r[");
		for (int i = 0; i < 100; i += 10) {
			if (i < progress)
				System.out.print("=");
			else
				System.out.print(" ");
		}
		System.out.print("] " + (progress * 100.0) + "%");
	}


	// # CLI #########################################

	public EccoCli() {
		this.eccoService = new EccoService();
		this.eccoService.detectRepository();
		this.eccoService.addListener(this);
	}

	private void initRepo() {
		if (!this.eccoService.repositoryDirectoryExists())
			throw new EccoException("There is no repository at " + this.eccoService.getRepositoryDir());

		this.eccoService.open();
	}


	/**
	 * TODO:
	 * - ignore files
	 * - file to plugin map
	 * - untracked files: those not yet in file to plugin map
	 * - ignored files: those in ignore list
	 * - changed and unchanged files (during checkout save hash for every checked out file. this needs to be done in base directory, similar to .config or .warnings. for simplicity, initially i could generate .hashes or .ecco/.hashes).
	 * - current configuration: see .config
	 * - list loaded plugins
	 */


	public void init() {
		if (this.eccoService.repositoryDirectoryExists()) {
			System.err.println("ERROR: Repository already exists at this location.");
		} else {
			if (this.eccoService.init()) {
				System.out.println("SUCCESS: Repository initialized.");
				this.eccoService.close();
			} else
				System.err.println("ERROR: Error during repository initialization.");
		}
	}

	public void status() {
		this.initRepo();

		StringBuilder output = new StringBuilder();

		output.append("Repository Directory: " + this.eccoService.getRepositoryDir() + "\n");
		output.append("Base Directory: " + this.eccoService.getBaseDir() + "\n");

		// TODO: status

		// configuration
		output.append("Current Configuration: " + "\n");

		// files status
		output.append("Unchanged Files:");
		output.append("\n");

		output.append("Changed Files:");
		output.append("\n");

		output.append("Untracked Files:");
		output.append("\n");

		output.append("Ignored Files:");
		output.append("\n");

		System.out.println(output.toString());

		this.eccoService.close();
	}

	public void setProperty(String clientProperty, String value) {
		this.initRepo();

		switch (clientProperty.toLowerCase()) {
			case "basedir":
				Path baseDir = Paths.get(value);
				this.eccoService.setBaseDir(baseDir);
				System.out.println("SUCCESS: SET baseDir=" + baseDir);
				break;
//			case "maxorder":
//				int maxOrder = Integer.parseInt(value);
//				this.eccoService.setMaxOrder(maxOrder);
//				System.out.println("SUCCESS: SET maxOrder=" + maxOrder);
//				break;
			default:
				System.out.println("ERROR: No property named \"" + clientProperty + "\".");
				break;
		}

		this.eccoService.close();
	}

	public void getProperty(String clientProperty) {
		this.initRepo();

		switch (clientProperty.toLowerCase()) {
			case "basedir":
				System.out.println("SUCCESS: GET baseDir=" + this.eccoService.getBaseDir());
				break;
//			case "maxorder":
//				System.out.println("SUCCESS: GET maxOrder=" + this.eccoService.getMaxOrder());
//				break;
			default:
				System.out.println("ERROR: No property named \"" + clientProperty + "\".");
				break;
		}

		this.eccoService.close();
	}

//	public void addFiles(String pathString) throws EccoException {
//		if (!this.repository.repositoryDirectoryExists())
//			return;
//
//		this.repository.detectRepository();
//		this.repository.init();
//
//		// NOTE: maybe do this in the client service and not in the CLI.
//		try {
//			// collect ecco files
//			Set<Path> eccoFiles = new HashSet<Path>();
//			Files.walk(this.repository.getBaseDir()).forEach(eccoFiles::add);
//
//			// go through files in current folder
//			Files.walk(Paths.get(pathString)).filter(path -> {
//				boolean accept = true;
//				// ignore all ecco files
//				accept = accept && !eccoFiles.contains(path);
//				// ignore directories
//				accept = accept && !Files.isDirectory(path);
//				// ignore all files on the ignore list
////				accept = accept && !this.clientService.getIgnoredFiles().contains(path);
//				return accept;
//			}).forEach(path -> {
////				this.clientService.addTrackedFile(path);
//				System.out.println("ADDED: " + path.toString());
//			});
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
//
//	}
//
//	public void removeFiles(String path) {
//
//	}
//
//	public void ignoreFiles(String path) {
//
//	}

	public void checkout(String configurationString) {
		this.initRepo();

		this.eccoService.checkout(configurationString);

		this.eccoService.close();
	}

	public void commit() {
		this.initRepo();

		this.eccoService.commit();

		this.eccoService.close();
	}

	public void commit(String configurationString) {
		this.initRepo();

		this.eccoService.commit(configurationString);

		this.eccoService.close();
	}

	public void fork(String remoteUriString) {
		Path path;
		try {
			path = Paths.get(remoteUriString);
		} catch (InvalidPathException | NullPointerException ex) {
			path = null;
		}

		if (remoteUriString.matches("[a-zA-Z]+:[0-9]+")) {
			String[] pair = remoteUriString.split(":");
			String hostname = pair[0];
			int port = Integer.parseInt(pair[1]);
			this.eccoService.fork(hostname, port);
			this.eccoService.close();
		} else if (path != null) {
			this.eccoService.fork(path);
			this.eccoService.close();
		} else {
			System.err.println("ERROR: Invalid remote address provided.");
		}
	}

	public void fork(String remoteUriString, String excludedFeatureVersionsString) {
		Path path;
		try {
			path = Paths.get(remoteUriString);
		} catch (InvalidPathException | NullPointerException ex) {
			path = null;
		}

		if (remoteUriString.matches("[a-zA-Z]+:[0-9]+")) {
			String[] pair = remoteUriString.split(":");
			String hostname = pair[0];
			int port = Integer.parseInt(pair[1]);
			this.eccoService.fork(hostname, port, excludedFeatureVersionsString);
			this.eccoService.close();
		} else if (path != null) {
			this.eccoService.fork(path, excludedFeatureVersionsString);
			this.eccoService.close();
		} else {
			System.err.println("ERROR: Invalid remote address provided.");
		}
	}

	public void pull(String remoteName) {
		this.initRepo();

		this.eccoService.pull(remoteName);

		this.eccoService.close();
	}

	public void pull(String remoteName, String excludedFeatureVersionsString) {
		this.initRepo();

		this.eccoService.pull(remoteName, excludedFeatureVersionsString);

		this.eccoService.close();
	}

	public void push(String remoteName) {
		this.initRepo();

		this.eccoService.pull(remoteName);

		this.eccoService.close();
	}

	public void push(String remoteName, String excludedFeatureVersionsString) {
		this.initRepo();

		this.eccoService.push(remoteName, excludedFeatureVersionsString);

		this.eccoService.close();
	}

	public void fetch(String remoteName) {
		this.initRepo();

		this.eccoService.fetch(remoteName);

		this.eccoService.close();
	}

	public void addRemote(String remoteName, String remoteUriString) {
		this.eccoService.addRemote(remoteName, remoteUriString);

//		Path path;
//		try {
//			path = Paths.get(remoteUriString);
//		} catch (InvalidPathException | NullPointerException ex) {
//			path = null;
//		}
//
//		Remote.Type remoteType;
//		if (path != null) {
//			this.initRepo();
//			this.eccoService.addRemote(remoteName, remoteUriString, Remote.Type.LOCAL);
//			this.eccoService.close();
//		} else if (remoteUriString.matches("[a-zA-Z]+:[0-9]+")) {
//			this.initRepo();
//			this.eccoService.addRemote(remoteName, remoteUriString, Remote.Type.REMOTE);
//			this.eccoService.close();
//		} else {
//			System.err.println("ERROR: Invalid remote address provided.");
//		}
	}

	public void removeRemote(String remoteName) {
		this.initRepo();

		this.eccoService.removeRemote(remoteName);

		this.eccoService.close();
	}

	public void listRemotes() {
		this.initRepo();

		for (Remote remote : this.eccoService.getRemotes()) {
			System.out.println(remote.getName() + ": " + remote.getAddress() + " [" + remote.getType() + "]");
		}

		this.eccoService.close();
	}

	public void showRemote(String remoteName) {
		this.initRepo();

		Remote remote = this.eccoService.getRemote(remoteName);
		if (remote != null) {
			System.out.println(remote.getName() + ": " + remote.getAddress() + " [" + remote.getType() + "]");

			if (remote.getFeatures() != null) {
				for (Feature feature : remote.getFeatures()) {
					System.out.println(feature.toString());
					for (FeatureRevision fv : feature.getRevisions()) {
						System.out.println("\t" + fv);
					}
				}
			}
		} else {
			System.out.println("Remote " + remoteName + " does not exist.");
		}

		this.eccoService.close();
	}

	public void listFeatures() {
		this.initRepo();

		for (Feature feature : this.eccoService.getRepository().getFeatures()) {
			System.out.println(feature.toString());
		}

		this.eccoService.close();
	}

	public void showFeature(String featureName) {
		this.initRepo();

		for (Feature feature : this.eccoService.getRepository().getFeatures()) {
			if (feature.getName().equals(featureName)) {
				System.out.println(feature.toString());
				for (FeatureRevision fv : feature.getRevisions()) {
					System.out.println("\t" + fv);
				}
			}
		}

		this.eccoService.close();
	}

	public void listTraces() {
		this.initRepo();

		for (Association association : this.eccoService.getRepository().getAssociations()) {
			System.out.println("[" + association.getId() + "] " + association.computeCondition().getModuleRevisionConditionString());
		}

		this.eccoService.close();
	}

	public void showTraces(String traceId) {
		this.initRepo();

		for (Association association : this.eccoService.getRepository().getAssociations()) {
			if (association.getId().equals(traceId)) {
				System.out.println("[" + association.getId() + "] " + association.computeCondition().getModuleRevisionConditionString());
				Trees.print(association.getRootNode());
			}
		}

		this.eccoService.close();
	}

	public void showDependencyGraph() {
		this.initRepo();

		System.out.println(new DependencyGraph(this.eccoService.getRepository().getAssociations()).getGMLString()); // TODO: do this via the repository api

		this.eccoService.close();
	}

	public void setRepoDir(String repoDir) {
		this.eccoService.setRepositoryDir(Paths.get(repoDir));
	}

	public void setBaseDir(String baseDir) {
		this.eccoService.setBaseDir(Paths.get(baseDir));
	}

	public void startServer(int port) {
		this.initRepo();

		this.eccoService.startServer(port);

		this.eccoService.close();
	}

}
