package at.jku.isse.ecco.cli.command.commit;

import at.jku.isse.ecco.cli.command.Command;
import at.jku.isse.ecco.service.EccoService;
import net.sourceforge.argparse4j.inf.Namespace;

public class CommitCommand implements Command {
    public final static String COMMIT = "commit";
    public static final String FLAG_CONFIGURATION = "-c";
    private static final String CONFIGURATION_KEY = "c";
    public static final String FLAG_COMMIT_MESSAGE = "-m";
    private static final String COMMIT_MESSAGE_KEY = "m";
    private final EccoService eccoService;

    public CommitCommand(
            EccoService eccoService
    ) {
        this.eccoService = eccoService;
    }

    @Override
    public void run(Namespace namespace) {
        System.out.println(namespace.toString());
        eccoService.open();
        eccoService.commit(namespace.getString(COMMIT_MESSAGE_KEY), namespace.getString(CONFIGURATION_KEY));
        eccoService.close();
    }
}
