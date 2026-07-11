package at.jku.isse.ecco.mining;

import at.jku.isse.ecco.core.Association;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ParallelMinimization} must produce exactly the same result as calling
 * {@link PresenceConditionMinimizer#minimize} sequentially, association by association -- this is
 * the regression guard for the one real hazard in going concurrent: a LogicNG {@code FormulaFactory}
 * (and anything built from it, including a compiled {@link FeatureModelFormula}) is thread-local by
 * design, so a bug here would most likely show up as either an exception (formulas from different
 * factories mixed together) or silently wrong/inconsistent results, not a hang -- both of which this
 * equality check would catch.
 */
public class ParallelMinimizationTest {

    @Test
    @Timeout(30)
    public void minimizeAll_matchesSequentialMinimize_associationByAssociation() throws IOException {
        Path workDir = Files.createTempDirectory("parallel-minimization-test");
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

            ConstraintSuggestionPreferences.accept(repoDir, "MANDATORY|Core|");

            List<Set<String>> configs = ConfigurationBridge.readConfigurations(service);
            List<ConstraintMiner.Suggestion> mined = new ConstraintMiner(4, 0.9, null).mine(configs);
            Set<String> accepted = ConstraintSuggestionPreferences.getAccepted(repoDir);
            List<ConstraintMiner.Suggestion> acceptedSuggestions = new ArrayList<>();
            for (ConstraintMiner.Suggestion suggestion : mined) {
                if (accepted.contains(ConstraintSuggestionPreferences.signatureOf(suggestion))) {
                    acceptedSuggestions.add(suggestion);
                }
            }
            assertTrue(!acceptedSuggestions.isEmpty(), "sanity check: expected MANDATORY Core to re-mine and be accepted");

            List<Association> associations = new ArrayList<>(service.getRepository().getAssociations());
            assertTrue(associations.size() > 1, "sanity check: need more than one association for this to actually exercise concurrency");

            // sequential baseline: exactly what MinimizePreviewCommand/AssociationsView did before
            // ParallelMinimization existed
            Formula featureModel = FeatureModelFormula.compile(acceptedSuggestions);
            Map<String, String> sequential = new HashMap<>();
            for (Association association : associations) {
                List<PresenceConditionMinimizer.Term> originalTerms = ModuleConditionBridge.toTerms(association.computeCondition());
                List<PresenceConditionMinimizer.Term> minimizedTerms = PresenceConditionMinimizer.minimize(featureModel, originalTerms);
                sequential.put(association.getId(), PresenceConditionMinimizer.format(minimizedTerms));
            }

            // parallel, via the class under test
            AtomicInteger completions = new AtomicInteger(0);
            Set<String> completedIds = java.util.Collections.synchronizedSet(new java.util.HashSet<>());
            Map<String, String> parallel = ParallelMinimization.minimizeAll(associations, acceptedSuggestions, (association, minimizedText) -> {
                completions.incrementAndGet();
                completedIds.add(association.getId());
            });

            assertEquals(associations.size(), completions.get(), "onAssociationDone should fire exactly once per association");
            assertEquals(sequential.keySet(), completedIds, "onAssociationDone should fire for every association actually processed");
            assertEquals(sequential, parallel, "parallel minimization must produce exactly the same result as sequential minimization");
        }
    }

    @Test
    @Timeout(10)
    public void minimizeAll_onEmptyInput_returnsEmptyMapWithoutSpawningThreads() {
        Map<String, String> result = ParallelMinimization.minimizeAll(List.of(), List.of(), null);
        assertTrue(result.isEmpty());
    }

    /**
     * Regression guard for {@code MinimizationResults}' cancellation-on-repository-close: the
     * calling thread being interrupted must make {@code minimizeAll} give up (not silently finish
     * the whole batch regardless), and must leave the thread's interrupted status set afterward so
     * the caller can tell it was a cancellation, not a real failure (see
     * {@code MinimizationResults.run()}'s {@code Thread.currentThread().isInterrupted()} check).
     *
     * <p>Pre-interrupting the calling thread before entry, rather than trying to interrupt a
     * separate thread mid-flight, is what makes this deterministic instead of a timing-dependent
     * race: {@link java.util.concurrent.BlockingQueue#take()} (which {@code CompletionService.take()}
     * delegates to) is specified to check the interrupted status immediately and throw without
     * blocking if it's already set.
     */
    @Test
    @Timeout(10)
    public void minimizeAll_respondsToAnAlreadyInterruptedCallingThread() throws IOException {
        Path workDir = Files.createTempDirectory("parallel-minimization-interrupt-test");
        Path repoDir = workDir.resolve(".ecco");
        Path coreDir = workDir.resolve("core");
        Files.createDirectories(coreDir);
        Files.writeString(coreDir.resolve("core.txt"), "core\n");

        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repoDir);
            service.init();
            service.setBaseDir(coreDir);
            service.commit("core", "Core");

            List<Association> associations = new ArrayList<>(service.getRepository().getAssociations());
            assertTrue(!associations.isEmpty(), "sanity check: need at least one association to reach the interruptible wait at all");

            Thread.currentThread().interrupt();
            try {
                org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                        () -> ParallelMinimization.minimizeAll(associations, List.of(), null),
                        "an already-interrupted calling thread should make minimizeAll give up instead of completing the batch");
            } finally {
                // Thread.interrupted() reads AND clears the flag: doubles as the assertion that
                // minimizeAll correctly left it set for the caller, and as cleanup so it doesn't
                // leak into whatever test JUnit runs next on this thread.
                assertTrue(Thread.interrupted(), "interrupted status should still be set after minimizeAll propagates the interruption");
            }
        }
    }
}