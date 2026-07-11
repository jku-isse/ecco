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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies, against a real repository (not the synthetic {@link PresenceConditionMinimizer.Term}
 * data used by {@code PresenceConditionMinimizerTest}), that a minimized condition agrees with the
 * real {@link Condition#holds}on every actually-committed configuration. This is what exercises
 * {@link ModuleConditionBridge} -- the one ECCO-coupled piece of the minimization feature that the
 * synthetic unit tests can't touch -- end to end, using the exact same pipeline as
 * {@code MinimizePreviewCommand}/{@code AssociationsView}'s minimize button.
 *
 * <p>This deliberately stops short of checkout/artifact-tree comparison: minimized conditions are
 * never wired into checkout (see CONSTRAINT_MINING_DESIGN.md's "Minimization" section -- that
 * remains a deliberately unstarted, riskier step), so there is no artifact tree built from a
 * minimized condition to compare against. Agreement with {@code holds()} on every committed
 * configuration is the strongest claim testable without that wiring existing.
 */
public class PresenceConditionMinimizerRealRepoTest {

    @Test
    @Timeout(30)
    public void minimizedConditions_agreeWithRealHolds_onEveryCommittedConfiguration() throws IOException {
        Path workDir = Files.createTempDirectory("minimizer-real-repo-test");
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
            ConstraintSuggestionPreferences.accept(repoDir, "MANDATORY|Core|");

            // same pipeline as MinimizePreviewCommand/AssociationsView's "Minimize Presence
            // Conditions" button: re-mine fresh, filter to accepted signatures, compile once.
            List<Set<String>> configs = ConfigurationBridge.readConfigurations(service);
            List<ConstraintMiner.Suggestion> mined = new ConstraintMiner(4, 0.9, null).mine(configs);
            Set<String> accepted = ConstraintSuggestionPreferences.getAccepted(repoDir);
            List<ConstraintMiner.Suggestion> acceptedSuggestions = new ArrayList<>();
            for (ConstraintMiner.Suggestion suggestion : mined) {
                if (accepted.contains(ConstraintSuggestionPreferences.signatureOf(suggestion))) {
                    acceptedSuggestions.add(suggestion);
                }
            }
            assertEquals(1, acceptedSuggestions.size(), "sanity check: MANDATORY Core should have re-mined and been picked up as accepted");
            assertTrue(acceptedSuggestions.get(0).isHard(), "sanity check: MANDATORY Core should have re-mined as hard");

            Formula featureModel = FeatureModelFormula.compile(acceptedSuggestions);

            List<Commit> commits = new ArrayList<>(service.getCommits());
            int checkedPairs = 0;
            for (Association association : service.getRepository().getAssociations()) {
                Condition condition = association.computeCondition();
                List<PresenceConditionMinimizer.Term> originalTerms = ModuleConditionBridge.toTerms(condition);
                List<PresenceConditionMinimizer.Term> minimizedTerms = PresenceConditionMinimizer.minimize(featureModel, originalTerms);

                for (Commit commit : commits) {
                    Configuration configuration = commit.getConfiguration();
                    boolean realHolds = condition.holds(configuration);
                    boolean minimizedHolds = holds(minimizedTerms, featureNamesOf(configuration));
                    assertEquals(realHolds, minimizedHolds, "association " + association.getId()
                            + " disagreed with the real Condition.holds() for configuration " + configuration);
                    checkedPairs++;
                }
            }
            assertTrue(checkedPairs > 0, "expected at least one association/configuration pair to actually be checked");
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
     * Mirrors {@code Module.holds()}/{@code Condition.holds()}'s real semantics (a term holds if
     * all its positive features are present and none of its negative features are, ORed across
     * terms) -- operating on plain feature-name {@link PresenceConditionMinimizer.Term}s instead of
     * real {@code Module}/{@code Feature} objects, since that's all a minimized condition is.
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
