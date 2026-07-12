package at.jku.isse.ecco.service;

import at.jku.isse.ecco.core.Constraint;
import at.jku.isse.ecco.mining.ConfigurationBridge;
import at.jku.isse.ecco.mining.ConstraintMiner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the single most important safety property of the persisted-{@link Constraint} feature (see
 * {@code CONSTRAINT_MINING_DESIGN.md}'s "Crucial safety rule" and
 * {@link EccoService#acceptedSuggestions}'s javadoc): a constraint accepted while it mined as hard
 * must stop being trusted the moment a later commit contradicts it -- moving accepted-constraint
 * storage from local {@code ConstraintSuggestionPreferences} into the repository itself must not
 * weaken this. Acceptance is durable (the persisted {@link Constraint} is never silently deleted just
 * because it turned out to be wrong); *trust* is not (it's re-derived fresh, every call, from current
 * data).
 */
public class AcceptedConstraintStaleReMineTest {

    @Test
    @Timeout(30)
    public void staleAcceptedConstraint_isExcludedFromTrustedSetOnceContradicted() throws IOException {
        Path workDir = Files.createTempDirectory("accepted-constraint-stale-remine");
        Path repoDir = workDir.resolve(".ecco");

        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repoDir);
            service.init();

            // Core and Extra never co-occur (4 witnesses each, minWitness=4) -- EXCLUDES(Core,Extra)
            // mines hard.
            for (int i = 1; i <= 4; i++) {
                Path p = workDir.resolve("core-" + i);
                Files.createDirectories(p);
                Files.writeString(p.resolve("core.txt"), "core " + i + "\n");
                service.setBaseDir(p);
                service.commit("core " + i, "Core");
            }
            for (int i = 1; i <= 4; i++) {
                Path p = workDir.resolve("extra-" + i);
                Files.createDirectories(p);
                Files.writeString(p.resolve("extra.txt"), "extra " + i + "\n");
                service.setBaseDir(p);
                service.commit("extra " + i, "Extra");
            }

            List<Set<String>> configs = ConfigurationBridge.readConfigurations(service);
            List<ConstraintMiner.Suggestion> mined = new ConstraintMiner(4, 0.9, null).mine(configs);
            ConstraintMiner.Suggestion excludesCoreExtra = null;
            for (ConstraintMiner.Suggestion suggestion : mined) {
                if (suggestion.kind == ConstraintMiner.Kind.EXCLUDES
                        && ((suggestion.a.equals("Core") && "Extra".equals(suggestion.b))
                        || (suggestion.a.equals("Extra") && "Core".equals(suggestion.b)))) {
                    excludesCoreExtra = suggestion;
                }
            }
            org.junit.jupiter.api.Assertions.assertNotNull(excludesCoreExtra,
                    "sanity check: EXCLUDES(Core,Extra) should have been mined, since they never co-occur");
            assertTrue(excludesCoreExtra.isHard(), "sanity check: should be hard, no counterexample exists yet");

            service.acceptConstraint(excludesCoreExtra);

            // before any contradiction: the accepted constraint is trusted
            List<ConstraintMiner.Suggestion> trustedBefore = service.acceptedSuggestions(service.getRepository());
            assertTrue(trustedBefore.stream().anyMatch(s -> s.kind == ConstraintMiner.Kind.EXCLUDES
                            && ((s.a.equals("Core") && "Extra".equals(s.b)) || (s.a.equals("Extra") && "Core".equals(s.b)))),
                    "accepted EXCLUDES(Core,Extra) must be trusted while it still re-mines as hard");

            // now commit a genuine counterexample: Core and Extra together
            Path bothDir = workDir.resolve("both");
            Files.createDirectories(bothDir);
            Files.writeString(bothDir.resolve("core.txt"), "core both\n");
            Files.writeString(bothDir.resolve("extra.txt"), "extra both\n");
            service.setBaseDir(bothDir);
            service.commit("core and extra together", "Core, Extra");

            // (1) acceptance is durable -- the persisted Constraint is still there
            boolean stillPersisted = service.getRepository().getConstraints().stream()
                    .anyMatch(c -> c.getKind() == Constraint.Kind.EXCLUDES
                            && ((c.getFeatureA().equals("Core") && "Extra".equals(c.getFeatureB()))
                            || (c.getFeatureA().equals("Extra") && "Core".equals(c.getFeatureB()))));
            assertTrue(stillPersisted, "acceptance must be durable -- a contradicted constraint is not silently deleted");

            // (2) but it must no longer be TRUSTED -- excluded from the fresh-mined-and-filtered result
            List<ConstraintMiner.Suggestion> trustedAfter = service.acceptedSuggestions(service.getRepository());
            assertFalse(trustedAfter.stream().anyMatch(s -> s.kind == ConstraintMiner.Kind.EXCLUDES
                            && ((s.a.equals("Core") && "Extra".equals(s.b)) || (s.a.equals("Extra") && "Core".equals(s.b)))),
                    "a persisted-but-now-contradicted constraint must be excluded from the trusted set, "
                            + "even though it's still recorded as accepted -- this is the safety rule that must survive "
                            + "moving storage from local Preferences into the repository");
        }
    }
}
