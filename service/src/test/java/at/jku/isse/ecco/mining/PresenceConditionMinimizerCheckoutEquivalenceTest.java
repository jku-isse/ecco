package at.jku.isse.ecco.mining;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.module.Condition;
import at.jku.isse.ecco.service.EccoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.logicng.formulas.Formula;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that association selection during checkout -- {@code Repository.Op#compose(Configuration)},
 * which is driven entirely by {@link Condition#holds} and is the sole condition-dependent step of
 * checkout, everything after it (artifact tree composition) being a deterministic function of the
 * selected-association set -- would produce the exact same result if driven by minimized presence
 * conditions instead, for every actually-committed configuration in a real repository.
 *
 * <p>Minimized conditions are NOT wired into checkout today (see
 * {@link PresenceConditionMinimizerRealRepoTest}'s javadoc); this test changes nothing about
 * production selection logic. It calls {@link EccoService#getAssociations(Configuration)} -- the
 * exact same {@code compose(configuration)} used internally by {@link EccoService#checkout}, minus
 * that method's disk writes, which would otherwise need a fresh checkout directory per configuration
 * checked here -- and independently recomputes, from each association's minimized
 * {@link PresenceConditionMinimizer.Term}s, what selection minimized conditions would have produced.
 * Agreement of the two association-ID sets for every committed configuration is the checkout-shaped
 * claim {@link PresenceConditionMinimizerRealRepoTest} deliberately stopped short of.
 */
public class PresenceConditionMinimizerCheckoutEquivalenceTest {

    @Test
    @Timeout(30)
    public void checkoutWouldSelectSameAssociations_usingMinimizedConditions() throws IOException {
        Path workDir = Files.createTempDirectory("minimizer-checkout-equivalence-test");
        Path repoDir = workDir.resolve(".ecco");

        Path coreDir = workDir.resolve("core");
        Files.createDirectories(coreDir);
        Files.writeString(coreDir.resolve("core.txt"), "core\n");

        Path branchADir = workDir.resolve("branchA");
        Files.createDirectories(branchADir);
        Files.writeString(branchADir.resolve("core.txt"), "core\n");
        Files.writeString(branchADir.resolve("branchA.txt"), "branch A\n");

        Path branchBDir = workDir.resolve("branchB");
        Files.createDirectories(branchBDir);
        Files.writeString(branchBDir.resolve("core.txt"), "core\n");
        Files.writeString(branchBDir.resolve("branchB.txt"), "branch B\n");

        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repoDir);
            service.init();

            service.setBaseDir(coreDir);
            service.commit("core", "Core");

            service.setBaseDir(branchADir);
            service.commit("branch A", "Core, BranchA");

            service.setBaseDir(branchBDir);
            service.commit("branch B", "Core, BranchB");

            // a real accept, exactly like a human reviewing `suggest-constraints` output would do
            service.acceptConstraint(ConstraintMiner.Kind.MANDATORY, "Core", null);

            // same pipeline as MinimizePreviewCommand/AssociationsView's "Minimize Presence
            // Conditions" button: re-mine fresh, filter to accepted signatures, compile once.
            List<Set<String>> configs = ConfigurationBridge.readConfigurations(service);
            List<ConstraintMiner.Suggestion> mined = new ConstraintMiner(4, 0.9, null).mine(configs);
            Set<String> accepted = AcceptedConstraints.acceptedSignatures(service.getRepository().getConstraints());
            List<ConstraintMiner.Suggestion> acceptedSuggestions = new ArrayList<>();
            for (ConstraintMiner.Suggestion suggestion : mined) {
                if (accepted.contains(ConstraintSuggestionPreferences.signatureOf(suggestion))) {
                    acceptedSuggestions.add(suggestion);
                }
            }
            assertEquals(1, acceptedSuggestions.size(), "sanity check: MANDATORY Core should have re-mined and been picked up as accepted");

            Formula featureModel = FeatureModelFormula.compile(acceptedSuggestions);

            // one minimized Term list per association, computed once -- reused for every
            // configuration checked below, exactly like a real minimize-then-checkout workflow would
            Map<String, List<PresenceConditionMinimizer.Term>> minimizedTermsByAssociationId = new HashMap<>();
            boolean atLeastOneSimplified = false;
            for (Association association : service.getRepository().getAssociations()) {
                Condition condition = association.computeCondition();
                List<PresenceConditionMinimizer.Term> originalTerms = ModuleConditionBridge.toTerms(condition);
                List<PresenceConditionMinimizer.Term> minimizedTerms = PresenceConditionMinimizer.minimize(featureModel, originalTerms);
                minimizedTermsByAssociationId.put(association.getId(), minimizedTerms);
                if (!PresenceConditionMinimizer.format(originalTerms).equals(PresenceConditionMinimizer.format(minimizedTerms))) {
                    atLeastOneSimplified = true;
                }
            }
            assertTrue(atLeastOneSimplified, "expected at least one association's condition to actually be simplified given the accepted constraints");

            List<Commit> commits = new ArrayList<>(service.getCommits());
            assertEquals(3, commits.size());

            for (Commit commit : commits) {
                Configuration configuration = commit.getConfiguration();

                // the real production selection step -- same compose(configuration) that checkout() uses
                Set<Association> realSelected = service.getAssociations(configuration);
                Set<String> realSelectedIds = new HashSet<>();
                for (Association association : realSelected) {
                    realSelectedIds.add(association.getId());
                }

                Set<String> minimizedSelectedIds = new HashSet<>();
                Set<String> selectedFeatureNames = featureNamesOf(configuration);
                for (Map.Entry<String, List<PresenceConditionMinimizer.Term>> entry : minimizedTermsByAssociationId.entrySet()) {
                    if (holds(entry.getValue(), selectedFeatureNames)) {
                        minimizedSelectedIds.add(entry.getKey());
                    }
                }

                assertEquals(realSelectedIds, minimizedSelectedIds, "checkout(" + configuration
                        + ") would have selected a different set of associations under minimized conditions");
            }
        }
    }

    private static Set<String> featureNamesOf(Configuration configuration) {
        Set<String> names = new HashSet<>();
        for (FeatureRevision featureRevision : configuration.getFeatureRevisions()) {
            names.add(featureRevision.getFeature().getName());
        }
        return names;
    }

    /**
     * Mirrors {@code Module.holds()}/{@code Condition.holds()}'s real semantics; see
     * {@link PresenceConditionMinimizerRealRepoTest} for the full rationale.
     */
    private static boolean holds(List<PresenceConditionMinimizer.Term> terms, Set<String> selectedFeatureNames) {
        for (PresenceConditionMinimizer.Term term : terms) {
            if (selectedFeatureNames.containsAll(term.positive)
                    && term.negative.stream().noneMatch(selectedFeatureNames::contains)) {
                return true;
            }
        }
        return false;
    }
}
