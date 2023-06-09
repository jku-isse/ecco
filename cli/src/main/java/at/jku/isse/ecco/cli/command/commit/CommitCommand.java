package at.jku.isse.ecco.cli.command.commit;

import at.jku.isse.ecco.cli.command.Command;
import at.jku.isse.ecco.service.EccoService;
import net.sourceforge.argparse4j.inf.Namespace;

public class CommitCommand implements Command {
    public final static String COMMIT = "commit";
    public static final String FLAG_CONFIGURATION = "-c";
    public static final String FLAG_COMMIT_MESSAGE = "-m";
    private final EccoService eccoService;

    public CommitCommand(
            EccoService eccoService
    ) {
        this.eccoService = eccoService;
    }

    @Override
    public void run(Namespace namespace) {
        eccoService.open();
        eccoService.commit(namespace.getString("m"), namespace.getString("c"));
        eccoService.close();
    }
}
