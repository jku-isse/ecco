package at.jku.isse.ecco.cli.command.fork;

import at.jku.isse.ecco.cli.command.Command;
import at.jku.isse.ecco.cli.writer.OutWriter;
import at.jku.isse.ecco.cli.writer.SystemWriter;
import at.jku.isse.ecco.service.EccoService;
import net.sourceforge.argparse4j.inf.Namespace;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ForkCommand implements Command {
    public final static String FORK = "fork";
    public static final String REMOTE_KEY = "remote";
    public static final String FLAG_EXCLUDE = "--exclude";
    public static final String EXCLUDE_KEY = "exclude";
    private final EccoService eccoService;
    private final OutWriter writer;

    public ForkCommand(
            EccoService eccoService,
            OutWriter writer
    ) {
        this.eccoService = eccoService;
        this.writer = writer;
    }

    public ForkCommand(EccoService eccoService) {
        this(eccoService, new SystemWriter());
    }

    @Override
    public void run(Namespace namespace) {
        String remoteUriString = namespace.getString(REMOTE_KEY);
        String exclude = namespace.getString(EXCLUDE_KEY);

        if (remoteUriString.matches("[a-zA-Z]+:[0-9]+")) {
            String[] pair = remoteUriString.split(":");
            String hostname = pair[0];
            int port = Integer.parseInt(pair[1]);
            eccoService.fork(hostname, port, exclude);
            eccoService.close();
            return;
        }

        Path path;
        try {
            path = Paths.get(remoteUriString);
        } catch (InvalidPathException ignored) {
            path = null;
        }

        if (path != null) {
            eccoService.fork(path, exclude);
            eccoService.close();
        } else {
            writer.println("ERROR: Invalid remote address provided.");
        }
    }
}
