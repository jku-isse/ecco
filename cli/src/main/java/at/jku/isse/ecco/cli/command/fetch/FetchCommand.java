package at.jku.isse.ecco.cli.command.fetch;

import at.jku.isse.ecco.cli.command.Command;
import at.jku.isse.ecco.service.EccoService;
import net.sourceforge.argparse4j.inf.Namespace;

public class FetchCommand implements Command {
    public final static String FETCH = "fetch";
    public static final String REMOTE_KEY = "remote";
    private final EccoService eccoService;

    public FetchCommand(EccoService eccoService) {
        this.eccoService = eccoService;
    }

    @Override
    public void run(Namespace namespace) {
        String remote = namespace.getString(REMOTE_KEY);

        eccoService.open();
        eccoService.fetch(remote);
        eccoService.close();
    }
}
