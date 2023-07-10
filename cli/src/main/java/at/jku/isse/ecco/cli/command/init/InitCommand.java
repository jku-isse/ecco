package at.jku.isse.ecco.cli.command.init;

import at.jku.isse.ecco.cli.ProgramConstants;
import at.jku.isse.ecco.cli.command.Command;
import at.jku.isse.ecco.cli.command.CommandRegister;
import at.jku.isse.ecco.service.EccoService;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import net.sourceforge.argparse4j.inf.Subparsers;

public class InitCommand implements Command {
    public final static String INIT = "init";
    private final EccoService eccoService;

    public InitCommand(EccoService eccoService) {
        this.eccoService = eccoService;
    }

    @Override
    public void run(Namespace namespace) {
        this.eccoService.init();
    }

    @Override
    public void register(Subparsers commandParser, CommandRegister commandRegister) {
        commandParser.addParser(INIT).setDefault(ProgramConstants.COMMAND, INIT);
        commandRegister.register(INIT, this);
    }
}
