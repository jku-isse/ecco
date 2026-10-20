package at.jku.isse.ecco.gui.view.graph;

import at.jku.isse.ecco.service.EccoService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.SwingUtilities;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static at.jku.isse.ecco.gui.FxTestSupport.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The dependency graph is drawn on the AWT thread, queued from the FX thread: a repository closed
 * before the queued drawing ran made it throw "Service is not initialized" there.
 */
public class DependencyGraphViewTest {

    @BeforeAll
    public static void start() throws Exception {
        startToolkit();
        // AWT is started on the FX thread, as the views do: on macOS, started from the test thread
        // while the FX thread (AppKit's main thread) waits for it too, both hang
        onFx(() -> java.awt.Toolkit.getDefaultToolkit());
    }

    /** Shown, then the repository closed at once: the queued drawing does nothing instead of throwing. */
    @Test
    @Timeout(60)
    public void closingTheRepositoryBeforeTheGraphIsDrawnThrowsNothing(@TempDir Path tmp) throws Exception {
        List<Throwable> thrown = new CopyOnWriteArrayList<>();
        Thread.UncaughtExceptionHandler before = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, e) -> thrown.add(e));
        try {
            EccoService service = repository(tmp.resolve("repo"));
            commit(service, tmp.resolve("v1"), "A.1", Map.of("a.txt", "a\n"));
            commit(service, tmp.resolve("v2"), "A.1, B.1", Map.of("a.txt", "a\n", "b.txt", "b\n"));
            DependencyGraphView view = onFx(() -> new DependencyGraphView(service));

            // hold the AWT thread so the drawing is still queued when the repository closes
            Object gate = new Object();
            synchronized (gate) {
                SwingUtilities.invokeLater(() -> {
                    synchronized (gate) {
                    }
                });
                onFx(() -> view.setTabVisible(true));
                onFx(() -> { }); // the runLater that queues the drawing
                service.close();
            }
            onFx(() -> { });
            CountDownLatch drawn = new CountDownLatch(1);
            SwingUtilities.invokeLater(drawn::countDown);
            assertTrue(drawn.await(20, TimeUnit.SECONDS), "the AWT thread is stuck");

            assertEquals(List.of(), thrown);
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(before);
        }
    }

    /**
     * "Hide Implied Dependencies" hides a dependency that says nothing new: from (A and B, say a line)
     * needs to (A, its file) - wherever from is, to is. It checked the other way round.
     */
    @Test
    public void aDependencyIsImpliedWhenItsSourceImpliesItsTarget() {
        at.jku.isse.ecco.dao.EntityFactory ef = new at.jku.isse.ecco.storage.ser.dao.SerEntityFactory();
        at.jku.isse.ecco.repository.Repository.Op repository = ef.createRepository();
        repository.setMaxOrder(2);
        at.jku.isse.ecco.feature.FeatureRevision a = repository.addFeature("a", "A").addRevision("1");
        at.jku.isse.ecco.feature.FeatureRevision b = repository.addFeature("b", "B").addRevision("1");
        at.jku.isse.ecco.module.Module moduleA = repository.addModule(new at.jku.isse.ecco.feature.Feature[]{a.getFeature()}, new at.jku.isse.ecco.feature.Feature[0]);
        at.jku.isse.ecco.module.Module moduleAB = repository.addModule(new at.jku.isse.ecco.feature.Feature[]{a.getFeature(), b.getFeature()}, new at.jku.isse.ecco.feature.Feature[0]);
        at.jku.isse.ecco.module.Condition onlyA = new at.jku.isse.ecco.storage.ser.module.SerCondition();
        onlyA.addModuleRevision(moduleA.addRevision(new at.jku.isse.ecco.feature.FeatureRevision[]{a}, new at.jku.isse.ecco.feature.Feature[0]));
        at.jku.isse.ecco.module.Condition aAndB = new at.jku.isse.ecco.storage.ser.module.SerCondition();
        aAndB.addModuleRevision(moduleAB.addRevision(new at.jku.isse.ecco.feature.FeatureRevision[]{a, b}, new at.jku.isse.ecco.feature.Feature[0]));

        assertTrue(DependencyGraphView.isImplied(aAndB, onlyA), "A and B needs A: wherever A and B is, A is - nothing new");
        org.junit.jupiter.api.Assertions.assertFalse(DependencyGraphView.isImplied(onlyA, aAndB), "A needs A and B: A can be without B - shown");
    }

    /**
     * Grouped by feature, a dependency's weight is shared out over its feature pairs: with the whole
     * weight for each pair, an association of many features made many heavy edges (zip4j: 1728).
     */
    @Test
    public void aDependencyIsSharedOutOverItsFeaturePairs() {
        Map<List<String>, Double> weights = new java.util.LinkedHashMap<>();
        DependencyGraphView.addFeatureDependency(weights, new java.util.TreeSet<>(java.util.Set.of("a", "b")), java.util.Set.of("c"), 6);
        DependencyGraphView.addFeatureDependency(weights, java.util.Set.of("a"), new java.util.TreeSet<>(java.util.Set.of("a", "c")), 4);

        assertEquals(Map.of(List.of("a", "c"), 3.0 + 2.0, List.of("b", "c"), 3.0), weights,
                "6 over a->c and b->c, 4 over a->a (within a, not drawn) and a->c");
        assertEquals("5.0", DependencyGraphView.formatWeight(5));
        assertEquals("12", DependencyGraphView.formatWeight(12.4));
    }

    /** A condition's features are the present ones of its terms; a negated one is not among them, an untraced association has none. */
    @Test
    public void theFeaturesOfAConditionAreItsPresentOnes() {
        at.jku.isse.ecco.dao.EntityFactory ef = new at.jku.isse.ecco.storage.ser.dao.SerEntityFactory();
        at.jku.isse.ecco.repository.Repository.Op repository = ef.createRepository();
        repository.setMaxOrder(2);
        at.jku.isse.ecco.feature.FeatureRevision a = repository.addFeature("a", "A").addRevision("1");
        at.jku.isse.ecco.feature.FeatureRevision b = repository.addFeature("b", "B").addRevision("1");
        at.jku.isse.ecco.feature.FeatureRevision c = repository.addFeature("c", "C").addRevision("1");
        at.jku.isse.ecco.module.Module aNotB = repository.addModule(new at.jku.isse.ecco.feature.Feature[]{a.getFeature()}, new at.jku.isse.ecco.feature.Feature[]{b.getFeature()});
        at.jku.isse.ecco.module.Module onlyC = repository.addModule(new at.jku.isse.ecco.feature.Feature[]{c.getFeature()}, new at.jku.isse.ecco.feature.Feature[0]);
        at.jku.isse.ecco.module.Condition condition = new at.jku.isse.ecco.storage.ser.module.SerCondition();
        condition.addModuleRevision(aNotB.addRevision(new at.jku.isse.ecco.feature.FeatureRevision[]{a}, new at.jku.isse.ecco.feature.Feature[]{b.getFeature()}));
        condition.addModuleRevision(onlyC.addRevision(new at.jku.isse.ecco.feature.FeatureRevision[]{c}, new at.jku.isse.ecco.feature.Feature[0]));

        assertEquals(java.util.Set.of("A", "C"), DependencyGraphView.features(condition.getAbsorbedModuleRevisions()));
        assertEquals(java.util.Set.of(at.jku.isse.ecco.module.Condition.NOT_TRACED),
                DependencyGraphView.features(List.of()));
    }

    /** Decided on the absorbed terms, an implication - and a label - is what {@link at.jku.isse.ecco.module.Condition#isImpliedBy} says on all of them. */
    @Test
    public void anImplicationOnTheAbsorbedTermsIsTheSameAsOnAllOfThem() {
        at.jku.isse.ecco.dao.EntityFactory ef = new at.jku.isse.ecco.storage.ser.dao.SerEntityFactory();
        at.jku.isse.ecco.repository.Repository.Op repository = ef.createRepository();
        repository.setMaxOrder(3);
        at.jku.isse.ecco.feature.FeatureRevision[] revisions = new at.jku.isse.ecco.feature.FeatureRevision[4];
        for (int i = 0; i < revisions.length; i++)
            revisions[i] = repository.addFeature("f" + i, "F" + i).addRevision("1");
        java.util.Random random = new java.util.Random(7);
        int implied = 0;
        for (int round = 0; round < 2000; round++) {
            at.jku.isse.ecco.module.Condition from = randomCondition(repository, revisions, random);
            at.jku.isse.ecco.module.Condition to = randomCondition(repository, revisions, random);
            for (boolean withRevisions : new boolean[]{true, false})
                assertEquals(from.toDisplayString(withRevisions), DependencyGraphView.displayString(from.getAbsorbedModuleRevisions(), withRevisions));
            boolean expected = to.isImpliedBy(from);
            assertEquals(expected, DependencyGraphView.isImplied(from, to), "round " + round);
            if (expected)
                implied++;
        }
        assertTrue(implied > 100 && implied < 1900, "both answers are tried: " + implied);
    }

    /** One to four terms, each a random choice of present and absent features, small enough to absorb one another often. */
    private static at.jku.isse.ecco.module.Condition randomCondition(at.jku.isse.ecco.repository.Repository.Op repository,
                                                                    at.jku.isse.ecco.feature.FeatureRevision[] revisions, java.util.Random random) {
        at.jku.isse.ecco.module.Condition condition = new at.jku.isse.ecco.storage.ser.module.SerCondition();
        int terms = 1 + random.nextInt(4);
        for (int t = 0; t < terms; t++) {
            List<at.jku.isse.ecco.feature.FeatureRevision> pos = new java.util.ArrayList<>();
            List<at.jku.isse.ecco.feature.Feature> neg = new java.util.ArrayList<>();
            for (at.jku.isse.ecco.feature.FeatureRevision revision : revisions) {
                int pick = random.nextInt(5);
                if (pick == 0)
                    pos.add(revision);
                else if (pick == 1)
                    neg.add(revision.getFeature());
            }
            if (pos.isEmpty())
                pos.add(revisions[random.nextInt(revisions.length)]);
            neg.removeIf(feature -> pos.stream().anyMatch(revision -> revision.getFeature() == feature));
            at.jku.isse.ecco.feature.Feature[] posFeatures = pos.stream().map(at.jku.isse.ecco.feature.FeatureRevision::getFeature).toArray(at.jku.isse.ecco.feature.Feature[]::new);
            at.jku.isse.ecco.feature.Feature[] negFeatures = neg.toArray(new at.jku.isse.ecco.feature.Feature[0]);
            at.jku.isse.ecco.module.Module module = repository.getModule(posFeatures, negFeatures);
            if (module == null)
                module = repository.addModule(posFeatures, negFeatures);
            at.jku.isse.ecco.feature.FeatureRevision[] posRevisions = pos.toArray(new at.jku.isse.ecco.feature.FeatureRevision[0]);
            at.jku.isse.ecco.module.ModuleRevision moduleRevision = module.getRevision(posRevisions, negFeatures);
            if (moduleRevision == null)
                moduleRevision = module.addRevision(posRevisions, negFeatures);
            if (!condition.contains(moduleRevision))
                condition.addModuleRevision(moduleRevision);
        }
        return condition;
    }
}
