package at.jku.isse.ecco.cli;

import at.jku.isse.ecco.cli.command.Command;
import at.jku.isse.ecco.cli.command.CommandRegister;
import at.jku.isse.ecco.cli.command.adapters.ListAdaptersCommand;
import at.jku.isse.ecco.cli.command.checkout.CheckoutCommand;
import at.jku.isse.ecco.cli.command.commit.CommitCommand;
import at.jku.isse.ecco.cli.command.designspace.ListWorkspaces;
import at.jku.isse.ecco.cli.command.features.ListFeaturesCommand;
import at.jku.isse.ecco.cli.command.init.InitCommand;
import at.jku.isse.ecco.service.EccoService;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Main class for the CLI. Parses the command line parameters.
 */
public class Main {

    public static final ArgumentParser parser = ArgumentParsers.newFor(ProgramConstants.ECCO).build();
    private static final CommandRegister commandRegister = new CommandRegister();
    private static final EccoService eccoService = new EccoService(Path.of("."));

    public static void main(String[] args) {
        List<Command> commandList = new ArrayList<>(){{
            add(new InitCommand(eccoService));
            add(new ListAdaptersCommand(eccoService));
            add(new ListFeaturesCommand(eccoService));
            add(new CommitCommand(eccoService));
            add(new CheckoutCommand(eccoService));
            add(new ListWorkspaces(eccoService));
        }};

        for (Command command: commandList) {
            command.register(parser.addSubparsers(), commandRegister);
        }

        try {
            Namespace namespace = parser.parseArgs(args);
            String command = namespace.getString("command");
            commandRegister.run(command, namespace);
        } catch (ArgumentParserException e) {
            parser.printHelp();
        }
    }
}
