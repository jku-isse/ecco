package at.jku.isse.ecco.gui.view.artifacts;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.tree.Node;
import javafx.beans.value.WritableValue;
import javafx.scene.Scene;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableRow;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static at.jku.isse.ecco.gui.FxTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * What the artifact tree table shows for a tree: every node, expanded, with its ordered/atomic/
 * unique flags (hidden at first), sequence number and association, and the association's highlight
 * color. Its "Selected" column, which marked an artifact for extraction that nothing extracted, is gone.
 */
public class ArtifactTreeTableViewTest {

    @BeforeAll
    public static void start() throws InterruptedException {
        startToolkit();
    }

    /** Every node of the tree gets an item, all expanded; null empties the table. */
    @Test
    @Timeout(60)
    public void listsEveryNodeOfTheTreeExpanded(@TempDir Path tmp) throws Exception {
        try (EccoService service = twoFeatures(tmp)) {
            Association a = association(service, "a.txt");
            ArtifactTreeTableView view = shown(service, a.getRootNode());

            List<TreeItem<ArtifactTreeTableView.NodeWrapper>> items = onFx(() -> items(view.getRoot()));
            List<Node> nodes = new ArrayList<>();
            a.getRootNode().traverse(nodes::add);
            assertEquals(nodes.stream().map(String::valueOf).toList(), items.stream().map(i -> String.valueOf(i.getValue())).toList());
            assertTrue(items.stream().allMatch(i -> i.isLeaf() || i.isExpanded()), "everything is expanded");
            assertTrue(onFx(() -> items.stream().filter(TreeItem::isLeaf).allMatch(i -> i.getValue().getChildren().isEmpty())));

            onFx(() -> view.setRootNode(null));
            assertNull(onFx(() -> view.getRoot()));
        }
    }

    /**
     * A large tree is shown at once, opened only so far: up to {@link ArtifactTreeTableView#MAX_EXPANDED_ROWS}
     * rows, breadth first; the rest opens on a click. Opened all through, each row added one by one
     * made the table walk every opened row again - a tinycc commit (57,000 artifacts) took 100 s on
     * the FX thread, Select All in the Artifacts tab about 10 minutes.
     */
    @Test
    @Timeout(120)
    public void aLargeTreeIsShownAtOnceOpenedOnlySoFar(@TempDir Path tmp) throws Exception {
        StringBuilder lines = new StringBuilder();
        for (int i = 0; i < 20_000; i++)
            lines.append("line ").append(i).append('\n');
        try (EccoService service = repository(tmp.resolve("repo"))) {
            commit(service, tmp.resolve("v1"), "A.1", java.util.Map.of("big.txt", lines.toString()));
            Association big = service.getRepository().getAssociations().stream()
                    .max(java.util.Comparator.comparingInt(a -> a.getRootNode().countArtifacts())).orElseThrow();
            ArtifactTreeTableView view = onFx(() -> new ArtifactTreeTableView(service));

            long start = System.nanoTime();
            onFx(() -> view.setRootNode(big.getRootNode()));
            read(view);
            long millis = (System.nanoTime() - start) / 1_000_000;

            assertTrue(millis < 2_000, "shown in " + millis + " ms");
            int shown = onFx(view::getExpandedItemCount);
            assertTrue(shown <= ArtifactTreeTableView.MAX_EXPANDED_ROWS + 1, shown + " rows open");
            // the file's lines are there - one click away
            TreeItem<ArtifactTreeTableView.NodeWrapper> file = onFx(() -> items(view.getRoot()).stream()
                    .filter(i -> String.valueOf(i.getValue()).startsWith("big.txt")).findFirst().orElseThrow());
            onFx(() -> file.setExpanded(true));
            // read when opened, in the background
            waitUntil("the lines", () -> !file.getChildren().isEmpty());
            assertEquals(20_000 + 2, (int) onFx(() -> file.getChildren().size()), "every line, the final newline and the format");
        }
    }

    /**
     * The tree's internals are hidden at first - they took three quarters of the width, and the code
     * was cut off; the table menu shows them.
     */
    @Test
    @Timeout(60)
    public void theInternalsAreHiddenAtFirst() throws Exception {
        // no tree is shown: no repository is read
        ArtifactTreeTableView view = onFx(() -> new ArtifactTreeTableView(null));
        for (String hidden : List.of("Ordered", "Atomic", "Unique", "Sequence Number"))
            assertFalse(onFx(() -> column(view, hidden).isVisible()), hidden);
        for (String shown : List.of("Node", "Association"))
            assertTrue(onFx(() -> column(view, shown).isVisible()), shown);
    }

    /** The flag/sequence/association columns read the node's artifact; a node without one shows defaults. */
    @Test
    @Timeout(60)
    public void columnsShowTheArtifactsFlagsAndAssociation(@TempDir Path tmp) throws Exception {
        try (EccoService service = twoFeatures(tmp)) {
            Association a = association(service, "a.txt");
            ArtifactTreeTableView view = shown(service, a.getRootNode());
            TreeItem<ArtifactTreeTableView.NodeWrapper> root = onFx(() -> view.getRoot());
            TreeItem<ArtifactTreeTableView.NodeWrapper> file = onFx(() -> item(view, "a.txt"));
            TreeItem<ArtifactTreeTableView.NodeWrapper> line = onFx(() -> item(view, "a1"));

            assertEquals(Boolean.TRUE, onFx(() -> value(view, "Ordered", file)), "a text file orders its lines");
            assertEquals(Boolean.FALSE, onFx(() -> value(view, "Ordered", line)));
            assertEquals(Boolean.FALSE, onFx(() -> value(view, "Ordered", root)), "no artifact");
            assertEquals(line.getValue().isAtomic(), onFx(() -> value(view, "Atomic", line)));
            assertEquals(Boolean.TRUE, onFx(() -> value(view, "Unique", line)));
            assertEquals(line.getValue().getArtifact().getSequenceNumber(), onFx(() -> value(view, "Sequence Number", line)));
            assertEquals(-1, onFx(() -> value(view, "Sequence Number", root)));
            assertEquals(a.getId(), onFx(() -> value(view, "Association", line)));
            assertEquals("null", onFx(() -> value(view, "Association", root)));
        }
    }

    /** A node of a known association takes on (and follows) that association's highlight color; others stay transparent. */
    @Test
    @Timeout(60)
    public void nodesFollowTheirAssociationsColor(@TempDir Path tmp) throws Exception {
        try (EccoService service = twoFeatures(tmp)) {
            Association a = association(service, "a.txt");
            AssociationInfoImpl info = new AssociationInfoImpl(a);
            ArtifactTreeTableView view = onFx(() -> {
                ArtifactTreeTableView v = new ArtifactTreeTableView(service);
                v.setAssociationInfo(List.of(info));
                v.setRootNode(a.getRootNode());
                return v;
            });
            read(view);
            List<TreeItem<ArtifactTreeTableView.NodeWrapper>> items = onFx(() -> items(view.getRoot()));

            onFx(() -> info.colorProperty().set(Color.RED));
            for (TreeItem<ArtifactTreeTableView.NodeWrapper> item : items) {
                Color expected = item.getValue().getArtifact() == null ? Color.TRANSPARENT : Color.RED;
                assertEquals(expected, item.getValue().colorProperty().get(), String.valueOf(item.getValue()));
            }

            // in b.txt's tree only the directory, whose artifact a.txt's association owns, takes the color
            Association b = association(service, "b.txt");
            onFx(() -> view.setRootNode(b.getRootNode()));
            read(view);
            for (TreeItem<ArtifactTreeTableView.NodeWrapper> item : onFx(() -> items(view.getRoot()))) {
                Artifact<?> artifact = item.getValue().getArtifact();
                boolean owned = artifact != null && artifact.getContainingNode().getContainingAssociation() == a;
                assertEquals(owned ? Color.RED : Color.TRANSPARENT, item.getValue().colorProperty().get(), String.valueOf(item.getValue()));
            }
            assertTrue(onFx(() -> items(view.getRoot()).stream().anyMatch(i -> Color.TRANSPARENT.equals(i.getValue().colorProperty().get())
                    && i.getValue().getArtifact() != null)), "b.txt's own artifacts stay uncolored");
        }
    }

    /** A rendered row is styled unique or non-unique by its node, never both. */
    @Test
    @Timeout(60)
    public void renderedRowsAreStyledByUniqueness(@TempDir Path tmp) throws Exception {
        try (EccoService service = twoFeatures(tmp)) {
            // b.txt's directory node is shared with a.txt's association, so not unique
            Association b = association(service, "b.txt");
            AssociationInfoImpl info = new AssociationInfoImpl(b);
            info.colorProperty().set(Color.YELLOW);
            ArtifactTreeTableView read = onFx(() -> {
                ArtifactTreeTableView v = new ArtifactTreeTableView(service);
                v.setAssociationInfo(List.of(info));
                v.setRootNode(b.getRootNode());
                return v;
            });
            read(read);
            ArtifactTreeTableView view = onFx(() -> {
                ArtifactTreeTableView v = read;
                new Scene(v, 900, 600);
                v.applyCss();
                v.layout();
                // a selection change re-renders the rows
                v.getSelectionModel().select(1);
                v.getSelectionModel().select(2);
                v.layout();
                return v;
            });

            List<TreeTableRow<?>> rows = onFx(() -> view.lookupAll(".tree-table-row-cell").stream()
                    .<TreeTableRow<?>>map(n -> (TreeTableRow<?>) n).filter(r -> r.getItem() != null).toList());
            assertTrue(rows.size() >= 4, "the rows were rendered: " + rows.size());
            boolean sawNonUnique = false;
            for (TreeTableRow<?> row : rows) {
                ArtifactTreeTableView.NodeWrapper node = (ArtifactTreeTableView.NodeWrapper) row.getItem();
                List<String> classes = onFx(() -> List.copyOf(row.getStyleClass()));
                assertEquals(node.isUnique(), classes.contains("uniquerow"), node + ": " + classes);
                assertEquals(!node.isUnique(), classes.contains("nonuniquerow"), node + ": " + classes);
                sawNonUnique |= !node.isUnique();
            }
            assertTrue(sawNonUnique, "the shared directory node is shown");
        }
    }

    /** The wrapper the table holds answers like the node it wraps. */
    @Test
    @Timeout(60)
    public void theWrapperDelegatesToItsNode(@TempDir Path tmp) throws Exception {
        try (EccoService service = twoFeatures(tmp)) {
            Node file = association(service, "a.txt").getRootNode().getChildren().get(0).getChildren().get(0);
            ArtifactTreeTableView view = onFx(() -> new ArtifactTreeTableView(service));
            ArtifactTreeTableView.NodeWrapper wrapper = view.new NodeWrapper(file);

            assertSame(file, wrapper.getNode());
            assertEquals(file.toString(), wrapper.toString());
            assertEquals(file.getNodeString(), wrapper.getNodeString());
            assertEquals(file.hashCode(), wrapper.hashCode());
            assertTrue(wrapper.equals(file));
            assertSame(file.getParent(), wrapper.getParent());
            assertSame(file.getContainingAssociation(), wrapper.getContainingAssociation());
            assertEquals(file.getChildren(), wrapper.getChildren());
            assertEquals(file.countArtifacts(), wrapper.countArtifacts());
            assertEquals(file.computeDepth(), wrapper.computeDepth());
            assertEquals(file.countArtifactsPerDepth(), wrapper.countArtifactsPerDepth());
            List<Node> visited = new ArrayList<>();
            wrapper.traverse(visited::add);
            assertEquals(file.countArtifacts(), visited.size());

            wrapper.putProperty("test.key", "value");
            assertEquals("value", file.<String>getProperty("test.key").orElseThrow());
            assertEquals(file.getProperties(), wrapper.getProperties());
            assertEquals("value", wrapper.<String>getProperty("test.key").orElseThrow());
            wrapper.removeProperty("test.key");
            assertTrue(file.getProperty("test.key").isEmpty());
        }
    }

    /**
     * A tree is read in the background, holding the service's lock, a level at a time: while a commit
     * holds it, the FX thread goes on (the items and cells read the live tree there, while the commit
     * changed it) and the tree shown before stays; released, the tree is shown - and a level opened
     * while the lock is held is read once it is released.
     */
    @Test
    @Timeout(60)
    public void theTreeIsReadOffTheFxThreadHoldingTheLock(@TempDir Path tmp) throws Exception {
        try (EccoService service = twoFeatures(tmp)) {
            Association a = association(service, "a.txt");
            ArtifactTreeTableView view = onFx(() -> new ArtifactTreeTableView(service));
            java.util.concurrent.CountDownLatch locked = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
            Thread commitLike = new Thread(() -> {
                synchronized (service) {
                    locked.countDown();
                    try {
                        release.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            });
            commitLike.start();
            try {
                assertTrue(locked.await(10, java.util.concurrent.TimeUnit.SECONDS));
                // onFx fails if the FX thread does not get to it within 10 s
                onFx(() -> view.setRootNode(a.getRootNode()));
                Thread.sleep(300);
                assertTrue(onFx(view::isReading), "not read while the lock is held");
                assertNull(onFx(view::getRoot));

                release.countDown();
                read(view);
                List<Node> nodes = new ArrayList<>();
                a.getRootNode().traverse(nodes::add);
                assertEquals(nodes.stream().map(String::valueOf).toList(), onFx(() -> items(view.getRoot())).stream().map(i -> String.valueOf(i.getValue())).toList());
            } finally {
                release.countDown();
                commitLike.join(10_000);
            }

            // a level not read yet: opened while the lock is held, read once it is released
            TreeItem<ArtifactTreeTableView.NodeWrapper> file = onFx(() -> item(view, "a.txt"));
            ArtifactTreeTableView.NodeTreeItem unread = onFx(() -> view.new NodeTreeItem(file.getValue()));
            java.util.concurrent.CountDownLatch lockedAgain = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.CountDownLatch releaseAgain = new java.util.concurrent.CountDownLatch(1);
            Thread again = new Thread(() -> {
                synchronized (service) {
                    lockedAgain.countDown();
                    try {
                        releaseAgain.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            });
            again.start();
            try {
                assertTrue(lockedAgain.await(10, java.util.concurrent.TimeUnit.SECONDS));
                assertTrue(onFx(() -> unread.getChildren().isEmpty()), "none until read");
                releaseAgain.countDown();
                waitUntil("the level", () -> !unread.getChildren().isEmpty());
                assertEquals(file.getValue().getNode().getChildren().stream().map(String::valueOf).toList(),
                        onFx(() -> unread.getChildren().stream().map(i -> String.valueOf(i.getValue())).toList()));
            } finally {
                releaseAgain.countDown();
                again.join(10_000);
            }
        }
    }

    // --- helpers -------------------------------------------------------------------------------------

    /** A table showing [root] - read in the background, holding the service's lock. */
    private static ArtifactTreeTableView shown(EccoService service, at.jku.isse.ecco.tree.RootNode root) throws Exception {
        ArtifactTreeTableView view = onFx(() -> new ArtifactTreeTableView(service, root));
        read(view);
        return view;
    }

    /** Waits until the tree last set is read and shown. */
    static void read(ArtifactTreeTableView view) throws Exception {
        waitUntil("the tree is read", () -> !view.isReading());
    }

    /** A repository with a.txt for A and b.txt added for B. */
    static EccoService twoFeatures(Path tmp) throws Exception {
        EccoService service = repository(tmp.resolve("repo"));
        commit(service, tmp.resolve("v1"), "A.1", Map.of("a.txt", "a1\na2\n"));
        commit(service, tmp.resolve("v2"), "A.1, B.1", Map.of("a.txt", "a1\na2\n", "b.txt", "b1\n"));
        return service;
    }

    /** The association whose artifacts include the file {@code name}. */
    static Association association(EccoService service, String name) {
        return service.getRepository().getAssociations().stream().filter(a -> {
            List<String> labels = new ArrayList<>();
            a.getRootNode().traverse(n -> labels.add(String.valueOf(n)));
            return labels.stream().anyMatch(l -> l.startsWith(name + " "));
        }).findFirst().orElseThrow();
    }

    private static List<TreeItem<ArtifactTreeTableView.NodeWrapper>> items(TreeItem<ArtifactTreeTableView.NodeWrapper> item) {
        List<TreeItem<ArtifactTreeTableView.NodeWrapper>> all = new ArrayList<>();
        all.add(item);
        for (TreeItem<ArtifactTreeTableView.NodeWrapper> child : item.getChildren())
            all.addAll(items(child));
        return all;
    }

    private static TreeItem<ArtifactTreeTableView.NodeWrapper> item(ArtifactTreeTableView view, String label) {
        return items(view.getRoot()).stream().filter(i -> String.valueOf(i.getValue()).equals(label)
                || String.valueOf(i.getValue()).startsWith(label + " ")).findFirst().orElseThrow();
    }

    private static TreeTableColumn<ArtifactTreeTableView.NodeWrapper, ?> column(ArtifactTreeTableView view, String text) {
        return view.getColumns().stream().filter(c -> text.equals(c.getText())).findFirst().orElseThrow();
    }

    private static Object value(ArtifactTreeTableView view, String column, TreeItem<ArtifactTreeTableView.NodeWrapper> item) {
        return column(view, column).getCellObservableValue(item).getValue();
    }
}
