package at.jku.isse.ecco.cli;

import at.jku.isse.ecco.plugin.CoreModule;
import at.jku.isse.ecco.plugin.PerstModule;
import at.jku.isse.ecco.plugin.artifact.ArtifactPlugin;
import at.jku.isse.ecco.EccoService;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.name.Names;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.*;
import net.sourceforge.argparse4j.internal.HelpScreenException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

// TODO: use an EccoService that takes care of the injection stuff and these directories. They can, for example, be passed as arguments during the constructor call which then takes care of the bindings and its own injections (which would be the other services).

public class Main {

	private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

	public static final String BASE_DIR = ".";
	public static final String REPOSITORY_DIR = ".ecco/";
	public static final String CONNECTION_STRING = Paths.get(REPOSITORY_DIR, "ecco.db").toString();
	public static final String CLIENT_CONNECTION_STRING = Paths.get(REPOSITORY_DIR, "client.db").toString();
	public static final String SERVER_CONNECTION_STRING = Paths.get(REPOSITORY_DIR, "server.db").toString();

	public static void main(String[] args) {

		LOGGER.debug("CONNECTION_STRING: " + CONNECTION_STRING);

		// default properties
		Properties properties = new Properties();
		// properties.setProperty("module.dal", "at.jku.isse.ecco.perst");
		properties.setProperty("baseDir", BASE_DIR);
		properties.setProperty("repositoryDir", REPOSITORY_DIR);
		properties.setProperty("connectionString", CONNECTION_STRING);
		properties.setProperty("clientConnectionString", CLIENT_CONNECTION_STRING);
		properties.setProperty("serverConnectionString", SERVER_CONNECTION_STRING);

		// create modules
		final Module connectionStringModule = new AbstractModule() {
			@Override
			protected void configure() {
				bind(String.class).annotatedWith(Names.named("connectionString")).toInstance(properties.getProperty("connectionString"));
				bind(String.class).annotatedWith(Names.named("clientConnectionString")).toInstance(properties.getProperty("clientConnectionString"));
				bind(String.class).annotatedWith(Names.named("serverConnectionString")).toInstance(properties.getProperty("serverConnectionString"));
			}
		};
		final Module repositoryDirModule = new AbstractModule() {
			@Override
			protected void configure() {
				bind(String.class).annotatedWith(Names.named("baseDir")).toInstance(properties.getProperty("baseDir"));
				bind(String.class).annotatedWith(Names.named("repositoryDir")).toInstance(properties.getProperty("repositoryDir"));
			}
		};
		List<Module> modules = new ArrayList<Module>();
		for (ArtifactPlugin ap : ArtifactPlugin.getArtifactPlugins()) {
			modules.add(ap.getModule());
		}
		LOGGER.debug("ARTIFACT PLUGINS: " + modules.toString());
		modules.addAll(Arrays.asList(new CoreModule(), new PerstModule(), repositoryDirModule, connectionStringModule));

		// create injector
		Injector injector = Guice.createInjector(modules);

		// create CLI
		// CLI cli = new CLI();
		CLI cli = injector.getInstance(CLI.class);

		EccoService eccoService = new EccoService();


		// # PICK APART ARGUMENTS ... #########################################
		// TODO: parse arguments


		ArgumentParser parser = ArgumentParsers.newArgumentParser("ecco").description("ECCO Description.");

		Subparsers subparsers = parser.addSubparsers().title("subcommands").description("valid subcommands").help("additional help").metavar("COMMAND").dest("command");

		// initialize empty local repository
		Subparser parserInit = subparsers.addParser("init").help("init help");

		// status of local repository and working copy
		Subparser parserStatus = subparsers.addParser("status").help("status help");

		// get a configuration property <name>
		Subparser parserGet = subparsers.addParser("get").help("get help");
		parserGet.addArgument("name");

		// set a configuration property <name> to <value>
		Subparser parserSet = subparsers.addParser("set").help("set help");
		parserSet.addArgument("name");
		parserSet.addArgument("value");

		// checkout a configuration from the local repository as working copy (composition)
		Subparser parserCheckout = subparsers.addParser("checkout").help("checkout help");
		parserCheckout.addArgument("configurationString");

		// commit the working copy as a new configuration into the local repository
		Subparser parserCommit = subparsers.addParser("commit").help("commit help");
		parserCommit.addArgument("configurationString").required(false);

		// TODO: clone (cloning remote locally), fetch (fetching changes from remote), update (update working copy), pull (fetch + update), push (push changes in local repository to remote), ...

		try {
			Namespace res = parser.parseArgs(args);
			System.out.println(res);

			switch (res.getString("command")) {
				case "init":
					cli.init();
					break;
				case "status":
					cli.status();
					break;
				case "get":
					cli.getProperty(res.getString("name"));
					break;
				case "set":
					cli.setProperty(res.getString("name"), res.getString("value"));
					break;
				case "checkout":
					cli.checkout(res.getString("configurationString"));
					break;
				case "commit":
					if (res.getString("configurationString") != null)
						cli.commit(res.getString("configurationString"));
					break;
			}

		} catch (HelpScreenException e) {
			parser.handleError(e);
		} catch (ArgumentParserException e) {
			System.err.println("ERROR: " + e.getMessage());
		}

	}

}
