package at.jku.isse.ecco.cli;

import at.jku.isse.ecco.EccoException;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.*;
import net.sourceforge.argparse4j.internal.HelpScreenException;

/**
 * Main class for the CLI. Parses the command line parameters.
 */
public class Main {

	public static final String COMMAND = "command";
	public static final String CONFIGURATION_STRING = "configurationString";
	public static final String EXCLUDED_FEATURE_VERSIONS_STRING = "excludedFeatureVersionsString";
	public static final String REMOTES_COMMAND = "remotesCommand";
	public static final String REMOTE_NAME = "remoteName";
	public static final String REMOTE_URI = "remoteUri";
	public static final String FEATURES_COMMAND = "featuresCommand";
	public static final String FEATURE_NAME = "featureName";
	public static final String TRACES_COMMAND = "featuresCommand";
	public static final String TRACE_ID = "traceId";


	public static void main(String[] args) {

		EccoCli cli = new EccoCli();


		// parse arguments

		ArgumentParser parser = ArgumentParsers.newArgumentParser("ecco").description("ECCO. A Variability-Aware / Feature-Oriented Version Control System.").version("0.1.4");
		parser.addArgument("-v", "--version").action(Arguments.version()).help("show the version");

		parser.addArgument("-r", "--repodir").help("set the repository directory to use");
		parser.addArgument("-b", "--basedir").help("set the base directory to use");

		Subparsers subparsers = parser.addSubparsers().title("COMMANDs").description("List of valid commands.").help("DESCRIPTION").metavar("COMMAND").dest(COMMAND);

		// initialize empty local repository
		Subparser parserInit = subparsers.addParser("init").help("initialize a new repository").description("Initialize a new repository at the current location.");

		// status of local repository and working copy
		Subparser parserStatus = subparsers.addParser("status").help("status of repository").description("Show the status of the repository at the current location.");

		// get a configuration property <name>
		Subparser parserGet = subparsers.addParser("get").help("get the value of a property");
		parserGet.addArgument("name");

		// set a configuration property <name> to <value>
		Subparser parserSet = subparsers.addParser("set").help("set the value of a property");
		parserSet.addArgument("name");
		parserSet.addArgument("value");

		// checkout a configuration from the local repository as working copy (composition)
		Subparser parserCheckout = subparsers.addParser("checkout").help("checkout a configuration").description("Checkout a given configuration from the repository at the current location to the current base directory (property baseDir) of the repository.");
		parserCheckout.addArgument(CONFIGURATION_STRING);

		// commit the working copy as a new configuration into the local repository
		Subparser parserCommit = subparsers.addParser("commit").help("commit a configuration").description("Commit a given configuration to the repository at the current location from its current base directory (property baseDir).");
		parserCommit.addArgument(CONFIGURATION_STRING).nargs("?");

		// clone/fork (cloning remote locally)
		Subparser parserFork = subparsers.addParser("fork").help("fork from another repository");
		parserFork.addArgument(REMOTE_URI);
		parserFork.addArgument(EXCLUDED_FEATURE_VERSIONS_STRING).nargs("?");

		// pull (fetch + update?)
		Subparser parserPull = subparsers.addParser("pull").help("pull from a remote");
		parserPull.addArgument(REMOTE_NAME);
		parserPull.addArgument(EXCLUDED_FEATURE_VERSIONS_STRING).nargs("?");

		// push (push changes in local repository to remote)
		Subparser parserPush = subparsers.addParser("push").help("push to a remote");
		parserPush.addArgument(REMOTE_NAME);
		parserPush.addArgument(EXCLUDED_FEATURE_VERSIONS_STRING).nargs("?");

		// fetch (fetching changes from remote) (fetch features and versions from remote)
		Subparser parserFetch = subparsers.addParser("fetch").help("fetch from a remote").description("Fetch information from a remote, like its available features and versions.");
		parserFetch.addArgument(REMOTE_NAME);

		// remotes
		Subparser parserRemotes = subparsers.addParser("remotes").help("manage remotes").description("Manage available remotes.");
		Subparsers remotesSubparsers = parserRemotes.addSubparsers().title("remotes subcommands").description("valid remotes subcommands").help("additional help").metavar("REMOTES_COMMAND").dest(REMOTES_COMMAND);
		Subparser parserAddRemote = remotesSubparsers.addParser("add").help("Add a new remote.");
		parserAddRemote.addArgument(REMOTE_NAME);
		parserAddRemote.addArgument(REMOTE_URI);
		Subparser parserRemoveRemote = remotesSubparsers.addParser("remove").help("Remove a given remote.");
		parserRemoveRemote.addArgument(REMOTE_NAME);
		Subparser parserListRemotes = remotesSubparsers.addParser("list").help("List all available remotes.");
		Subparser parserShowRemote = remotesSubparsers.addParser("show").help("Show details for the given remote, like the available features and versions.");
		parserShowRemote.addArgument(REMOTE_NAME);

		// features (feature (version) list, etc.)
		Subparser parserFeatures = subparsers.addParser("features").help("manage features").description("Manage available features.");
		Subparsers featuresSubparsers = parserFeatures.addSubparsers().title("features subcommands").description("valid features subcommands").help("additional help").metavar("FEATURES_COMMAND").dest(FEATURES_COMMAND);
		Subparser parserListFeatures = featuresSubparsers.addParser("list").help("List all available features.");
		Subparser parserShowFeature = featuresSubparsers.addParser("show").help("Show details for the given feature, like the description and available versions.");
		parserShowFeature.addArgument(FEATURE_NAME);

		// traces (association list, artifact tree print)
		Subparser parserTraces = subparsers.addParser("traces").help("manage traces").description("Manage available traces.");
		Subparsers tracesSubparsers = parserTraces.addSubparsers().title("traces subcommands").description("valid traces subcommands").help("additional help").metavar("TRACES_COMMAND").dest(TRACES_COMMAND);
		Subparser parserListTraces = tracesSubparsers.addParser("list").help("List all available features.");
		Subparser parserShowTraces = tracesSubparsers.addParser("show").help("Show details for the given feature, like the description and available versions.");
		parserShowTraces.addArgument(TRACE_ID);

		// dependency graph (export as gml)
		Subparser parserDG = subparsers.addParser("dg").aliases("dependencyGraph").help("dependency graph").description("Show the dependency graph of the traces stored in the repository at the current location.");

		// server
		Subparser parserServer = subparsers.addParser("server").help("start a server").description("Start a server on the given port.");
		parserServer.addArgument(REMOTE_NAME);


		// TODO: update (update working copy)?


		try {
			Namespace res = parser.parseArgs(args);
			System.out.println(res);

			if (res.getString("repodir") != null) {
				cli.setRepoDir(res.getString("repodir"));
			}
			if (res.getString("basedir") != null) {
				cli.setBaseDir(res.getString("basedir"));
			}

			switch (res.getString(COMMAND)) {
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
					cli.checkout(res.getString(CONFIGURATION_STRING));
					break;
				case "commit":
					if (res.getString(CONFIGURATION_STRING) != null)
						cli.commit(res.getString(CONFIGURATION_STRING));
					else
						cli.commit();
					break;
				case "fork":
					if (res.getString(EXCLUDED_FEATURE_VERSIONS_STRING) != null)
						cli.fork(res.getString(REMOTE_URI), res.getString(EXCLUDED_FEATURE_VERSIONS_STRING));
					else
						cli.fork(res.getString(REMOTE_URI));
					break;
				case "pull":
					if (res.getString(EXCLUDED_FEATURE_VERSIONS_STRING) != null)
						cli.pull(res.getString(REMOTE_NAME), res.getString(EXCLUDED_FEATURE_VERSIONS_STRING));
					else
						cli.pull(res.getString(REMOTE_NAME));
					break;
				case "push":
					if (res.getString(EXCLUDED_FEATURE_VERSIONS_STRING) != null)
						cli.push(res.getString(REMOTE_NAME), res.getString(EXCLUDED_FEATURE_VERSIONS_STRING));
					else
						cli.push(res.getString(REMOTE_NAME));
					break;
				case "fetch":
					cli.fetch(res.getString(REMOTE_NAME));
					break;
				case "remotes":
					if (res.getString(REMOTES_COMMAND) != null) {
						switch (res.getString(REMOTES_COMMAND)) {
							case "add":
								cli.addRemote(res.getString(REMOTE_NAME), res.getString(REMOTE_URI));
								break;
							case "remove":
								cli.removeRemote(res.getString(REMOTE_NAME));
								break;
							case "list":
								cli.listRemotes();
								break;
							case "show":
								cli.showRemote(res.getString(REMOTE_NAME));
								break;
						}
					}
					break;
				case "features":
					if (res.getString(FEATURES_COMMAND) != null) {
						switch (res.getString(FEATURES_COMMAND)) {
							case "list":
								cli.listFeatures();
								break;
							case "show":
								cli.showFeature(res.getString(FEATURE_NAME));
								break;
						}
					}
					break;
				case "traces":
					if (res.getString(TRACES_COMMAND) != null) {
						switch (res.getString(TRACES_COMMAND)) {
							case "list":
								cli.listTraces();
								break;
							case "show":
								cli.showTraces(res.getString(TRACE_ID));
								break;
						}
					}
					break;
				case "dg":
					cli.showDependencyGraph();
					break;
				case "server":
					cli.startServer(Integer.parseInt(res.getString("port")));
					break;
			}

		} catch (HelpScreenException e) {
			parser.handleError(e);
		} catch (ArgumentParserException e) {
			System.err.println("ERROR: " + e.getMessage());
			System.err.flush();
			System.exit(1);
		} catch (EccoException e) {
			System.err.println("ERROR: " + e.getMessage());
			Throwable cause = e.getCause();
			while (cause != null) {
				System.err.println("ERROR: " + cause.getMessage());
				cause = cause.getCause();
			}
			System.err.flush();
			System.exit(2);
		}

	}

}
