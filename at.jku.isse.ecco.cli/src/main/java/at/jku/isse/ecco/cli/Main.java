package at.jku.isse.ecco.cli;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.EccoService;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.*;
import net.sourceforge.argparse4j.internal.HelpScreenException;

public class Main {

	public static void main(String[] args) {

		EccoService eccoService = new EccoService();

		CLI cli = new CLI(eccoService);


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
		} catch (EccoException e) {
			System.err.println("ERROR: " + e.getMessage());
		}

	}

}
