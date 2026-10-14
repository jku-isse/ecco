package at.jku.isse.ecco.gui.view.detail;

import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.tree.Node;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static at.jku.isse.ecco.gui.FxTestSupport.*;
import static at.jku.isse.ecco.gui.view.detail.ArtifactDetailViewTest.association;
import static at.jku.isse.ecco.gui.view.detail.ArtifactDetailViewTest.twoFiles;
import static org.junit.jupiter.api.Assertions.*;

/**
 * What the snippet tree shows: one legend toggle per artifact kind, every artifact as its own text,
 * and - with a kind toggled off - only the snippets left, keeping the ancestors of visible ones.
 */
public class ArtifactSnippetTreeViewTest {

    @BeforeAll
    public static void start() throws InterruptedException {
        startToolkit();
    }

    /** The legend lists the kinds in the order they first occur; the tree holds every node, the root hidden. */
    @Test
    @Timeout(60)
    public void showsEveryArtifactWithOneLegendTogglePerKind(@TempDir Path tmp) throws Exception {
        try (EccoService service = twoFiles(tmp)) {
            Node root = association(service, "a.txt").getRootNode();
            ArtifactSnippetTreeView view = onFx(() -> {
                ArtifactSnippetTreeView v = new ArtifactSnippetTreeView();
                v.setRootNode((at.jku.isse.ecco.tree.RootNode) root);
                return v;
            });

            assertEquals(List.of("Directory", "Plugin", "Line", "FinalNewline"), onFx(() -> legend(view)));
            assertFalse(onFx(() -> tree(view).isShowRoot()));
            assertEquals(List.of("", "a.txt [at.jku.isse.ecco.adapter.text.TextPlugin]", "a1", "a2", "(final newline)"), onFx(() -> shown(view)));

            onFx(() -> view.setRootNode(null));
            assertNull(onFx(() -> tree(view).getRoot()));
            assertEquals(List.of(), onFx(() -> legend(view)));
        }
    }

    /** Toggling a kind off hides its snippets; a hidden node with visible descendants stays for them. */
    @Test
    @Timeout(60)
    public void legendTogglesFilterByKindButKeepAncestors(@TempDir Path tmp) throws Exception {
        try (EccoService service = twoFiles(tmp)) {
            Node root = association(service, "a.txt").getRootNode();
            ArtifactSnippetTreeView view = onFx(() -> {
                ArtifactSnippetTreeView v = new ArtifactSnippetTreeView();
                v.setRootNode((at.jku.isse.ecco.tree.RootNode) root);
                return v;
            });

            onFx(() -> toggle(view, "Line").setSelected(false));
            assertEquals(List.of("", "a.txt [at.jku.isse.ecco.adapter.text.TextPlugin]", "(final newline)"), onFx(() -> shown(view)));

            // the file's kind off, but its final newline still on: the file stays as its ancestor
            onFx(() -> toggle(view, "Plugin").setSelected(false));
            assertEquals(List.of("", "a.txt [at.jku.isse.ecco.adapter.text.TextPlugin]", "(final newline)"), onFx(() -> shown(view)));

            onFx(() -> toggle(view, "FinalNewline").setSelected(false));
            assertEquals(List.of(""), onFx(() -> shown(view)), "only the directory is left");

            onFx(() -> toggle(view, "Line").setSelected(true));
            assertEquals(List.of("", "a.txt [at.jku.isse.ecco.adapter.text.TextPlugin]", "a1", "a2"), onFx(() -> shown(view)));
        }
    }

    /** A rendered cell shows the artifact's text; a node with empty text gets no label. */
    @Test
    @Timeout(60)
    public void renderedCellsShowTheSnippetText(@TempDir Path tmp) throws Exception {
        try (EccoService service = twoFiles(tmp)) {
            Node root = association(service, "a.txt").getRootNode();
            ArtifactSnippetTreeView view = onFx(() -> {
                ArtifactSnippetTreeView v = new ArtifactSnippetTreeView();
                v.setRootNode((at.jku.isse.ecco.tree.RootNode) root);
                new Scene(v, 600, 600);
                v.applyCss();
                v.layout();
                return v;
            });

            Set<String> texts = onFx(() -> tree(view).lookupAll(".tree-cell").stream()
                    .map(n -> (TreeCell<?>) n).filter(c -> !c.isEmpty())
                    .map(c -> String.valueOf(c.getText())).collect(Collectors.toSet()));
            assertEquals(Set.of("null", "a.txt [at.jku.isse.ecco.adapter.text.TextPlugin]", "a1", "a2", "(final newline)"), texts,
                    "the directory's empty text is no label (null)");
            assertTrue(onFx(() -> tree(view).lookupAll(".tree-cell").stream()
                    .map(n -> (TreeCell<?>) n).filter(c -> "a1".equals(c.getText()))
                    .allMatch(c -> c.getStyle().contains("-fx-background-color"))), "a snippet is tinted by its kind");
        }
    }

    // --- helpers -------------------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static TreeView<Node> tree(ArtifactSnippetTreeView view) {
        return (TreeView<Node>) view.getCenter();
    }

    private static List<String> legend(ArtifactSnippetTreeView view) {
        return findAll(view.getTop(), n -> n instanceof CheckBox).stream().map(n -> ((CheckBox) n).getText()).toList();
    }

    private static CheckBox toggle(ArtifactSnippetTreeView view, String kind) {
        return (CheckBox) find(view.getTop(), n -> n instanceof CheckBox c && kind.equals(c.getText()));
    }

    /** The nodes in the tree below the (hidden) root, depth first. */
    private static List<String> shown(ArtifactSnippetTreeView view) {
        List<String> shown = new ArrayList<>();
        collect(tree(view).getRoot(), shown);
        shown.remove(0);
        return shown;
    }

    private static void collect(TreeItem<Node> item, List<String> shown) {
        shown.add(String.valueOf(item.getValue()));
        for (TreeItem<Node> child : item.getChildren())
            collect(child, shown);
    }
}
