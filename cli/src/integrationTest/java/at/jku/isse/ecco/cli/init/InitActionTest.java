package at.jku.isse.ecco.cli.init;

import at.jku.isse.ecco.service.EccoService;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class InitActionTest {
    @BeforeAll
    public static void deleteTestRepositories() throws IOException {
        Files.walk(Path.of("tests"))
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
    }
    @Test
    public void initializesRepository() throws IOException, ArgumentParserException {
        Path testDir = Path.of("tests/initialize-repo-test");
        Path eccoDir = Path.of("tests/initialize-repo-test/.ecco");
        Files.createDirectories(testDir);
        EccoService eccoService = new EccoService(testDir);
        InitAction action = new InitAction(eccoService);

        action.run(null, null, null, null, null, null);

        assertTrue(Files.exists(testDir));
        assertTrue(Files.exists(eccoDir));
    }

}
