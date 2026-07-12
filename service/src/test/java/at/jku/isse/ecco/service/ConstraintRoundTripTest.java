package at.jku.isse.ecco.service;

import at.jku.isse.ecco.core.Constraint;
import at.jku.isse.ecco.mining.ConstraintMiner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConstraintRoundTripTest {

    @Test
    @Timeout(30)
    public void acceptedConstraintSurvivesRealCloseAndReopen() throws IOException {
        Path workDir = Files.createTempDirectory("constraint-round-trip");
        Path repoDir = workDir.resolve(".ecco");

        Path xDir = workDir.resolve("x");
        Files.createDirectories(xDir);
        Files.writeString(xDir.resolve("x.txt"), "x\n");

        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repoDir);
            service.init();
            service.setBaseDir(xDir);
            service.commit("x", "X");
            service.acceptConstraint(ConstraintMiner.Kind.MANDATORY, "X", null);
        }

        // a real serialization round-trip: a brand new EccoService instance, reopening from disk --
        // this is the actual proof storage moved from local Preferences into the repository, since a
        // fresh process/instance has no access to the first instance's in-memory state at all.
        try (EccoService reopened = new EccoService()) {
            reopened.setRepositoryDir(repoDir);
            reopened.open();

            assertEquals(1, reopened.getRepository().getConstraints().size());
            Constraint constraint = reopened.getRepository().getConstraints().iterator().next();
            assertEquals(Constraint.Kind.MANDATORY, constraint.getKind());
            assertEquals("X", constraint.getFeatureA());
        }
    }

    @Test
    @Timeout(30)
    public void acceptUnacceptReAccept_isIdempotentAndReversible() throws IOException {
        Path workDir = Files.createTempDirectory("constraint-undo-roundtrip");
        Path repoDir = workDir.resolve(".ecco");

        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repoDir);
            service.init();

            service.acceptConstraint(ConstraintMiner.Kind.MANDATORY, "Core", null);
            assertTrue(hasConstraint(service, Constraint.Kind.MANDATORY, "Core"));

            // accepting the same constraint again is idempotent -- still exactly one entry
            service.acceptConstraint(ConstraintMiner.Kind.MANDATORY, "Core", null);
            assertEquals(1, service.getRepository().getConstraints().size());

            service.unacceptConstraint(Constraint.Kind.MANDATORY, "Core", null);
            assertFalse(hasConstraint(service, Constraint.Kind.MANDATORY, "Core"),
                    "move back to pending / undo-accept must remove the persisted constraint");

            // undo on an already-absent constraint is a safe no-op, not an error
            service.unacceptConstraint(Constraint.Kind.MANDATORY, "Core", null);
            assertEquals(0, service.getRepository().getConstraints().size());

            // re-accepting after undo works again
            service.acceptConstraint(ConstraintMiner.Kind.MANDATORY, "Core", null);
            assertTrue(hasConstraint(service, Constraint.Kind.MANDATORY, "Core"));
        }
    }

    private static boolean hasConstraint(EccoService service, Constraint.Kind kind, String featureA) {
        for (Constraint constraint : service.getRepository().getConstraints()) {
            if (constraint.getKind() == kind && constraint.getFeatureA().equals(featureA)) return true;
        }
        return false;
    }
}
