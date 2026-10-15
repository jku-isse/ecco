package at.jku.isse.ecco.service;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.FeatureRevision;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * What a configuration string may say: "-B" is no way to deselect B (it was committed as a new
 * feature named "-B"), a feature has one revision in a configuration ("A.1, A.2" was checked out
 * with both), and spaces around the whole string are no part of it.
 */
public class ConfigurationGrammarTest {

    @Test
    @Timeout(60)
    public void configurationStrings(@TempDir Path tmp) throws Exception {
        Path content = Files.createDirectories(tmp.resolve("content"));
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(tmp.resolve(".ecco"));
            service.init();
            service.setBaseDir(content);
            Files.writeString(content.resolve("a.txt"), "a\n");
            service.commit("a", "A.1, B.1");
            Files.writeString(content.resolve("a.txt"), "a2\n");
            service.commit("a2", "A.2, B.1");

            // "-B" is refused, with how to deselect B
            EccoException minus = assertThrows(EccoException.class, () -> service.parseConfigurationString("A, -B"));
            assertTrue(messages(minus).contains("leave it out"), messages(minus));
            assertThrows(EccoException.class, () -> service.parseConfigurationString("-C"), "also a feature not in the repository");

            // one revision per feature - also of a new feature, and given by name and by [id]
            for (String twice : List.of("A.1, A.2", "A.1, A'", "N.1, N.2", "A.1, [" + featureId(service, "A") + "].2")) {
                EccoException e = assertThrows(EccoException.class, () -> service.parseConfigurationString(twice), twice);
                assertTrue(messages(e).contains("selected twice"), twice + ": " + messages(e));
            }
            // the same revision twice is that revision once
            assertEquals(List.of("A.1"), names(service.parseConfigurationString("A.1, A.1")));
            assertEquals(List.of("A.2"), names(service.parseConfigurationString("A, A")));
            assertEquals(1, service.parseConfigurationString("N, N").getFeatureRevisions().length, "a new feature named twice");

            // spaces around the whole string
            assertEquals(List.of("A.2", "B.1"), names(service.parseConfigurationString("  A, B \n")));
            assertEquals(0, service.parseConfigurationString("   ").getFeatureRevisions().length);
        }
    }

    private static String featureId(EccoService service, String name) {
        return service.getRepository().getFeatures().stream().filter(f -> f.getName().equals(name)).findFirst().orElseThrow().getId();
    }

    private static List<String> names(Configuration configuration) {
        return Arrays.stream(configuration.getFeatureRevisions()).map(r -> r.getFeature().getName() + "." + r.getId())
                .sorted().toList();
    }

    private static String messages(Throwable e) {
        StringBuilder messages = new StringBuilder();
        for (Throwable t = e; t != null; t = t.getCause())
            messages.append(t.getMessage()).append(" / ");
        return messages.toString();
    }
}
