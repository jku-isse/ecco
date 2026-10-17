package at.jku.isse.ecco.adapter.text;

import at.jku.isse.ecco.adapter.AssociationInfo;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.tree.RootNode;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TextViewer, headless (the gui module's FxTestSupport approach): built and laid out in a Scene on
 * the FX thread without a window, so the list cells exist and show their text and background.
 */
public class TextViewerTest {

    private final SerEntityFactory factory = new SerEntityFactory();

    @BeforeAll
    public static void startToolkit() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyStarted) {
            latch.countDown();
        }
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        Platform.setImplicitExit(false);
    }

    private static <T> T onFx(Callable<T> action) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                result.set(action.call());
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.countDown();
            }
        });
        assertTrue(done.await(10, TimeUnit.SECONDS), "the FX thread did not get to the action within 10 s");
        if (failure.get() instanceof Error error)
            throw error;
        if (failure.get() != null)
            throw new Exception(failure.get());
        return result.get();
    }

    /** Reads {@code files} (path -> content) into one association; returns the file nodes in order. */
    private List<Node.Op> association(String id, Map<String, String> files) throws IOException {
        Path dir = Files.createTempDirectory("text-viewer");
        List<Path> paths = new ArrayList<>();
        for (Map.Entry<String, String> file : files.entrySet()) {
            Path path = Path.of(file.getKey());
            Files.createDirectories(dir.resolve(path).getParent());
            Files.writeString(dir.resolve(path), file.getValue());
            paths.add(path);
        }
        List<Node.Op> fileNodes = new ArrayList<>();
        for (Path path : paths)
            fileNodes.addAll(new TextReader(factory).read(dir, new Path[]{path}));
        Association.Op association = factory.createAssociation(new LinkedHashSet<>(fileNodes));
        association.setId(id);
        for (Node.Op fileNode : fileNodes)
            for (Node.Op line : fileNode.getChildren())
                line.getArtifact().setContainingNode(line);
        return fileNodes;
    }

    private static class Info implements AssociationInfo {
        private final Association association;
        private final Map<String, Object> values = new HashMap<>();
        private final PropertyChangeSupport changes = new PropertyChangeSupport(this);

        Info(Association association, boolean selected, Color color) {
            this.association = association;
            values.put("selected", selected);
            values.put("color", color);
        }

        void set(String property, Object value) {
            Object old = values.put(property, value);
            changes.firePropertyChange(property, old, value);
        }

        int listenerCount() {
            return changes.getPropertyChangeListeners().length;
        }

        @Override
        public Association getAssociation() {
            return association;
        }

        @Override
        public void addPropertyChangeListener(PropertyChangeListener listener) {
            changes.addPropertyChangeListener(listener);
        }

        @Override
        public void removePropertyChangeListener(PropertyChangeListener listener) {
            changes.removePropertyChangeListener(listener);
        }

        @Override
        public Object getPropertyValue(String propertyName) {
            return values.get(propertyName);
        }
    }

    private static Association associationOf(Node node) {
        Node root = node;
        while (root.getParent() != null)
            root = root.getParent();
        return ((RootNode) root).getContainingAssociation();
    }

    /** Lays the viewer out in a scene and returns the visible cells of the shown list: text and fill. */
    private static List<String[]> cells(TextViewer viewer) throws Exception {
        return onFx(() -> {
            if (viewer.getScene() == null)
                new Scene(viewer, 400, 400);
            viewer.applyCss();
            viewer.layout();
            ListView<?> list = shownList(viewer);
            list.applyCss();
            list.layout();
            List<String[]> result = new ArrayList<>();
            for (javafx.scene.Node n : list.lookupAll(".list-cell")) {
                ListCell<?> cell = (ListCell<?>) n;
                if (cell.isEmpty() || cell.getItem() == null || !cell.isVisible())
                    continue;
                Paint fill = cell.getBackground() == null ? null : cell.getBackground().getFills().get(0).getFill();
                result.add(new String[]{String.valueOf(cell.getIndex()), cell.getText(), String.valueOf(fill)});
            }
            result.sort((a, b) -> Integer.compare(Integer.parseInt(a[0]), Integer.parseInt(b[0])));
            return result;
        });
    }

    private static ListView<?> shownList(TextViewer viewer) {
        if (viewer.getCenter() instanceof TabPane tabs)
            return (ListView<?>) tabs.getSelectionModel().getSelectedItem().getContent();
        return (ListView<?>) viewer.getCenter();
    }

    private static List<String> texts(List<String[]> cells) {
        return cells.stream().map(c -> c[1]).toList();
    }

    @Test
    public void showsOneRowPerLineOfASingleFileWithoutTabs() throws Exception {
        Node.Op file = association("A", Map.of("a.txt", "one\n\ttwo  \nthree\n")).get(0);
        TextViewer viewer = onFx(() -> {
            TextViewer v = new TextViewer();
            v.showTree(file);
            return v;
        });
        assertInstanceOf(ListView.class, viewer.getCenter());
        // the final newline marker is shown as a row of its own
        assertEquals(List.of("one", "\ttwo  ", "three", "(final newline)", "(format: UTF-8, LF)"), texts(cells(viewer)));
        assertEquals(TextPlugin.class.getName(), viewer.getPluginId());
    }

    @Test
    public void emptyTreeShowsAnEmptyList() throws Exception {
        Node.Op notAFile = factory.createNode(new LineArtifactData("loose"));
        TextViewer viewer = onFx(() -> {
            TextViewer v = new TextViewer();
            v.showTree(notAFile);
            return v;
        });
        assertEquals(0, ((ListView<?>) viewer.getCenter()).getItems().size());
    }

    @Test
    public void severalFilesGetATabEachAndTheSelectedLineIsHighlighted() throws Exception {
        Map<String, String> files = new java.util.LinkedHashMap<>();
        files.put("a.txt", "a1\na2\n");
        files.put("b.txt", "b1\nb2\nb3");
        List<Node.Op> fileNodes = association("A", files);
        Node.Op b2 = fileNodes.get(1).getChildren().get(1);
        TextViewer viewer = onFx(() -> {
            TextViewer v = new TextViewer();
            v.showTree(b2);
            return v;
        });
        TabPane tabs = assertInstanceOf(TabPane.class, viewer.getCenter());
        assertEquals(List.of("a.txt", "b.txt"), tabs.getTabs().stream().map(Tab::getText).toList());
        assertEquals("b.txt", tabs.getSelectionModel().getSelectedItem().getText());
        List<String[]> cells = cells(viewer);
        assertEquals(List.of("b1", "b2", "b3", "(format: UTF-8, LF)"), texts(cells));
        assertEquals(Color.YELLOW.toString(), cells.get(1)[2]);
        assertEquals(Color.WHITE.toString(), cells.get(0)[2]);
    }

    @Test
    public void withoutSelectionHighlightingTheSelectedLineIsNotYellow() throws Exception {
        Node.Op file = association("A", Map.of("a.txt", "x\ny\n")).get(0);
        Node.Op y = file.getChildren().get(1);
        TextViewer viewer = onFx(() -> {
            TextViewer v = new TextViewer();
            v.setHighlightSelection(false);
            v.showTree(y);
            return v;
        });
        assertEquals(Color.WHITE.toString(), cells(viewer).get(1)[2]);
    }

    @Test
    public void linesTakeTheirSelectedAssociationsColorAndFollowItsChanges() throws Exception {
        Node.Op file = association("A", Map.of("a.txt", "x\ny\n")).get(0);
        Info info = new Info(associationOf(file), true, Color.RED);
        TextViewer viewer = onFx(() -> {
            TextViewer v = new TextViewer();
            v.setAssociationInfos(List.of(info));
            v.showTree(file);
            return v;
        });
        assertEquals(Color.RED.toString(), cells(viewer).get(0)[2]);

        onFx(() -> {
            info.set("color", Color.BLUE);
            return null;
        });
        assertEquals(Color.BLUE.toString(), cells(viewer).get(0)[2]);

        // other properties and unselected associations are ignored
        onFx(() -> {
            info.set("other", "x");
            info.set("selected", false);
            info.set("color", Color.GREEN);
            return null;
        });
        assertEquals(Color.BLUE.toString(), cells(viewer).get(0)[2]);
    }

    @Test
    public void unselectedOrTransparentAssociationsLeaveLinesWhite() throws Exception {
        Node.Op file = association("A", Map.of("a.txt", "x\n")).get(0);
        for (Info info : List.of(new Info(associationOf(file), false, Color.RED), new Info(associationOf(file), true, Color.TRANSPARENT))) {
            TextViewer viewer = onFx(() -> {
                TextViewer v = new TextViewer();
                v.setAssociationInfos(List.of(info));
                v.showTree(file);
                return v;
            });
            assertEquals(Color.WHITE.toString(), cells(viewer).get(0)[2]);
        }
    }

    @Test
    public void replacingTheAssociationInfosRemovesTheOldListeners() throws Exception {
        Node.Op file = association("A", Map.of("a.txt", "x\n")).get(0);
        Info info = new Info(associationOf(file), true, Color.RED);
        TextViewer viewer = onFx(() -> {
            TextViewer v = new TextViewer();
            v.setAssociationInfos(List.of(info));
            return v;
        });
        assertEquals(1, info.listenerCount());
        onFx(() -> {
            viewer.setAssociationInfos(null);
            return null;
        });
        assertEquals(0, info.listenerCount());
    }

    @Test
    public void showingAgainKeepsTheTabsAndMovesTheHighlight() throws Exception {
        Map<String, String> files = new java.util.LinkedHashMap<>();
        files.put("a.txt", "a1\na2\n");
        files.put("b.txt", "b1\n");
        List<Node.Op> fileNodes = association("A", files);
        TextViewer viewer = onFx(() -> {
            TextViewer v = new TextViewer();
            v.showTree(fileNodes.get(1).getChildren().get(0));
            v.showTree(fileNodes.get(0).getChildren().get(1));
            return v;
        });
        TabPane tabs = (TabPane) viewer.getCenter();
        assertEquals("a.txt", tabs.getSelectionModel().getSelectedItem().getText());
        assertEquals(Color.YELLOW.toString(), cells(viewer).get(1)[2]);
    }

    /**
     * Only the file shown is read into rows, the others when their tab is first selected: every file
     * of the tree was, on every click - for a commit with many files the whole commit.
     */
    @Test
    public void onlyTheFileShownIsBuiltTheOthersWhenSelected() throws Exception {
        Map<String, String> files = new java.util.LinkedHashMap<>();
        files.put("a.txt", "a1\n");
        files.put("b.txt", "b1\nb2\n");
        files.put("c.txt", "c1\n");
        List<Node.Op> fileNodes = association("A", files);
        Node.Op b1 = fileNodes.stream().filter(f -> f.getArtifact().getData().toString().contains("b.txt")).findFirst().orElseThrow().getChildren().get(0);
        TextViewer viewer = onFx(() -> {
            TextViewer v = new TextViewer();
            v.showTree(b1);
            return v;
        });
        TabPane tabs = (TabPane) viewer.getCenter();
        assertEquals("b.txt", onFx(() -> tabs.getSelectionModel().getSelectedItem().getText()));
        assertEquals(List.of("b1", "b2", "(final newline)", "(format: UTF-8, LF)"), texts(cells(viewer)));
        Tab a = onFx(() -> tabs.getTabs().stream().filter(t -> t.getText().equals("a.txt")).findFirst().orElseThrow());
        Tab c = onFx(() -> tabs.getTabs().stream().filter(t -> t.getText().equals("c.txt")).findFirst().orElseThrow());
        assertFalse(onFx(() -> built(a)), "a.txt not built yet");
        assertFalse(onFx(() -> built(c)), "c.txt not built yet");

        onFx(() -> {
            tabs.getSelectionModel().select(c);
            return null;
        });
        assertEquals(List.of("c1", "(final newline)", "(format: UTF-8, LF)"), texts(cells(viewer)), "built when selected");
        assertFalse(onFx(() -> built(a)), "a.txt still not");
    }

    /** Whether [tab]'s rows were built: it has none, or no content at all, until it is first shown. */
    private static boolean built(Tab tab) {
        return tab.getContent() instanceof ListView<?> list && !list.getItems().isEmpty();
    }

    /**
     * Two files of the same name in different folders get a tab each, named by their paths - they
     * shared one, only the last shown, and a line of the first was looked for in the other.
     */
    @Test
    public void sameNamedFilesInDifferentFoldersGetATabEach() throws Exception {
        Map<String, String> files = new java.util.LinkedHashMap<>();
        files.put("x/a.txt", "from x\n");
        files.put("y/a.txt", "from y\n");
        List<Node.Op> fileNodes = association("A", files);
        Node.Op fromX = fileNodes.stream().filter(f -> f.getArtifact().getData().toString().contains("x")).findFirst().orElseThrow();
        TextViewer viewer = onFx(() -> {
            TextViewer v = new TextViewer();
            v.showTree(fromX.getChildren().get(0));
            return v;
        });
        TabPane tabs = assertInstanceOf(TabPane.class, viewer.getCenter(), "a tab each");
        assertEquals(java.util.Set.of(Path.of("x", "a.txt").toString(), Path.of("y", "a.txt").toString()),
                onFx(() -> tabs.getTabs().stream().map(Tab::getText).collect(java.util.stream.Collectors.toSet())));
        assertEquals("from x", texts(cells(viewer)).get(0), "the file of the line shown");
        assertEquals(Color.YELLOW.toString(), cells(viewer).get(0)[2], "and the line marked");
    }
}
