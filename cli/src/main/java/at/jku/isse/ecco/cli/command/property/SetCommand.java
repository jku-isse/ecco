package at.jku.isse.ecco.cli.command.property;

import at.jku.isse.ecco.cli.command.Command;
import at.jku.isse.ecco.cli.writer.OutWriter;
import at.jku.isse.ecco.cli.writer.SystemWriter;
import at.jku.isse.ecco.service.EccoService;
import net.sourceforge.argparse4j.inf.Namespace;

import java.nio.file.Path;
import java.nio.file.Paths;

public class SetCommand implements Command {
    public final static String SET = "set";
    public static final String PROPERTY_KEY = "property";
    public static final String VALUE_KEY = "value";
    private final EccoService eccoService;
    private final OutWriter writer;

    public SetCommand(
            EccoService eccoService,
            OutWriter writer
    ) {
        this.eccoService = eccoService;
        this.writer = writer;
    }

    public SetCommand(EccoService eccoService) {
        this(eccoService, new SystemWriter());
    }

    @Override
    public void run(Namespace namespace) {
        String property = namespace.getString(PROPERTY_KEY);
        String value = namespace.getString(VALUE_KEY);

        eccoService.open();

        switch (property.toLowerCase()) {
            case "basedir":
                Path baseDir = Paths.get(value);
                eccoService.setBaseDir(baseDir);
                writer.println("baseDir=" + baseDir);
                break;
            default:
                writer.println("ERROR: No property named \"" + property + "\".");
                break;
        }

        eccoService.close();
    }
}
