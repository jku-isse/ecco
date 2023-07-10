package at.jku.isse.ecco.cli.command.designspace;

import at.jku.isse.designspace.sdk.core.DesignSpace;
import at.jku.isse.ecco.cli.ProgramConstants;
import at.jku.isse.ecco.cli.command.Command;
import at.jku.isse.ecco.cli.command.CommandRegister;
import at.jku.isse.ecco.service.EccoService;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import net.sourceforge.argparse4j.inf.Subparsers;

public class ListWorkspaces implements Command {
    protected static final String WORKSPACE = "workspaces";

    private final EccoService eccoService;

    public ListWorkspaces(EccoService eccoService) {
        this.eccoService = eccoService;
    }

    @Override
    public void run(Namespace namespace) {
        DesignSpace
                .allWorkspaces()
                .forEach(System.out::println);
    }

    @Override
    public void register(Subparsers commandParser, CommandRegister commandRegister) {
        Subparser parser = commandParser.addParser(WORKSPACE).setDefault(ProgramConstants.COMMAND, WORKSPACE);
        commandRegister.register(WORKSPACE, this);

        new CheckoutWorkspace(eccoService).register(parser.addSubparsers(), commandRegister);
    }
}
