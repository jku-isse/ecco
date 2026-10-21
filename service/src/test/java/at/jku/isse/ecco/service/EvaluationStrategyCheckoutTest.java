package at.jku.isse.ecco.service;

import at.jku.isse.ecco.core.Checkout;
import at.jku.isse.ecco.featuretrace.evaluation.ProactiveAdditionEvaluation;
import at.jku.isse.ecco.featuretrace.evaluation.ProactiveBasedEvaluation;
import at.jku.isse.ecco.featuretrace.evaluation.ProactiveSubtractionEvaluation;
import at.jku.isse.ecco.featuretrace.evaluation.RetroactiveBasedEvaluation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FR-K1b end to end: the repository's evaluation strategy decides, per node, whether a node with a
 * proactive trace and a learned (retroactive) condition is in a checkout - through EccoService commit
 * and checkout, not only on one class (NodeRemovalVisitorTest).
 * <p>
 * The strategies can only differ where an <em>accepted</em> trace and the learned condition disagree.
 * A trace that contradicts the history is not used (FR-T2a, ProactiveTraceCheck): it must be true in
 * every commit containing its association and false in every other. The learned condition fits the
 * history too, so both agree on every committed configuration - they can disagree only on one that
 * was never committed. Three commits of main.c:
 * <pre>
 *   C1  BASE         base lines only
 *   C2  BASE, A      + line a          trace of a: "A | B"
 *   C3  BASE, A, B   + line a + line b trace of a: "A | B", of b: "A &amp; B"
 * </pre>
 * Line a is in C2 and C3 only: learned condition A. Its trace "A | B" is false in C1 and true in C2
 * and C3 - accepted. Line b is in C3 only: learned condition B (B was only ever committed in C3).
 * Its trace "A &amp; B" is false in C1 and C2 and true in C3 - accepted. In the never committed
 * configuration "BASE, B" the two disagree in opposite directions:
 * <pre>
 *            learned   trace
 *   line a   A: false  A | B: true
 *   line b   B: true   A &amp; B: false
 * </pre>
 * so each of the four strategies checks out a different main.c (each expectation follows from the
 * strategy's documented semantics, see featuretrace/evaluation). Main-tree building is NO-BOOST:
 * boosting would copy a trace to the untraced artifacts of its association, which is not what is
 * tested here (each line is its own association, so it would not change main.c either).
 */
public class EvaluationStrategyCheckoutTest {

    private static final String HEADER = "Path;File Condition;Block Condition;Presence Condition;Line Type;start;end\n";
    private static final String BASE = "int main() {\n    int x = 1;\n    return 0;\n}\n";
    private static final String WITH_A = "int main() {\n    int x = 1;\n    int a = 2;\n    return 0;\n}\n";
    private static final String WITH_A_B = "int main() {\n    int x = 1;\n    int a = 2;\n    int b = 3;\n    return 0;\n}\n";
    private static final String WITH_B = "int main() {\n    int x = 1;\n    int b = 3;\n    return 0;\n}\n";

    @TempDir
    Path work;

    /** PROACTIVE: the trace decides where there is one - a ("A | B" true) stays, b ("A &amp; B" false) goes. */
    @Test
    @Timeout(120)
    public void proactiveFollowsTheTraces() throws Exception {
        assertEquals(WITH_A, checkoutOfUncommittedCombination(ProactiveBasedEvaluation.STRATEGY_NAME));
    }

    /** RETROACTIVE: the learned condition decides, traces are ignored - a (A false) goes, b (B true) stays. */
    @Test
    @Timeout(120)
    public void retroactiveFollowsTheHistory() throws Exception {
        assertEquals(WITH_B, checkoutOfUncommittedCombination(RetroactiveBasedEvaluation.STRATEGY_NAME));
    }

    /**
     * PROACTIVE-ADDITION: learned condition or trace - a trace can add a node (a: false or true) but
     * not remove one (b: true or false): both lines stay.
     */
    @Test
    @Timeout(120)
    public void proactiveAdditionKeepsWhatEitherKeeps() throws Exception {
        assertEquals(WITH_A_B, checkoutOfUncommittedCombination(ProactiveAdditionEvaluation.STRATEGY_NAME));
    }

    /**
     * PROACTIVE-SUBTRACTION: learned condition and trace - a trace can remove a node (b: true and
     * false) but not add one (a: false and true): both lines go. The base lines stay: learned condition
     * BASE, true here (a trace from the ROOT entry would be True and change nothing).
     */
    @Test
    @Timeout(120)
    public void proactiveSubtractionKeepsWhatBothKeep() throws Exception {
        assertEquals(BASE, checkoutOfUncommittedCombination(ProactiveSubtractionEvaluation.STRATEGY_NAME));
    }

    /** On committed configurations, traces and learned conditions agree: every strategy gives the commit back. */
    @Test
    @Timeout(240)
    public void everyStrategyReproducesTheCommittedVariants() throws Exception {
        for (String strategy : new String[]{ProactiveBasedEvaluation.STRATEGY_NAME, RetroactiveBasedEvaluation.STRATEGY_NAME,
                ProactiveAdditionEvaluation.STRATEGY_NAME, ProactiveSubtractionEvaluation.STRATEGY_NAME}) {
            try (EccoService service = committed(strategy)) {
                assertEquals(BASE, checkout(service, "BASE"), strategy);
                assertEquals(WITH_A, checkout(service, "BASE, A"), strategy);
                assertEquals(WITH_A_B, checkout(service, "BASE, A, B"), strategy);
            }
        }
    }

    private String checkoutOfUncommittedCombination(String strategy) throws Exception {
        try (EccoService service = committed(strategy)) {
            assertEquals(strategy, service.getRepository().getEvaluationStrategy().getStrategyName());
            return checkout(service, "BASE, B");
        }
    }

    /** A new repository with [strategy] (set on this service only - ecco.properties is left alone) and C1-C3. */
    private EccoService committed(String strategy) throws Exception {
        Path root = Files.createTempDirectory(this.work, strategy);
        EccoService service = new EccoService();
        service.getProperties().setProperty(EccoService.ECCO_PROPERTIES_EVALUATION_STRATEGY, strategy);
        service.getProperties().setProperty(EccoService.ECCO_PROPERTIES_MAIN_TREE_BUILDING_STRATEGY, "NO-BOOST");
        service.setRepositoryDir(root.resolve(".ecco"));
        service.init();
        commit(service, root.resolve("c1"), BASE, null, "BASE");
        commit(service, root.resolve("c2"), WITH_A, HEADER
                + "main.c;True;True;True;ROOT;1;5\n"
                + "main.c;True;A | B;A | B;artifact;3;3\n", "BASE, A");
        commit(service, root.resolve("c3"), WITH_A_B, HEADER
                + "main.c;True;True;True;ROOT;1;6\n"
                + "main.c;True;A | B;A | B;artifact;3;3\n"
                + "main.c;True;A & B;A & B;artifact;4;4\n", "BASE, A, B");
        return service;
    }

    private static void commit(EccoService service, Path dir, String content, String presenceConditions, String configuration) throws Exception {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("main.c"), content);
        if (presenceConditions != null)
            Files.writeString(dir.resolve("pcs.variant.csv"), presenceConditions);
        service.setBaseDir(dir);
        service.commit(configuration, configuration);
    }

    /** main.c of the checkout of [configuration]; also checks that no trace was rejected (both fit the history). */
    private String checkout(EccoService service, String configuration) throws Exception {
        Path out = Files.createTempDirectory(this.work, "checkout");
        service.setBaseDir(out);
        Checkout checkout = service.checkout(configuration);
        assertTrue(checkout.getRejectedTraces().isEmpty(), checkout.getRejectedTraces().toString());
        return Files.readString(out.resolve("main.c"));
    }
}
