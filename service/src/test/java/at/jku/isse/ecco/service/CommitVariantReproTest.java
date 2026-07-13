package at.jku.isse.ecco.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CommitVariantReproTest {

    @Test
    @Timeout(30)
    public void commitCreatesRetrievableVariant() throws IOException {
        Path workDir = Files.createTempDirectory("commit-variant-repro");
        Path repoDir = workDir.resolve(".ecco");

        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repoDir);
            service.init();

            Path p = workDir.resolve("core");
            Files.createDirectories(p);
            Files.writeString(p.resolve("core.txt"), "core\n");
            service.setBaseDir(p);
            service.commit("first commit", "Core");

            ArrayList<at.jku.isse.ecco.core.Variant> variants = service.getRepository().getVariants();
            System.out.println("Variant count after commit: " + variants.size());
            for (at.jku.isse.ecco.core.Variant v : variants) {
                System.out.println("  variant id=" + v.getId() + " name=" + v.getName() + " config=" + v.getConfiguration());
            }
            assertEquals(1, variants.size(), "exactly one variant should have been auto-created");
            assertTrue(variants.get(0).getConfiguration().equals(service.parseConfigurationString("Core")));
        }
    }
}
