package at.jku.isse.ecco.cli.init;

import at.jku.isse.ecco.cli.command.init.InitCommand;
import at.jku.isse.ecco.service.EccoService;
import net.sourceforge.argparse4j.inf.Namespace;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class InitCommandTest {

    // what Main does: the command line has no JavaFX, so adapters must not bind their viewers
    @BeforeAll
    public static void headless() {
        System.setProperty("ecco.headless", "true");
    }

    @Test
    public void initializesRepository(@TempDir Path tmp) throws Exception {
        Path testDir = Files.createDirectories(tmp.resolve("initialize-repo-test"));
        try (EccoService eccoService = new EccoService(testDir)) {
            new InitCommand(eccoService).run(new Namespace(Map.of()));
        }

        assertTrue(Files.isDirectory(testDir.resolve(".ecco")));
        assertTrue(Files.exists(testDir.resolve(".ecco").resolve("id")), "the repository has data, so it can be opened");
    }
}
