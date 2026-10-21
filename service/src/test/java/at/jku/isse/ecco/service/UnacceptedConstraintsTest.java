package at.jku.isse.ecco.service;

import at.jku.isse.ecco.core.Constraint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A mined constraint is a suggestion until it is accepted (FR-N2): Base is in every committed
 * configuration, so MANDATORY Base is mined as a hard rule - yet a configuration without Base
 * violates nothing until it is accepted, and then it does.
 */
public class UnacceptedConstraintsTest {

    @Test
    @Timeout(60)
    public void aMinedConstraintAppliesOnlyOnceAccepted(@TempDir Path tmp) throws Exception {
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(tmp.resolve(".ecco"));
            service.init();
            String[] configurations = {"Base, A", "Base, A", "Base, B", "Base, B", "Base, A, B"};
            for (int i = 0; i < configurations.length; i++) {
                Path dir = Files.createDirectories(tmp.resolve("v" + i));
                Files.writeString(dir.resolve("f" + i + ".txt"), i + "\n");
                service.setBaseDir(dir);
                service.commit("v" + i, configurations[i]);
            }

            assertTrue(service.acceptedSuggestions(service.getRepository()).isEmpty(), "nothing accepted, nothing trusted");
            assertEquals(java.util.List.of(), service.checkConstraintViolations(service.parseConfigurationString("A")),
                    "a mined but unaccepted MANDATORY Base is not applied");

            service.acceptConstraint(Constraint.Kind.MANDATORY, "Base", null);
            assertFalse(service.checkConstraintViolations(service.parseConfigurationString("A")).isEmpty(),
                    "accepted, it is applied");
        }
    }
}
