package at.jku.isse.ecco.cli.command.order;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.cli.ProgramConstants;
import at.jku.isse.ecco.cli.command.Command;
import at.jku.isse.ecco.cli.writer.OutWriter;
import at.jku.isse.ecco.cli.writer.SystemWriter;
import at.jku.isse.ecco.service.EccoService;
import net.sourceforge.argparse4j.inf.Namespace;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Records the order of checked-out files as they are now, without a commit: after a checkout whose
 * .warnings has an ORDER line, put the file's content in the right order, then run this (see
 * EccoService#recordOrderOfFiles). A commit would record the whole checkout as a variant of its
 * configuration instead.
 */
public class OrderCommand implements Command {
    public static final String ORDER = "order";
    public static final String FILES_KEY = "files";

    private final EccoService eccoService;
    private final OutWriter writer;

    public OrderCommand(EccoService eccoService, OutWriter writer) {
        this.eccoService = eccoService;
        this.writer = writer;
    }

    public OrderCommand(EccoService eccoService) {
        this(eccoService, new SystemWriter());
    }

    @Override
    public void run(Namespace namespace) {
        List<String> arguments = namespace.getList(FILES_KEY);
        Object startDir = namespace.get(ProgramConstants.START_DIR);
        Path start = realPath(startDir instanceof Path path ? path : Path.of(""));

        eccoService.open();
        try {
            // the files are given relative to where the command runs; the service wants them
            // relative to the working directory, the repository's directory
            // both real paths, or e.g. macOS's /var -> /private/var link makes every file look outside
            Path base = realPath(eccoService.getBaseDir());
            List<Path> files = new ArrayList<>();
            for (String argument : arguments) {
                Path file = base.relativize(start.resolve(argument).normalize());
                if (file.startsWith(".."))
                    throw new EccoException(argument + " is outside the working directory " + base + ".");
                files.add(file);
            }
            int added = eccoService.recordOrderOfFiles(files);
            String names = String.join(", ", arguments);
            writer.println(added == 0
                    ? "The order of " + names + " was already recorded."
                    : "Recorded the order of " + names + " (" + added + " new precedence" + (added == 1 ? "" : "s") + ").");
        } finally {
            eccoService.close();
        }
    }

    private static Path realPath(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException e) {
            return path.toAbsolutePath().normalize();
        }
    }
}
