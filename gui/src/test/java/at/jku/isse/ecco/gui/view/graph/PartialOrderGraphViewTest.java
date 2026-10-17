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

        PartialOrderGraphView.Snapshot snapshot = PartialOrderGraphView.blocksOf(pog, null);

        // every line is where alternatives part or meet: no run to fold, a block each
        int[] edges = {0};
        assertEquals(countNodesAndEdges(pog, edges), snapshot.labels().size());
        assertEquals(edges[0], snapshot.edges().size());
        assertEquals(2 + 60, snapshot.labels().size(), "start, end and 60 lines");
        assertEquals(snapshot.edges().size(), snapshot.edges().stream().map(Arrays::toString).distinct().count(), "no edge twice");
        assertEquals("start", snapshot.classes().get(0), "the head");
        assertEquals("Start", snapshot.labels().get(0), "it was \"null [-]\"");
        assertEquals(1, snapshot.classes().stream().filter("end"::equals).count(), "the tail");
        assertTrue(snapshot.labels().contains("End"));
        assertTrue(snapshot.labels().stream().anyMatch(l -> l.equals("Line[text=only b 19]")), snapshot.labels().toString());
        // alternatives side by side: "only a i" and "only b i" in one layer, below "shared i"
        int onlyA = snapshot.labels().indexOf("Line[text=only a 7]"), onlyB = snapshot.labels().indexOf("Line[text=only b 7]"), shared = snapshot.labels().indexOf("Line[text=shared 7]");
        assertEquals(snapshot.places().get(onlyA)[1], snapshot.places().get(onlyB)[1]);
        assertNotEquals(snapshot.places().get(onlyA)[0], snapshot.places().get(onlyB)[0]);
        assertTrue(snapshot.places().get(shared)[1] > snapshot.places().get(onlyA)[1], "top to bottom");
        assertEquals("branch", snapshot.classes().get(shared), "they part after it");
    }

    /**
     * A straight run is one block, labelled with where it is in the node shown - its places, or its
     * lines - how many it holds and its first and last text; what only other variants have is
     * labelled so; the details say the rest, the order numbers among it.
     */
    @Test
    public void blocksAreLabelledWithWhereTheyAreAndWhatTheyHold() {
        List<Artifact.Op<?>> shared = artifacts(List.of("a1", "a2"));
        List<Artifact.Op<?>> x = artifacts(List.of("x"));
        List<Artifact.Op<?>> y = artifacts(List.of("y1", "y2", "y3"));
        List<Artifact.Op<?>> end = artifacts(List.of("z"));
        PartialOrderGraph.Op pog = new SerPartialOrderGraph();
        List<Artifact.Op<?>> variantX = new ArrayList<>(shared);
        variantX.addAll(x);
        variantX.addAll(end);
        List<Artifact.Op<?>> variantY = new ArrayList<>(shared);
        variantY.addAll(y);
        variantY.addAll(end);
        pog.merge(variantX);
        pog.merge(variantY);

        // the variant with y shown: a file of those lines
        at.jku.isse.ecco.storage.ser.tree.SerNode file = new at.jku.isse.ecco.storage.ser.tree.SerNode(new SerArtifact<>(new Line("file")));
        for (Artifact.Op<?> artifact : variantY)
            file.addChild(new at.jku.isse.ecco.storage.ser.tree.SerNode(artifact));

        PartialOrderGraphView.Snapshot snapshot = PartialOrderGraphView.blocksOf(pog, file);

        assertEquals(List.of("Start", "1-2 (2)  Line[text=a1] \u2026 Line[text=a2]", "other  Line[text=x]",
                        "3-5 (3)  Line[text=y1] \u2026 Line[text=y3]", "6  Line[text=z]", "End"),
                snapshot.labels().stream().sorted(java.util.Comparator.comparing(l -> l.equals("Start") ? "0" : l.equals("End") ? "9" : l.startsWith("other") ? "2b" : l)).toList());
        int other = snapshot.labels().indexOf("other  Line[text=x]");
        assertEquals("other", snapshot.classes().get(other));
        String yDetails = snapshot.details().get(snapshot.labels().indexOf("3-5 (3)  Line[text=y1] \u2026 Line[text=y3]"));
        assertTrue(yDetails.startsWith("3 elements in this order, at 3-5 of what is shown"), yDetails);
        assertTrue(yDetails.contains("\nFirst: Line[text=y1]\nLast: Line[text=y3]\nOrder numbers: "), yDetails);
        assertTrue(snapshot.details().get(other).contains("only in other variants"));
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
        // start, the five lines as one block, end
        at.jku.isse.ecco.gui.FxTestSupport.waitUntil("the later graph", () -> view.drawnGraph().getNodeCount() == 3
                && view.drawnDetails().values().stream().anyMatch(d -> d.startsWith("5 elements")));
        Thread.sleep(300);
        assertEquals(3, view.drawnGraph().getNodeCount(), "the earlier graph is not drawn over it");
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
