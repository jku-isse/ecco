package at.jku.isse.ecco.cli;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.service.ClientService;
import at.jku.isse.ecco.EccoService;
import at.jku.isse.ecco.service.ServerService;
import com.google.inject.Inject;
import org.perf4j.LoggingStopWatch;
import org.perf4j.StopWatch;
import org.perf4j.aop.Profiled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/*
 * TODO:
 * - extract the core features into EccoService
 * - where is the db connection closed?
 * - are objects read from the perst db unique?
 */
public class CLI {

	private static final Logger LOGGER = LoggerFactory.getLogger(CLI.class);

	// # SERVICES #########################################

	private EccoService eccoService = new EccoService();

	private ClientService clientService;
	private ServerService serverService;

	// # UTILS #########################################

	private boolean checkRepository() {
		if (!this.eccoService.repositoryDirectoryExists()) {
			System.err.println("ERROR: There is no repository at the current location or any parent location.");
			System.exit(1);
			return false;
		} else {
			return true;
		}
	}

	// # CLI #########################################

	@Inject
	public CLI(ClientService clientService, ServerService serverService) {
		this.clientService = clientService;
		this.serverService = serverService;
	}

	// client

	@Profiled // this annotation requires aspectj or spring aop to work.
	public void init() {
		StopWatch stopWatch = new LoggingStopWatch();
		if (this.eccoService.repositoryDirectoryExists()) {
			System.err.println("ERROR: Repository already exists at this location.");
		} else {
			if (this.eccoService.createRepository())
				System.out.println("SUCCESS: Repository initialized.");
			else
				System.err.println("ERROR: Error during repository initialization.");

		}
		stopWatch.stop();
	}

	public void status() {
		if (!this.checkRepository())
			return;

		this.clientService.init();

		StringBuffer output = new StringBuffer();

		// configuration
		output.append("Current configuration: ");

		Configuration configuration = this.clientService.getCurrentConfiguration();

		output.append(this.eccoService.createConfigurationString(configuration));
		output.append("\n");

		// files status
		output.append("Unchanged Files:");
		output.append("\n");

		output.append("Changed Files:");
		output.append("\n");

		output.append("Untracked Files:");
		output.append("\n");

		output.append("Ignored Files:"); // TODO: these should at some point also make it to the server and not just be an individual client property
		output.append("\n");

		System.out.println(output.toString());
	}

	public void setProperty(String clientProperty, String value) {
		if (!this.checkRepository())
			return;

		this.clientService.init();

		switch (clientProperty.toLowerCase()) {
			case "configuration":
				Configuration configuration = this.eccoService.parseConfigurationString(value);
				this.clientService.setCurrentConfiguration(configuration);
				System.out.println("SUCCESS: SET configuration=" + configuration.getFeatureInstances().size());
				break;
			case "remote":
				try {
					URL remoteUrl = new URL(value);
					this.clientService.setRemote(remoteUrl);
					System.out.println("SUCCESS: SET remote=" + remoteUrl.toString());
				} catch (MalformedURLException e) {
					System.err.println("ERROR: Remote URL is malformed.");
				}
				break;
			case "maxorder":
				int maxOrder = Integer.parseInt(value);
				this.clientService.setMaxOrder(maxOrder);
				System.out.println("SUCCESS: SET maxOrder=" + maxOrder);
				break;
			default:
				System.out.println("ERROR: No property named \"" + clientProperty + "\".");
				break;
		}
	}

	public void getProperty(String clientProperty) {
		if (!this.checkRepository())
			return;

		this.clientService.init();

		switch (clientProperty.toLowerCase()) {
			case "configuration":
				System.out.println("SUCCESS: GET configuration=" + this.eccoService.createConfigurationString(this.clientService.getCurrentConfiguration()));
				break;
			case "remote":
				System.out.println("SUCCESS: GET remote=" + this.clientService.getRemote());
				break;
			case "maxorder":
				System.out.println("SUCCESS: GET maxOrder=" + this.clientService.getMaxOrder());
				break;
			default:
				System.out.println("ERROR: No property named \"" + clientProperty + "\".");
				break;
		}
	}

	public void addFiles(String pathString) {
		if (!this.checkRepository())
			return;

		this.eccoService.init();
		this.clientService.init();

		// NOTE: maybe to this in the client service and not in the CLI.
		try {
			// collect ecco files
			Set<Path> eccoFiles = new HashSet<Path>();
			Files.walk(this.eccoService.getBaseDir()).forEach(eccoFiles::add);

			// go through files in current folder
			Files.walk(Paths.get(pathString)).filter(path -> {
				boolean accept = true;
				// ignore all ecco files
				accept = accept && !eccoFiles.contains(path);
				// ignore directories
				accept = accept && !Files.isDirectory(path);
				// ignore all files on the ignore list
				accept = accept && !this.clientService.getIgnoredFiles().contains(path);
				return accept;
			}).forEach(path -> {
				this.clientService.addTrackedFile(path);
				System.out.println("ADDED: " + path.toString());
			});
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	public void removeFiles(String path) {

	}

	public void ignoreFiles(String path) {

	}

	// server

	public void checkout(String configurationString) {
		if (!this.checkRepository())
			return;

		Configuration configuration = this.eccoService.parseConfigurationString(configurationString);

		this.eccoService.checkout(configuration);
	}

	public void update() {
		if (!this.checkRepository())
			return;

	}

	public void commit() {
		if (!this.checkRepository())
			return;

		this.clientService.init();

		this.commit(this.clientService.getCurrentConfiguration());
	}

	public void commit(String configurationString) {
		if (!this.checkRepository())
			return;

		this.commit(this.eccoService.parseConfigurationString(configurationString));
	}

	public void commit(Configuration configuration) {
		if (!this.checkRepository())
			return;

		this.clientService.init();
		this.serverService.init();

		// Configuration configuration = this.parseConfigurationString(configurationString);
		// Set<Node> nodes = this.reader.read(this.clientService.getTracked());
		// NOTE: the previous two lines are not needed here, the clientService takes care of this when creating the associations

		/*
		 * get associations representing the current client state (i.e. based on the current configuration and the tracked artifacts/files). this will usually be just one single
		 * association representing what used to be a product/variant.
		 */
		List<Association> associations = this.clientService.getAssociations(configuration);

		/*
		 * commit the associations to the server where the usual diffing is done. here the server also has to make sure that the feature references match. and that the modules are
		 * correctly updated.
		 */
		this.serverService.commit(associations);
	}

	// remote

}
