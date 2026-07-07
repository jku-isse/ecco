package at.jku.isse.ecco.cli.command.push;

import at.jku.isse.ecco.cli.command.Command;
import at.jku.isse.ecco.service.EccoService;
import net.sourceforge.argparse4j.inf.Namespace;

public class PushCommand implements Command {
    public final static String PUSH = "push";
    public static final String REMOTE_KEY = "remote";
    public static final String FLAG_EXCLUDE = "--exclude";
    public static final String EXCLUDE_KEY = "exclude";
    private final EccoService eccoService;

    public PushCommand(EccoService eccoService) {
        this.eccoService = eccoService;
    }

    @Override
    public void run(Namespace namespace) {
        String remote = namespace.getString(REMOTE_KEY);
        String exclude = namespace.getString(EXCLUDE_KEY);

        eccoService.open();
        eccoService.push(remote, exclude);
        eccoService.close();
    }
}
