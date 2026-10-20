package at.jku.isse.ecco.gui.view.artifacts;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.service.EccoService;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TreeItem;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static at.jku.isse.ecco.gui.FxTestSupport.*;
import static at.jku.isse.ecco.gui.view.artifacts.ArtifactTreeTableViewTest.association;
import static at.jku.isse.ecco.gui.view.artifacts.ArtifactTreeTableViewTest.twoFeatures;
import static org.junit.jupiter.api.Assertions.*;

/**
 * What the artifact tree pane shows next to the tree: the first node a viewer exists for (without a
 * click), the node clicked in the tree, and the tab that was open before a recompose.
 */
public class ArtifactTreeViewTest {

    @BeforeAll
    public static void start() throws InterruptedException {
        startToolkit();
    }

    /** A new tree shows the first node with a registered viewer (the text file) in the detail tabs. */
    @Test
    @Timeout(60)
    public void aNewTreeShowsTheFirstNodeWithAViewer(@TempDir Path tmp) throws Exception {
        try (EccoService service = twoFeatures(tmp)) {
            Association a = association(service, "a.txt");
            ArtifactTreeView view = onFx(() -> new ArtifactTreeView(service));

            onFx(() -> view.setRootNode(a.getRootNode()));
            read(view);

            assertEquals(List.of("Code", "Order graph", "Info"), onFx(() -> tabNames(view)));
            assertEquals("Code", onFx(() -> tabPane(view).getSelectionModel().getSelectedItem().getText()), "it opens on the code");
            assertTrue(onFx(() -> infoText(view)).startsWith("a.txt "), "the file, not the root: " + onFx(() -> infoText(view)));
            assertNotNull(onFx(() -> table(view).getRoot()));
        }
    }

    /** Clicking a node in the tree shows that node. */
    @Test
    @Timeout(60)
    public void selectingATreeItemShowsThatNode(@TempDir Path tmp) throws Exception {
        try (EccoService service = twoFeatures(tmp)) {
            Association a = association(service, "a.txt");
            ArtifactTreeView view = onFx(() -> new ArtifactTreeView(service));
            onFx(() -> view.setRootNode(a.getRootNode()));
            read(view);

            onFx(() -> {
                ArtifactTreeTableView table = table(view);
                TreeItem<ArtifactTreeTableView.NodeWrapper> line = table.getRoot().getChildren().get(0).getChildren().get(0).getChildren().get(1);
                table.getSelectionModel().select(line);
            });
            read(view);

            assertTrue(onFx(() -> infoText(view)).startsWith("a2\n"), onFx(() -> infoText(view)));
            assertEquals(List.of("Code", "Info"), onFx(() -> tabNames(view)), "a line has no order graph");
        }
    }

    /** A recompose keeps the detail tab the user had open; an empty tree clears the details. */
    @Test
    @Timeout(60)
    public void aRecomposeKeepsTheSelectedTab(@TempDir Path tmp) throws Exception {
        try (EccoService service = twoFeatures(tmp)) {
            ArtifactTreeView view = onFx(() -> new ArtifactTreeView(service));
            onFx(() -> view.setRootNode(association(service, "a.txt").getRootNode()));
            read(view);
            onFx(() -> tabPane(view).getSelectionModel().select(tab(view, "Code")));

            onFx(() -> view.setRootNode(association(service, "b.txt").getRootNode()));
            read(view);

            assertEquals("Code", onFx(() -> tabPane(view).getSelectionModel().getSelectedItem().getText()));
            assertTrue(onFx(() -> infoText(view)).startsWith("b.txt "));

            onFx(() -> view.setRootNode(null));
            assertEquals(List.of(), onFx(() -> tabNames(view)));
            assertNull(onFx(() -> table(view).getRoot()));
        }
    }

    // --- helpers -------------------------------------------------------------------------------------

    /** Waits until the tree and the node shown are read - in the background, holding the service's lock. */
    private static void read(ArtifactTreeView view) throws Exception {
        waitUntil("the tree and the node are read", () -> !table(view).isReading()
                && !((at.jku.isse.ecco.gui.view.detail.ArtifactDetailView) find(view, n -> n instanceof at.jku.isse.ecco.gui.view.detail.ArtifactDetailView)).isReading());
    }

    private static ArtifactTreeTableView table(ArtifactTreeView view) {
        return (ArtifactTreeTableView) find(view, n -> n instanceof ArtifactTreeTableView);
    }

    private static TabPane tabPane(ArtifactTreeView view) {
        return (TabPane) find(view, n -> n instanceof TabPane);
    }

    private static List<String> tabNames(ArtifactTreeView view) {
        return tabPane(view).getTabs().stream().map(Tab::getText).toList();
    }

    private static Tab tab(ArtifactTreeView view, String name) {
        return tabPane(view).getTabs().stream().filter(t -> name.equals(t.getText())).findFirst().orElseThrow();
    }

    private static String infoText(ArtifactTreeView view) {
        return ((Label) tab(view, "Info").getContent()).getText();
    }
}
