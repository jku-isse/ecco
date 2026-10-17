package at.jku.isse.ecco.adapter.view;

import at.jku.isse.ecco.adapter.AssociationInfo;
import at.jku.isse.ecco.adapter.dispatch.DirectoryArtifactData;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.tree.Node;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** What the code viewers share: the files of a tree, a tab for each made when first shown, and the associations' colours. */
public class FileTabsTest {

    private static final String PLUGIN = "test.Plugin";

    private final EntityFactory ef = new SerEntityFactory();

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

    private Node.Op file(Node.Op parent, String path, String pluginId) {
        Node.Op file = this.ef.createNode(new PluginArtifactData(pluginId, Path.of(path)));
        parent.addChild(file);
        return file;
    }

    private Node.Op directory(Node.Op parent, String path) {
        Node.Op directory = this.ef.createNode(new DirectoryArtifactData(Path.of(path)));
        parent.addChild(directory);
        return directory;
    }

    @Test
    public void filesAreThoseOfThePluginInTheWholeTreeNotInAFile() {
        Node.Op root = this.ef.createRootNode();
        Node.Op a = this.file(root, "a.c", PLUGIN);
        Node.Op dir = this.directory(root, "src");
        Node.Op b = this.file(dir, "src/b.c", PLUGIN);
        this.file(dir, "src/c.txt", "other.Plugin");
        // a file inside a file is not walked into
        this.file(b, "src/b.c/inner", PLUGIN);
        Node.Op line = this.ef.createNode(new DirectoryArtifactData(Path.of("line")));
        b.addChild(line);

        assertEquals(List.of(a, b), FileTabs.files(line, PLUGIN));
        assertEquals(b, FileTabs.fileOf(line, PLUGIN));
        assertEquals(b, FileTabs.fileOf(b, PLUGIN));
        assertNull(FileTabs.fileOf(dir, PLUGIN));
        assertEquals(Path.of("src/b.c").toString(), FileTabs.label(b));
    }

    @Test
    public void oneItemIsShownWithoutTabsNoneByTheEmptyContent() {
        Label empty = new Label("empty");
        assertSame(empty, FileTabs.show(List.<String>of(), null, s -> s, Label::new, () -> empty));
        assertEquals("a", ((Label) FileTabs.show(List.of("a"), null, s -> s, Label::new, () -> empty)).getText());
    }

    @Test
    public void severalAreTabsTheirContentMadeWhenFirstSelected() {
        List<String> made = new ArrayList<>();
        TabPane tabs = (TabPane) FileTabs.show(List.of("a", "b", "c"), "b", s -> s.toUpperCase(), s -> {
            made.add(s);
            return new Label(s);
        }, Label::new);

        assertEquals(List.of("A", "B", "C"), tabs.getTabs().stream().map(Tab::getText).toList());
        assertEquals("B", tabs.getSelectionModel().getSelectedItem().getText());
        assertEquals(List.of("b"), made);
        assertNull(tabs.getTabs().get(0).getContent());

        tabs.getSelectionModel().select(2);
        tabs.getSelectionModel().select(1);
        tabs.getSelectionModel().select(2);
        assertEquals(List.of("b", "c"), made, "made once, when first selected");
        assertEquals("c", ((Label) tabs.getTabs().get(2).getContent()).getText());
    }

    @Test
    public void anItemNotAmongThemSelectsTheFirst() {
        TabPane tabs = (TabPane) FileTabs.show(List.of("a", "b"), "x", s -> s, Label::new, Label::new);
        assertEquals("a", tabs.getSelectionModel().getSelectedItem().getText());
        assertNotNull(tabs.getTabs().get(0).getContent());
    }

    @Test
    public void colorsAreThoseOfSelectedAssociationsAndChangesAreTold() {
        Association.Op selected = this.ef.createAssociation();
        selected.setId("s");
        Association.Op notSelected = this.ef.createAssociation();
        notSelected.setId("n");
        Association.Op transparent = this.ef.createAssociation();
        transparent.setId("t");
        Info s = new Info(selected, true, Color.RED);
        Info n = new Info(notSelected, false, Color.BLUE);
        Info t = new Info(transparent, true, Color.TRANSPARENT);

        Map<String, Color> told = new HashMap<>();
        AssociationColors colors = new AssociationColors(told::put);
        colors.set(List.of(s, n, t));

        assertEquals(Color.RED, colors.colorFor(selected));
        assertEquals(AssociationColors.NONE, colors.colorFor(notSelected));
        assertEquals(AssociationColors.NONE, colors.colorFor(transparent));
        assertEquals(AssociationColors.NONE, colors.colorFor((Association) null));

        s.set("color", Color.GREEN);
        n.set("color", Color.YELLOW);
        assertEquals(Map.of("s", Color.GREEN), told, "only a selected association's change");
        assertEquals(Color.GREEN, colors.colorFor(selected));

        colors.set(null);
        assertEquals(0, s.listenerCount(), "no longer listened to");
        assertEquals(AssociationColors.NONE, colors.colorFor(selected));
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
}
