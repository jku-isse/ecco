package at.jku.isse.ecco.storage.ser.feature;

import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.service.EccoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** A configuration is its set of feature revisions: the order they are listed in is none of its identity. */
public class ConfigurationIdentityTest {

    @Test
    public void theOrderOfTheRevisionsDoesNotMatter() {
        FeatureRevision a = new SerFeature("a", "A").addRevision("1"), b = new SerFeature("b", "B").addRevision("2");
        SerConfiguration ab = new SerConfiguration(new FeatureRevision[]{a, b}), ba = new SerConfiguration(new FeatureRevision[]{b, a});
        assertEquals(ab, ba);
        assertEquals(ab.hashCode(), ba.hashCode());
        assertEquals("A.1, B.2", ba.getConfigurationString(), "printed sorted");
        assertNotEquals(ab, new SerConfiguration(new FeatureRevision[]{a}));
    }

    /** Revision ids that share the 7 characters a configuration is displayed with are different variants. */
    @Test
    @Timeout(60)
    public void variantsAreFoundByTheirRevisionsNotTheirDisplayedIds(@TempDir Path tmp) throws Exception {
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(tmp.resolve(".ecco"));
            service.init();
            Path content = Files.createDirectories(tmp.resolve("content"));
            Files.writeString(content.resolve("f.txt"), "a\n");
            service.setBaseDir(content);
            service.commit("one", "A.12345678aa");
            Files.writeString(content.resolve("f.txt"), "b\n");
            service.commit("two", "A.12345678bb");
            assertEquals(2, service.getRepository().getVariants().size(), "two variants: " + service.getRepository().getVariants());
        }
    }
}
