package at.jku.isse.ecco.gui.view.graph;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.pog.PartialOrderGraph;
import at.jku.isse.ecco.storage.ser.artifact.SerArtifact;
import at.jku.isse.ecco.storage.ser.pog.SerPartialOrderGraph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * What the order graph view draws: every node once and every edge once. The walk went into a node's
 * next nodes again each time it came to the node from another previous node - exponential in the
 * open branches of a file's order (tinycc's tccgen.c had 15) - and was read without the service's
 * lock, while a commit could change the graph (ConcurrentModificationException); it is read holding
 * the lock it is given, and only the latest graph asked for is drawn.
 */
public class PartialOrderGraphViewTest {

    private record Line(String text) implements ArtifactData {
    }

    private static List<Artifact.Op<?>> artifacts(List<String> lines) {
        List<Artifact.Op<?>> artifacts = new ArrayList<>();
        for (String line : lines)
            artifacts.add(new SerArtifact<>(new Line(line)));
        return artifacts;
    }

    private static int countNodesAndEdges(PartialOrderGraph pog, int[] edges) {
        Set<PartialOrderGraph.Node> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<PartialOrderGraph.Node> pending = new ArrayDeque<>(List.of(pog.getHead()));
        seen.add(pog.getHead());
        while (!pending.isEmpty()) {
            PartialOrderGraph.Node node = pending.pop();
            edges[0] += node.getNext().size();
            for (PartialOrderGraph.Node next : node.getNext())
                if (seen.add(next))
                    pending.push(next);
        }
        return seen.size();
    }

    /** 20 places where variants added different lines, one after another: about a million paths, each node drawn once. */
    @Test
    @Timeout(30)
    public void everyNodeAndEdgeOnceAlsoWithManyOpenBranches() {
        PartialOrderGraph.Op pog = new SerPartialOrderGraph();
        List<String> a = new ArrayList<>();
        List<String> b = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            a.add("shared " + i);
            b.add("shared " + i);
            a.add("only a " + i);
            b.add("only b " + i);
        }
        pog.merge(artifacts(a));
        pog.merge(artifacts(b));

        PartialOrderGraphView.Snapshot snapshot = PartialOrderGraphView.snapshot(pog);

        int[] edges = {0};
        assertEquals(countNodesAndEdges(pog, edges), snapshot.labels().size());
        assertEquals(edges[0], snapshot.edges().size());
        assertEquals(2 + 60, snapshot.labels().size(), "head, tail and 60 lines");
        assertEquals(snapshot.edges().size(), snapshot.edges().stream().map(Arrays::toString).distinct().count(), "no edge twice");
        assertEquals("start", snapshot.classes().get(0), "the head");
        assertEquals(1, snapshot.classes().stream().filter("end"::equals).count(), "the tail");
        assertTrue(snapshot.labels().stream().anyMatch(l -> l.startsWith("Line[text=only b 19]")), snapshot.labels().toString());
    }

    private static PartialOrderGraph graph(String... lines) {
        PartialOrderGraph.Op pog = new SerPartialOrderGraph();
        pog.merge(artifacts(List.of(lines)));
        return pog;
    }

    /**
     * The graph is read holding the lock it is given (the service's, which commits hold): nothing
     * is drawn while it is held; and of two graphs asked for meanwhile only the later one is drawn,
     * whichever thread gets the lock first.
     */
    @Test
    @Timeout(60)
    public void readUnderTheLockAndOnlyTheLatestRequestIsDrawn() throws Exception {
        at.jku.isse.ecco.gui.FxTestSupport.startToolkit();
        // AWT started on the FX thread, never first from the test thread (macOS: both hang; see DependencyGraphViewTest)
        at.jku.isse.ecco.gui.FxTestSupport.onFx(() -> java.awt.Toolkit.getDefaultToolkit());
        PartialOrderGraphView view = at.jku.isse.ecco.gui.FxTestSupport.onFx(PartialOrderGraphView::new);
        Object lock = new Object();
        PartialOrderGraph first = graph("a", "b");
        PartialOrderGraph second = graph("a", "b", "c", "d", "e");
        synchronized (lock) {
            at.jku.isse.ecco.gui.FxTestSupport.onFx(() -> {
                view.showGraph(first, lock);
                view.showGraph(second, lock);
            });
            Thread.sleep(300);
            assertEquals(0, view.drawnGraph().getNodeCount(), "nothing read while the lock is held");
        }
        at.jku.isse.ecco.gui.FxTestSupport.waitUntil("the later graph", () -> view.drawnGraph().getNodeCount() == 2 + 5);
        Thread.sleep(300);
        assertEquals(2 + 5, view.drawnGraph().getNodeCount(), "the earlier graph is not drawn over it");
    }

    /** Closed while the graph waited for the lock: nothing is drawn, nothing fails. */
    @Test
    @Timeout(60)
    public void closedWhileWaitingForTheLockDrawsNothing() throws Exception {
        at.jku.isse.ecco.gui.FxTestSupport.startToolkit();
        at.jku.isse.ecco.gui.FxTestSupport.onFx(() -> java.awt.Toolkit.getDefaultToolkit());
        List<Throwable> thrown = new java.util.concurrent.CopyOnWriteArrayList<>();
        Thread.UncaughtExceptionHandler before = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, e) -> thrown.add(e));
        try {
            PartialOrderGraphView view = at.jku.isse.ecco.gui.FxTestSupport.onFx(PartialOrderGraphView::new);
            Object lock = new Object();
            synchronized (lock) {
                at.jku.isse.ecco.gui.FxTestSupport.onFx(() -> view.showGraph(graph("a", "b"), lock));
                at.jku.isse.ecco.gui.FxTestSupport.onFx(view::closeGraph);
            }
            Thread.sleep(500);
            at.jku.isse.ecco.gui.FxTestSupport.onFx(() -> { });
            assertEquals(List.of(), thrown);
            assertEquals(0, view.drawnGraph().getNodeCount());
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(before);
        }
    }
}
