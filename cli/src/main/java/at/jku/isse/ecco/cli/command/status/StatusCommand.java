package at.jku.isse.ecco.cli.command.status;

import at.jku.isse.ecco.cli.command.Command;
import at.jku.isse.ecco.cli.writer.OutWriter;
import at.jku.isse.ecco.cli.writer.SystemWriter;
import at.jku.isse.ecco.service.EccoService;
import net.sourceforge.argparse4j.inf.Namespace;

public class StatusCommand implements Command {
    public final static String STATUS = "status";
    private final EccoService eccoService;
    private final OutWriter writer;

    public StatusCommand(
            EccoService eccoService,
            OutWriter writer
    ) {
        this.eccoService = eccoService;
        this.writer = writer;
    }

    public StatusCommand(EccoService eccoService) {
        this(eccoService, new SystemWriter());
    }

    @Override
    public void run(Namespace namespace) {
        eccoService.open();

        writer.println("Repository Directory: " + eccoService.getRepositoryDir());
        writer.println("Base Directory: " + eccoService.getBaseDir());
        writer.println("Current Configuration: " + eccoService.getConfigStringFromFile(eccoService.getBaseDir()));

        eccoService.close();
    }
}
