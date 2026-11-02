package at.jku.isse.ecco.service;

import at.jku.isse.ecco.core.Checkout;
import at.jku.isse.ecco.core.Commit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A feature in several revisions: the lines all of them hold are in one association, whose
 * condition lists the feature with each of its revisions - alternatives, one of them holding is
 * enough (Condition#holds). The surplus check took each revision the configuration lacks for a
 * module not explained, so every checkout with the feature - also of the very configuration last
 * committed - warned "SURPLUS: S" once per other revision (FeatureRevisionsEndToEndTest found it).
 */
public class SurplusAcrossRevisionsTest {

    @Test
    @Timeout(60)
    public void aRevisionOfTheFeatureExplainsTheLinesAllItsRevisionsHold(@TempDir Path tmp) throws Exception {
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(Files.createDirectories(tmp.resolve("repo")).resolve(".ecco"));
            service.init();
            commit(service, tmp.resolve("v0"), "BASE", Map.of("a.txt", "a\n"));
            Commit first = commit(service, tmp.resolve("v1"), "BASE, S", Map.of("a.txt", "a\n", "s.txt", "x\n"));
            commit(service, tmp.resolve("v2"), "BASE, S'", Map.of("a.txt", "a\n", "s.txt", "x\ny\n"));
            commit(service, tmp.resolve("v3"), "BASE, S'", Map.of("a.txt", "a\n", "s.txt", "x\ny\nz\n"));

            // the latest revision, and the first: each explains the line x all three hold
            for (String configuration : new String[]{"BASE, S", first.getConfiguration().getConfigurationString()}) {
                Path out = Files.createDirectories(tmp.resolve("out-" + configuration.hashCode()));
                service.setBaseDir(out);
                Checkout checkout = service.checkout(configuration);
                assertEquals(Map.of(), checkout.getSurplusModules(), configuration);
                assertEquals("", Files.readString(out.resolve(EccoService.WARNINGS_FILE_NAME)), configuration);
            }
        }
    }

    private static Commit commit(EccoService service, Path dir, String configuration, Map<String, String> files) throws Exception {
        Files.createDirectories(dir);
        for (Map.Entry<String, String> file : files.entrySet())
            Files.writeString(dir.resolve(file.getKey()), file.getValue());
        service.setBaseDir(dir);
        return service.commit(configuration, configuration);
    }
}
