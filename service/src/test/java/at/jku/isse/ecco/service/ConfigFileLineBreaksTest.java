package at.jku.isse.ecco.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A .config file with one feature per line (and blank lines in between) was returned verbatim, so
 * the Commit Multiple Versions dialog showed every configuration - and its default commit message -
 * spread over several ragged lines. The line breaks are now collapsed into one "a.1, b.1" line.
 */
public class ConfigFileLineBreaksTest {

    @Test
    @Timeout(30)
    public void lineBreaksInConfigFileAreCollapsed() throws Exception {
        Path dir = Files.createTempDirectory("config-line-breaks");
        Files.writeString(dir.resolve(EccoService.CONFIG_FILE_NAME),
                "header.1\n\n, scorePartOne.1\r\n\n,partoneSopOneNotes.1,  partoneSopOneArticulations.1\n");
        try (EccoService service = new EccoService()) {
            assertEquals("header.1, scorePartOne.1, partoneSopOneNotes.1, partoneSopOneArticulations.1",
                    service.getConfigStringFromFile(dir));
        }
    }
}
