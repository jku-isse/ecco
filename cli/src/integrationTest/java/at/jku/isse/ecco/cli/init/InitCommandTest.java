package at.jku.isse.ecco.cli.init;

import at.jku.isse.ecco.cli.command.init.InitCommand;
import at.jku.isse.ecco.service.EccoService;
import net.sourceforge.argparse4j.inf.Namespace;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class InitCommandTest {
    @BeforeAll
    public static void deleteTestRepositories() throws IOException {
        //noinspection resource,ResultOfMethodCallIgnored
        Files.walk(Path.of("tests"))
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
    }
    @Test
    public void initializesRepository() throws IOException {
        Path testDir = Path.of("tests/initialize-repo-test");
        Path eccoDir = Path.of("tests/initialize-repo-test/.ecco");
        Files.createDirectories(testDir);
        EccoService eccoService = new EccoService(testDir);
        InitCommand action = new InitCommand(eccoService);

        action.run(new Namespace(Map.of()));

        assertTrue(Files.exists(testDir));
        assertTrue(Files.exists(eccoDir));
    }

}
