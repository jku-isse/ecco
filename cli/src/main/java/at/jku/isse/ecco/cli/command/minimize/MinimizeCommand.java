package at.jku.isse.ecco.cli.command.minimize;

import at.jku.isse.ecco.cli.command.Command;
import at.jku.isse.ecco.cli.writer.OutWriter;
import at.jku.isse.ecco.cli.writer.SystemWriter;
import at.jku.isse.ecco.service.EccoService;
import net.sourceforge.argparse4j.inf.Namespace;

/**
 * Stores, for every association, a minimized condition checkout can use with
 * {@code checkout --minimized} (see EccoService#minimizeConditionsForCheckout). Uses the accepted
 * constraints that are still trusted when re-mined; a later commit that changes what a condition
 * was computed from makes it unused until this runs again.
 */
public class MinimizeCommand implements Command {
    public static final String MINIMIZE = "minimize";

    private final EccoService eccoService;
    private final OutWriter writer;

    public MinimizeCommand(EccoService eccoService, OutWriter writer) {
        this.eccoService = eccoService;
        this.writer = writer;
    }

    public MinimizeCommand(EccoService eccoService) {
        this(eccoService, new SystemWriter());
    }

    @Override
    public void run(Namespace namespace) {
        eccoService.open();
        try {
            int stored = eccoService.minimizeConditionsForCheckout();
            writer.println("Stored minimized conditions for " + stored + " associations; use them with: checkout --minimized -c <configuration>");
        } finally {
            eccoService.close();
        }
    }
}
