package at.jku.isse.ecco.adapter.java.view;

import at.jku.isse.ecco.adapter.view.JavaCodeLine;
import at.jku.isse.ecco.adapter.AssociationInfo;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.adapter.java.JavaPlugin;
import at.jku.isse.ecco.adapter.java.data.LineArtifactData;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.tree.Node;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Java viewer shows each file of the tree in a tab of its own, the one with the node shown
 * selected and only it rendered; lines are coloured by their association while it is selected.
 */
public class JavaCodeViewerTest {

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
        if (failure.get() != null)
            throw new Exception(failure.get());
        return result.get();
    }

    /** A Java file of one line [line] in association [association]. */
    private Node.Op file(String path, String line, Association.Op association) {
        Node.Op file = this.factory.createNode(new PluginArtifactData(JavaPlugin.class.getName(), Path.of(path)));
        Node.Op lineNode = this.factory.createNode(new LineArtifactData(line));
        file.addChild(lineNode);
        association.getRootNode().addChild(file);
        lineNode.getArtifact().setContainingNode(lineNode);
        return file;
    }

    private static TabPane tabs(JavaCodeViewer viewer) {
        return (TabPane) ((SplitPane) viewer.getCenter()).getItems().get(0);
    }

    @SuppressWarnings("unchecked")
    private static ListView<JavaCodeLine> list(Tab tab) {
        return (ListView<JavaCodeLine>) tab.getContent();
    }

    @Test
    public void filesAreTabsRenderedWhenShownAndColouredByTheirAssociation() throws Exception {
        Node.Op seed = this.factory.createNode(new LineArtifactData("seed"));
        Association.Op red = this.factory.createAssociation(new LinkedHashSet<>(List.of(seed)));
        red.setId("red");
        red.getRootNode().removeChild(seed);
        Node.Op a = this.file("A.java", "int a = 1", red);
        Node.Op b = this.file("B.java", "int b = 2", red);
        Info info = new Info(red, Color.RED);

        JavaCodeViewer viewer = onFx(() -> {
            JavaCodeViewer v = new JavaCodeViewer();
            new Scene(v, 400, 300);
            v.setAssociationInfos(List.of(info));
            v.showTree(b.getChildren().get(0));
            return v;
        });
        TabPane tabs = tabs(viewer);
        assertEquals(List.of("A.java", "B.java"), onFx(() -> tabs.getTabs().stream().map(Tab::getText).toList()));
        Tab tabA = tabs.getTabs().get(0), tabB = tabs.getTabs().get(1);
        assertSame(tabB, onFx(() -> tabs.getSelectionModel().getSelectedItem()), "the file of the node shown");
        assertNull(onFx(tabA::getContent), "A.java not rendered yet");
        JavaCodeLine lineB = onFx(() -> list(tabB).getItems().get(0));
        assertEquals("int b = 2;", lineB.getText());
        assertEquals(Color.RED, lineB.backgroundColor().get());

        onFx(() -> {
            tabs.getSelectionModel().select(tabA);
            return null;
        });
        JavaCodeLine lineA = onFx(() -> list(tabA).getItems().get(0));
        assertEquals("int a = 1;", lineA.getText(), "rendered when selected");

        onFx(() -> {
            info.set("color", Color.GREEN);
            return null;
        });
        assertEquals(Color.GREEN, lineA.backgroundColor().get(), "a colour change reaches every file rendered");
        assertEquals(Color.GREEN, lineB.backgroundColor().get());

        onFx(() -> {
            viewer.setAssociationInfos(null);
            return null;
        });
        assertEquals(0, info.listenerCount(), "no longer listened to");
    }

    private static class Info implements AssociationInfo {
        private final Association association;
        private final Map<String, Object> values = new HashMap<>();
        private final PropertyChangeSupport changes = new PropertyChangeSupport(this);

        Info(Association association, Color color) {
            this.association = association;
            values.put("selected", true);
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
}
