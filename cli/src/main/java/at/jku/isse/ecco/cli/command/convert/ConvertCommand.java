package at.jku.isse.ecco.cli.command.convert;

import at.jku.isse.ecco.cli.command.Command;
import at.jku.isse.ecco.cli.writer.OutWriter;
import at.jku.isse.ecco.cli.writer.SystemWriter;
import at.jku.isse.ecco.service.EccoService;
import net.sourceforge.argparse4j.inf.Namespace;

/** Writes the whole repository again in the current storage layout (see EccoService#convertToCurrentFormat). */
public class ConvertCommand implements Command {
    public static final String CONVERT = "convert";

    private final EccoService eccoService;
    private final OutWriter writer;

    public ConvertCommand(EccoService eccoService, OutWriter writer) {
        this.eccoService = eccoService;
        this.writer = writer;
    }

    public ConvertCommand(EccoService eccoService) {
        this(eccoService, new SystemWriter());
    }

    @Override
    public void run(Namespace namespace) {
        Command.withOpenRepository(eccoService, () -> {
            int written = eccoService.convertToCurrentFormat();
            writer.println("Wrote " + written + " associations in the current storage layout.");
        });
    }
}
