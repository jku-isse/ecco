package at.jku.isse.ecco.cli.init;

import at.jku.isse.ecco.cli.command.Command;
import at.jku.isse.ecco.service.EccoService;

public class InitCommand implements Command {
    private final EccoService eccoService;

    public InitCommand(EccoService eccoService) {
        this.eccoService = eccoService;
    }

    @Override
    public void run() {
        this.eccoService.init();
    }
}
