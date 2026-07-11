package at.jku.isse.ecco.mining;

import at.jku.isse.ecco.core.Association;
import org.logicng.formulas.Formula;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;

/**
 * Runs {@link PresenceConditionMinimizer#minimize} across a repository's associations in parallel
 * (one worker thread per available processor) -- associations are fully independent of each other,
 * so this is a safe, correctness-neutral speedup: same checks, same results, just computed
 * concurrently instead of one at a time.
 *
 * <p>The one real hazard: a LogicNG {@code FormulaFactory} (and everything built from it, including
 * a compiled {@link FeatureModelFormula}) is thread-local by design (see
 * {@code FormulaFactoryProvider}) -- formulas from one thread's factory cannot be mixed into another
 * thread's SAT calls. So this deliberately does <em>not</em> compile the feature model once and
 * share that {@link Formula} object across worker threads; each worker thread compiles its own copy
 * from the shared, thread-safe {@code List<ConstraintMiner.Suggestion>} the first time it's used,
 * then reuses that copy for every association it happens to process.
 */
public final class ParallelMinimization {

    private ParallelMinimization() {
    }

    /**
     * @param associations        associations to minimize (read-only; nothing here is mutated or persisted)
     * @param acceptedSuggestions same input {@link FeatureModelFormula#compile} would take -- plain
     *                            data, safe to share across threads
     * @param onAssociationDone   called (from whichever worker thread finished, not necessarily in
     *                            {@code associations} order) as each association's minimized
     *                            condition becomes available; may be {@code null}
     * @return association id -> minimized condition text (see {@link PresenceConditionMinimizer#format})
     */
    public static Map<String, String> minimizeAll(
            Collection<? extends Association> associations,
            List<ConstraintMiner.Suggestion> acceptedSuggestions,
            BiConsumer<Association, String> onAssociationDone) {

        Map<String, String> result = new HashMap<>();
        if (associations.isEmpty()) return result;

        int threadCount = Math.max(1, Runtime.getRuntime().availableProcessors());
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        ThreadLocal<Formula> featureModelPerThread = ThreadLocal.withInitial(() -> FeatureModelFormula.compile(acceptedSuggestions));
        CompletionService<Map.Entry<Association, String>> completionService = new ExecutorCompletionService<>(executor);

        try {
            for (Association association : associations) {
                completionService.submit(() -> {
                    Formula featureModel = featureModelPerThread.get();
                    List<PresenceConditionMinimizer.Term> originalTerms = ModuleConditionBridge.toTerms(association.computeCondition());
                    List<PresenceConditionMinimizer.Term> minimizedTerms = PresenceConditionMinimizer.minimize(featureModel, originalTerms);
                    return Map.entry(association, PresenceConditionMinimizer.format(minimizedTerms));
                });
            }

            for (int i = 0; i < associations.size(); i++) {
                try {
                    Map.Entry<Association, String> entry = completionService.take().get();
                    result.put(entry.getKey().getId(), entry.getValue());
                    if (onAssociationDone != null) onAssociationDone.accept(entry.getKey(), entry.getValue());
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    throw new RuntimeException("Minimization failed", cause);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Minimization interrupted", e);
                }
            }
            return result;
        } finally {
            executor.shutdown();
        }
    }
}
