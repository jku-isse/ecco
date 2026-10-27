package at.jku.isse.ecco.cli.command.fork;

import at.jku.isse.ecco.service.EccoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A fork of a history whose ordered artifacts are nested - a Markdown heading holding its paragraphs -
 * is opened again and checks out what its origin does. merge() copies each association on its own,
 * so the heading held uniquely by one association and shared by others got two copies, each with its
 * own order graph, and graph nodes pointing to the other copy's paragraphs were never stored: the
 * fork could not be opened ("Could not resolve POG node artifact"). Found forking a real repository
 * (README.md of a replayed history); the service's own tests have no Markdown adapter.
 */
public class ForkNestedOrderTest {

    private static final String V1 = "# TaskTracker\n\nA minimal command-line task tracker written in Java.\n";
    private static final String V2 = "# TaskTracker\n\n";

    private record Step(String configuration, String readme) {
    }

    private static final List<Step> HISTORY = List.of(
            new Step("D", V1),
            new Step("D, R", V2));

    @Test
    @Timeout(120)
    public void aForkOfNestedOrderedArtifactsOpensAndChecksOutAsItsOrigin(@TempDir Path tmp) throws Exception {
        Path origin = Files.createDirectories(tmp.resolve("origin"));
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(origin.resolve(".ecco"));
            service.init();
            service.setBaseDir(origin);
            for (Step step : HISTORY) {
                Files.writeString(origin.resolve("README.md"), step.readme());
                service.commit("", step.configuration());
            }
        }

        Path fork = Files.createDirectories(tmp.resolve("fork"));
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(fork.resolve(".ecco"));
            service.fork(origin.resolve(".ecco"), "");
        }

        int i = 0;
        for (Step step : HISTORY) {
            String configuration = step.configuration();
            assertEquals(checkout(origin, configuration, tmp.resolve("co-origin-" + i)), checkout(fork, configuration, tmp.resolve("co-fork-" + i)), configuration);
            i++;
        }
    }

    private static String checkout(Path repository, String configuration, Path dir) throws Exception {
        Files.createDirectories(dir);
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repository.resolve(".ecco"));
            service.open();
            service.setBaseDir(dir);
            service.checkout(configuration);
        }
        return Files.readString(dir.resolve("README.md"));
    }
}
