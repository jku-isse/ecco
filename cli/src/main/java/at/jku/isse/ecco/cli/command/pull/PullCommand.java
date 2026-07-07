package at.jku.isse.ecco.cli.command.pull;

import at.jku.isse.ecco.cli.command.Command;
import at.jku.isse.ecco.service.EccoService;
import net.sourceforge.argparse4j.inf.Namespace;

public class PullCommand implements Command {
    public final static String PULL = "pull";
    public static final String REMOTE_KEY = "remote";
    public static final String FLAG_EXCLUDE = "--exclude";
    public static final String EXCLUDE_KEY = "exclude";
    private final EccoService eccoService;

    public PullCommand(EccoService eccoService) {
        this.eccoService = eccoService;
    }

    @Override
    public void run(Namespace namespace) {
        String remote = namespace.getString(REMOTE_KEY);
        String exclude = namespace.getString(EXCLUDE_KEY);

        eccoService.open();
        eccoService.pull(remote, exclude);
        eccoService.close();
    }
}
