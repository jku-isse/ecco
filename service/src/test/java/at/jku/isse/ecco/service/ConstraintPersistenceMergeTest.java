package at.jku.isse.ecco.service;

import at.jku.isse.ecco.core.Constraint;
import at.jku.isse.ecco.mining.ConstraintMiner;
import at.jku.isse.ecco.repository.Repository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves accepted {@link Constraint}s travel with fork/pull/push -- the entire point of moving their
 * storage from local {@code ConstraintSuggestionPreferences} into the repository itself -- and that
 * merging two repos that independently accepted the *same* constraint doesn't duplicate it (dedup by
 * natural {@code kind|a|b} id).
 */
public class ConstraintPersistenceMergeTest {

    @Test
    @Timeout(30)
    public void acceptedConstraintsSurviveForkAndMergeWithoutDuplication() throws IOException {
        Path workDir = Files.createTempDirectory("constraint-persistence-merge");
        Path repoADir = workDir.resolve("repoA").resolve(".ecco");
        Path repoBDir = workDir.resolve("repoB").resolve(".ecco");
        Files.createDirectories(repoADir.getParent());
        Files.createDirectories(repoBDir.getParent());

        Path xDir = workDir.resolve("x");
        Files.createDirectories(xDir);
        Files.writeString(xDir.resolve("x.txt"), "x\n");

        EccoService serviceA = new EccoService();
        serviceA.setRepositoryDir(repoADir);
        serviceA.init();
        serviceA.setBaseDir(xDir);
        serviceA.commit("x", "X");
        serviceA.acceptConstraint(ConstraintMiner.Kind.MANDATORY, "X", null);

        // fork B from A -- constraints must travel via subset()+merge(), the same as features/modules
        EccoService serviceB = new EccoService();
        serviceB.setRepositoryDir(repoBDir);
        serviceB.fork(repoADir);

        assertTrue(hasConstraint(serviceB.getRepository(), Constraint.Kind.MANDATORY, "X", null),
                "the constraint accepted in A before the fork must be present in B after forking");

        // B independently re-accepts the SAME constraint (should dedup, not duplicate) plus a
        // genuinely different one
        serviceB.acceptConstraint(ConstraintMiner.Kind.MANDATORY, "X", null);
        serviceB.acceptConstraint(ConstraintMiner.Kind.EXCLUDES, "X", "Y");

        Repository.Op repoAOp = (Repository.Op) serviceA.getRepository();
        Repository.Op repoBOp = (Repository.Op) serviceB.getRepository();

        Repository.Op merged = repoAOp.copy(repoAOp.getEntityFactory());
        merged.merge(repoBOp);

        assertEquals(2, merged.getConstraints().size(),
                "merging must produce the union with no duplication -- MANDATORY(X) accepted "
                        + "independently on both sides must appear exactly once");
        assertTrue(hasConstraint(merged, Constraint.Kind.MANDATORY, "X", null));
        assertTrue(hasConstraint(merged, Constraint.Kind.EXCLUDES, "X", "Y"));

        serviceA.close();
        serviceB.close();
    }

    private static boolean hasConstraint(Repository repository, Constraint.Kind kind, String a, String b) {
        for (Constraint constraint : repository.getConstraints()) {
            if (constraint.getKind() == kind && constraint.getFeatureA().equals(a)
                    && java.util.Objects.equals(constraint.getFeatureB(), b)) {
                return true;
            }
        }
        return false;
    }
}
