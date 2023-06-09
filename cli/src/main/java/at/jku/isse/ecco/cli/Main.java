package at.jku.isse.ecco.cli;

import at.jku.isse.ecco.cli.command.Command;
import at.jku.isse.ecco.cli.command.CommandRegister;
import at.jku.isse.ecco.cli.command.adapters.ListAdaptersCommand;
import at.jku.isse.ecco.cli.command.commit.CommitCommand;
import at.jku.isse.ecco.cli.command.features.ListFeaturesCommand;
import at.jku.isse.ecco.cli.command.init.InitCommand;
import at.jku.isse.ecco.service.EccoService;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.*;

import java.nio.file.Path;

/**
 * Main class for the CLI. Parses the command line parameters.
 */
public class Main {

    public static final ArgumentParser parser = ArgumentParsers.newFor(ProgramConstants.ECCO).build();
    private static final CommandRegister commandRegister = new CommandRegister();
    private static final EccoService eccoService = new EccoService(Path.of("."));

    public static void main(String[] args) {
        registerCommands();

        try {
            Namespace namespace = parser.parseArgs(args);
            String command = namespace.getString("command");
            commandRegister.run(command, namespace);
        } catch (ArgumentParserException e) {
            parser.printHelp();
        }
    }

    private static void registerCommands() {
        Subparsers commandParser  = parser.addSubparsers().title(ProgramConstants.COMMAND);

        registerSimpleCommand(commandParser, InitCommand.INIT, new InitCommand(eccoService));
        registerSimpleCommand(commandParser, ListFeaturesCommand.FEATURES, new ListFeaturesCommand(eccoService));
        registerSimpleCommand(commandParser, ListAdaptersCommand.ADAPTERS, new ListAdaptersCommand(eccoService));

        registerCommitCommand(commandParser);
    }

    private static void registerCommitCommand(Subparsers commandParser) {
        Subparser commitCommandParser = commandParser.addParser(CommitCommand.COMMIT);
        commitCommandParser.setDefault(ProgramConstants.COMMAND, CommitCommand.COMMIT);
        commitCommandParser.addArgument(CommitCommand.FLAG_CONFIGURATION).required(true);
        commitCommandParser.addArgument(CommitCommand.FLAG_COMMIT_MESSAGE).setDefault("").required(false);
        commandRegister.register(CommitCommand.COMMIT, new CommitCommand(eccoService));
    }

    private static void registerSimpleCommand(Subparsers commandParser, String commandString, Command command) {
        commandParser.addParser(commandString).setDefault(ProgramConstants.COMMAND, commandString);
        commandRegister.register(commandString, command);
    }

}
