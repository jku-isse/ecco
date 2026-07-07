package at.jku.isse.ecco.cli.command.property;

import at.jku.isse.ecco.cli.command.Command;
import at.jku.isse.ecco.cli.writer.OutWriter;
import at.jku.isse.ecco.cli.writer.SystemWriter;
import at.jku.isse.ecco.service.EccoService;
import net.sourceforge.argparse4j.inf.Namespace;

public class GetCommand implements Command {
    public final static String GET = "get";
    public static final String PROPERTY_KEY = "property";
    private final EccoService eccoService;
    private final OutWriter writer;

    public GetCommand(
            EccoService eccoService,
            OutWriter writer
    ) {
        this.eccoService = eccoService;
        this.writer = writer;
    }

    public GetCommand(EccoService eccoService) {
        this(eccoService, new SystemWriter());
    }

    @Override
    public void run(Namespace namespace) {
        String property = namespace.getString(PROPERTY_KEY);

        eccoService.open();

        switch (property.toLowerCase()) {
            case "basedir":
                writer.println("baseDir=" + eccoService.getBaseDir());
                break;
            default:
                writer.println("ERROR: No property named \"" + property + "\".");
                break;
        }

        eccoService.close();
    }
}
