package at.jku.isse.ecco.cli.command.init;

import at.jku.isse.ecco.cli.command.Command;
import at.jku.isse.ecco.service.EccoService;
import net.sourceforge.argparse4j.inf.Namespace;

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
}
