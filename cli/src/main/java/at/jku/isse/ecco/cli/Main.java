package at.jku.isse.ecco.cli;

import at.jku.isse.ecco.cli.command.Command;
import at.jku.isse.ecco.cli.command.CommandRegister;
import at.jku.isse.ecco.cli.features.ListFeaturesAction;
import at.jku.isse.ecco.cli.init.InitCommand;
import at.jku.isse.ecco.service.EccoService;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparsers;

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
            commandRegister.run(command);
        } catch (ArgumentParserException e) {
            parser.printHelp();
        }
    }

    private static void registerCommands() {
        Subparsers commandParser  = parser.addSubparsers().title(ProgramConstants.COMMAND);

        registerCommand(commandParser, ProgramConstants.INIT, new InitCommand(eccoService));
        registerCommand(commandParser, ProgramConstants.FEATURES, new ListFeaturesAction(eccoService));
    }

    private static void registerCommand(Subparsers commandParser, String commandString, Command command) {
        commandParser.addParser(commandString).setDefault(ProgramConstants.COMMAND, commandString);
        commandRegister.register(commandString, command);
    }

}
