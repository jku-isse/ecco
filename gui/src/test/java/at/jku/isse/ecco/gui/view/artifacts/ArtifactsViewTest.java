package at.jku.isse.ecco.gui.view.artifacts;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.gui.MinimizationResults;
import at.jku.isse.ecco.gui.io.DeleteDirectoryVisitor;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.tree.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static at.jku.isse.ecco.gui.FxTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * What the Artifacts tab decides: which associations it lists (and which it hides), what selecting
 * them does (colors, the composed artifact tree, the live feature toggles), the condition labels it
 * shows, and that a closed repository empties it.
 */
public class ArtifactsViewTest {

    @BeforeAll
    public static void start() throws InterruptedException {
        startToolkit();
    }

    /** Refresh lists every association with its artifact count, none selected or highlighted, no tree yet. */
    @Test
    @Timeout(60)
    public void listsEveryAssociationWithArtifactsSortedById(@TempDir Path tmp) throws Exception {
        try (EccoService service = twoFeatures(tmp)) {
            ArtifactsView view = loaded(service, 2);

            List<AssociationInfoImpl> rows = onFx(() -> List.copyOf(table(view).getItems()));
            Set<String> expected = service.getRepository().getAssociations().stream().map(Association::getId).collect(Collectors.toSet());
            assertEquals(expected, rows.stream().map(r -> r.getAssociation().getId()).collect(Collectors.toSet()));
            for (AssociationInfoImpl row : rows) {
                assertEquals(row.getAssociation().getRootNode().countArtifacts(), row.getNumArtifacts());
                assertFalse(row.isSelected(), "nothing is selected to begin with");
                assertEquals(Color.TRANSPARENT, row.colorProperty().get(), "no highlight before a selection");
            }
            assertNull(onFx(() -> treeTable(view).getRoot()), "no artifact tree before a compose");
            assertFalse(onFx(() -> view.isDisable()));
        }
    }

    /** Select All / Unselect All mark every row; a selected row gets its own color and keeps it. */
    @Test
    @Timeout(60)
    public void selectAllAndUnselectAllMarkEveryAssociationAndSelectingAssignsAFixedColor(@TempDir Path tmp) throws Exception {
        try (EccoService service = twoFeatures(tmp)) {
            ArtifactsView view = loaded(service, 2);

            onFx(() -> menuItem(view, "Select All").fire());
            List<AssociationInfoImpl> rows = onFx(() -> List.copyOf(table(view).getItems()));
            assertTrue(rows.stream().allMatch(AssociationInfoImpl::isSelected));
            Map<String, Color> colors = rows.stream().collect(Collectors.toMap(r -> r.getAssociation().getId(), r -> r.colorProperty().get()));
            assertFalse(colors.containsValue(Color.TRANSPARENT), "a selected association is highlighted");
            assertEquals(2, new HashSet<>(colors.values()).size(), "each association its own color");
            assertFalse(onFx(() -> toolBar(view).isDisable()));

            onFx(() -> menuItem(view, "Unselect All").fire());
            assertTrue(rows.stream().noneMatch(AssociationInfoImpl::isSelected));
            onFx(() -> menuItem(view, "Select All").fire());
            assertEquals(colors, rows.stream().collect(Collectors.toMap(r -> r.getAssociation().getId(), r -> r.colorProperty().get())),
                    "an association keeps its color across selections");
        }
    }

    /** The tree follows the ticks: it shows the composition of the ticked associations only, nothing once none is ticked. */
    @Test
    @Timeout(60)
    public void theTreeShowsTheTickedAssociationsArtifacts(@TempDir Path tmp) throws Exception {
        try (EccoService service = twoFeatures(tmp)) {
            ArtifactsView view = loaded(service, 2);

            onFx(() -> row(view, "b.txt").setSelected(true));
            waitUntil("the composed tree", () -> treeTable(view).getRoot() != null);
            Set<String> shown = onFx(() -> labels(treeTable(view).getRoot()));
            assertTrue(shown.contains("b1"), "" + shown);
            assertFalse(shown.contains("a1"), "a.txt's association is not ticked: " + shown);

            onFx(() -> row(view, "a.txt").setSelected(true));
            waitUntil("a.txt too", () -> treeTable(view).getRoot() != null && labels(treeTable(view).getRoot()).contains("a1"));

            onFx(() -> menuItem(view, "Unselect All").fire());
            waitUntil("an empty tree", () -> treeTable(view).getRoot() == null);
        }
    }

    /** Checkout Selected without a selection asks for no folder, moves no base directory and writes nothing. */
    @Test
    @Timeout(60)
    public void checkoutSelectedWithNothingSelectedDoesNothing(@TempDir Path tmp) throws Exception {
        try (EccoService service = twoFeatures(tmp)) {
            ScriptedArtifactsView view = scripted(service, false);
            Path baseDir = service.getBaseDir();

            onFx(() -> button(view, "Checkout Selected...").fire());

            assertEquals(0, view.foldersAsked);
            assertEquals(baseDir, service.getBaseDir(), "the base directory is left alone");
            try (Stream<Path> list = Files.list(tmp.resolve("repo"))) {
                assertEquals(List.of(".ecco"), list.map(p -> p.getFileName().toString()).toList(), "nothing is checked out");
            }
            assertFalse(onFx(() -> toolBar(view).isDisable()));
        }
    }

    /** Checkout Selected checks the ticked associations out into the folder chosen - it always used the repository's own. */
    @Test
    @Timeout(60)
    public void checkoutSelectedWritesIntoTheChosenFolder(@TempDir Path tmp) throws Exception {
        try (EccoService service = twoFeatures(tmp)) {
            ScriptedArtifactsView view = scripted(service, false);
            view.chosen = Files.createDirectories(tmp.resolve("chosen"));

            onFx(() -> {
                row(view, "b.txt").setSelected(true);
                button(view, "Checkout Selected...").fire();
            });

            waitUntil("b.txt is checked out", () -> Files.exists(tmp.resolve("chosen/b.txt")));
            assertEquals(1, view.foldersAsked);
            assertEquals(List.of(), view.clearAsked, "an empty folder is not asked about");
            try (Stream<Path> list = Files.list(tmp.resolve("repo"))) {
                assertEquals(List.of(".ecco"), list.map(p -> p.getFileName().toString()).toList(), "nothing in the repository's folder");
            }
        }
    }

    /** No folder chosen: nothing is checked out, and nothing is asked. */
    @Test
    @Timeout(60)
    public void checkoutSelectedStopsWhenNoFolderIsChosen(@TempDir Path tmp) throws Exception {
        try (EccoService service = twoFeatures(tmp)) {
            ScriptedArtifactsView view = scripted(service, true);
            view.chosen = null;
            Path baseDir = service.getBaseDir();

            onFx(() -> {
                row(view, "b.txt").setSelected(true);
                button(view, "Checkout Selected...").fire();
            });

            assertEquals(1, view.foldersAsked);
            assertEquals(List.of(), view.clearAsked);
            assertEquals(baseDir, service.getBaseDir());
            assertFalse(onFx(() -> toolBar(view).isDisable()));
        }
    }

    /** Cancel in "delete contents?" of a folder with files stops Checkout Selected: nothing is written. */
    @Test
    @Timeout(60)
    public void checkoutSelectedStopsWhenClearingIsCancelled(@TempDir Path tmp) throws Exception {
        try (EccoService service = twoFeatures(tmp)) {
            ScriptedArtifactsView view = scripted(service, false);

            onFx(() -> {
                row(view, "b.txt").setSelected(true);
                button(view, "Checkout Selected...").fire();
            });

            assertEquals(List.of(service.getRepositoryHomeDir()), view.clearAsked);
            Thread.sleep(500);
            try (Stream<Path> list = Files.list(tmp.resolve("repo"))) {
                assertEquals(List.of(".ecco"), list.map(p -> p.getFileName().toString()).toList(), "nothing is checked out");
            }
            assertFalse(onFx(() -> toolBar(view).isDisable()));
        }
    }

    /** Confirming "delete contents?" for the repository's own folder checks out there, and keeps the repository. */
    @Test
    @Timeout(60)
    public void checkoutSelectedWritesWhenClearingIsConfirmed(@TempDir Path tmp) throws Exception {
        try (EccoService service = twoFeatures(tmp)) {
            ScriptedArtifactsView view = scripted(service, true);

            onFx(() -> {
                row(view, "b.txt").setSelected(true);
                button(view, "Checkout Selected...").fire();
            });

            waitUntil("b.txt is checked out", () -> Files.exists(tmp.resolve("repo/b.txt")));
            assertEquals(List.of(service.getRepositoryHomeDir()), view.clearAsked);
            assertTrue(Files.isDirectory(tmp.resolve("repo/.ecco")), "the repository is kept");
        }
    }

    /** The condition column shows the live minimized condition with simplified labels, the raw condition without. */
    @Test
    @Timeout(60)
    public void theConditionColumnShowsTheMinimizedConditionOnlyWithSimplifiedLabels(@TempDir Path tmp) throws Exception {
        try (EccoService service = twoFeatures(tmp)) {
            MinimizationResults minimization = new MinimizationResults(service);
            ArtifactsView view = loaded(service, 2, minimization);
            AssociationInfoImpl row = onFx(() -> row(view, "a.txt"));
            String id = row.getAssociation().getId();

            // a minimization run (or its undo) reaches the displayed row without a refresh
            onFx(() -> minimization.getMinimizedByAssociationId().put(id, "A"));
            assertEquals("A", row.getMinimizedCondition());
            assertEquals("A", onFx(() -> conditionColumn(view).getCellObservableValue(row).getValue()), "simplified labels are on by default");

            onFx(() -> checkBox(view, "Use Simplified Labels").setSelected(false));
            assertEquals(row.getAssociation().computeCondition().toDisplayString(),
                    onFx(() -> conditionColumn(view).getCellObservableValue(row).getValue()));

            onFx(() -> minimization.getMinimizedByAssociationId().remove(id));
            assertEquals("", row.getMinimizedCondition());
        }
    }

    /** Before the conditions are minimized, simplified labels show the full condition: the column was empty. */
    @Test
    @Timeout(60)
    public void aConditionNotMinimizedYetIsShownInFull(@TempDir Path tmp) throws Exception {
        try (EccoService service = twoFeatures(tmp)) {
            ArtifactsView view = loaded(service, 2, new MinimizationResults(service));
            AssociationInfoImpl row = onFx(() -> row(view, "a.txt"));

            assertTrue(onFx(() -> checkBox(view, "Use Simplified Labels").isSelected()));
            String shown = onFx(() -> conditionColumn(view).getCellObservableValue(row).getValue());
            assertEquals(row.getAssociation().computeCondition().toDisplayString(), shown);
            assertFalse(shown.isEmpty());
        }
    }

    /** Rendered rows show each association's artifact count and its highlight color in the picker. */
    @Test
    @Timeout(60)
    public void renderedRowsShowTheArtifactCountsAndTheHighlightColor(@TempDir Path tmp) throws Exception {
        try (EccoService service = twoFeatures(tmp)) {
            ArtifactsView view = loaded(service, 2);
            onFx(() -> menuItem(view, "Select All").fire());

            onFx(() -> {
                new Scene(view, 1000, 700);
                view.applyCss();
                view.layout();
            });

            Set<String> counts = onFx(() -> table(view).lookupAll(".table-cell").stream()
                    .filter(n -> n instanceof TableCell<?, ?> c && !c.isEmpty() && "Artifacts".equals(c.getTableColumn().getText()))
                    .map(n -> ((Label) ((javafx.scene.layout.HBox) ((TableCell<?, ?>) n).getGraphic()).getChildren().get(1)).getText())
                    .collect(Collectors.toSet()));
            assertEquals(onFx(() -> table(view).getItems().stream().map(r -> String.valueOf(r.getNumArtifacts())).collect(Collectors.toSet())), counts);

            Set<Color> pickers = onFx(() -> table(view).lookupAll(".table-cell").stream()
                    .filter(n -> n instanceof TableCell<?, ?> c && !c.isEmpty() && "Colour".equals(c.getTableColumn().getText()))
                    .map(n -> ((ColorPicker) ((TableCell<?, ?>) n).getGraphic()).getValue())
                    .collect(Collectors.toSet()));
            assertEquals(onFx(() -> table(view).getItems().stream().map(r -> r.colorProperty().get()).collect(Collectors.toSet())), pickers);
        }
    }

    /**
     * The Features pane, docked at the left: toggling a feature ticks the associations a checkout of
     * the configuration takes - as Select by Configuration does - and the tree follows. It was a
     * window of its own, and took the associations whose certain condition holds.
     */
    @Test
    @Timeout(60)
    public void theFeaturesPaneTicksTheAssociationsACheckoutTakes(@TempDir Path tmp) throws Exception {
        try (EccoService service = twoFeatures(tmp)) {
            ArtifactsView view = loaded(service, 2);
            onFx(() -> featuresButton(view).fire());
            Parent pane = onFx(() -> (Parent) view.getLeft());
            assertNotNull(pane, "the pane is docked");

            onFx(() -> featureToggle(pane, "A").fire());
            waitUntil("a.txt is ticked", () -> row(view, "a.txt").isSelected());
            waitUntil("the tree for A", () -> treeTable(view).getRoot() != null);
            assertFalse(onFx(() -> row(view, "b.txt").isSelected()), "b.txt needs B");
            Set<String> shown = onFx(() -> labels(treeTable(view).getRoot()));
            assertTrue(shown.contains("a1") && !shown.contains("b1"), "" + shown);
            Set<Association> taken = service.getAssociations("A.1");
            assertEquals(taken, onFx(() -> table(view).getItems().stream().filter(AssociationInfoImpl::isSelected)
                    .map(AssociationInfoImpl::getAssociation).collect(Collectors.toSet())), "what a checkout of A takes");

            // the button hides it again, and shows the same pane
            onFx(() -> featuresButton(view).fire());
            assertNull(onFx(() -> view.getLeft()));
            onFx(() -> featuresButton(view).fire());
            assertSame(pane, onFx(() -> view.getLeft()));

            service.close();
            waitUntil("the pane goes with the repository", () -> view.getLeft() == null && !featuresButton(view).isSelected());
        }
    }

    /** A closed repository empties the list and the tree and disables the view. */
    @Test
    @Timeout(60)
    public void aClosedRepositoryEmptiesAndDisablesTheView(@TempDir Path tmp) throws Exception {
        EccoService service = twoFeatures(tmp);
        try {
            ArtifactsView view = loaded(service, 2);
            onFx(() -> menuItem(view, "Select All").fire());
            waitUntil("the composed tree", () -> treeTable(view).getRoot() != null);

            service.close();
            waitUntil("the view is emptied", () -> view.isDisable() && table(view).getItems().isEmpty() && treeTable(view).getRoot() == null);
        } finally {
            service.close();
        }
    }

    /** Built on a service with no repository open, the view starts disabled. */
    @Test
    @Timeout(60)
    public void aViewOnAClosedServiceStartsDisabled(@TempDir Path tmp) throws Exception {
        try (EccoService service = new EccoService()) {
            ArtifactsView view = onFx(() -> new ArtifactsView(service, new MinimizationResults(service)));
            assertTrue(onFx(() -> view.isDisable()));
        }
    }

    /** A view opened before the first commit refreshes itself when a commit changes the repository. */
    @Test
    @Timeout(60)
    public void aCommitRefreshesTheView(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            ArtifactsView view = onFx(() -> new ArtifactsView(service, new MinimizationResults(service)));
            assertEquals(0, (int) onFx(() -> table(view).getItems().size()));

            commit(service, tmp.resolve("v1"), "A.1", Map.of("a.txt", "a1\na2\n"));
            waitUntil("the committed association", () -> table(view).getItems().size() == 1 && !toolBar(view).isDisable());
            assertFalse(onFx(view::isDisable));
        }
    }

    /** A refresh that fails reports the error (non-modal) and leaves the toolbar usable and the rows as they were. */
    @Test
    @Timeout(60)
    public void aFailingRefreshLeavesTheToolbarUsable(@TempDir Path tmp) throws Exception {
        java.util.concurrent.atomic.AtomicBoolean fail = new java.util.concurrent.atomic.AtomicBoolean();
        Files.createDirectories(tmp.resolve("repo"));
        try (EccoService service = new EccoService() {
            @Override
            public synchronized Map<String, String> validMinimizedConditions() {
                if (fail.get())
                    throw new IllegalStateException("refresh failed");
                return super.validMinimizedConditions();
            }
        }) {
            service.setRepositoryDir(tmp.resolve("repo").resolve(".ecco"));
            service.init();
            commit(service, tmp.resolve("v1"), "A.1", Map.of("a.txt", "a1\na2\n"));
            ArtifactsView view = loaded(service, 1);
            int windows = onFx(() -> Window.getWindows().size());

            fail.set(true);
            onFx(() -> button(view, "Refresh").fire());
            waitUntil("the error is shown", () -> Window.getWindows().size() == windows + 1);

            try {
                assertFalse(onFx(() -> toolBar(view).isDisable()), "the toolbar is enabled again");
                assertEquals(1, (int) onFx(() -> table(view).getItems().size()), "the previous rows stay");
            } finally {
                onFx(() -> {
                    for (Window window : List.copyOf(Window.getWindows()))
                        if (window.getScene() != null && window.getScene().getRoot() instanceof DialogPane)
                            window.hide();
                });
            }
        }
    }

    // --- helpers -------------------------------------------------------------------------------------

    /** A repository with a.txt for A and b.txt added for B. */
    private static EccoService twoFeatures(Path tmp) throws Exception {
        EccoService service = repository(tmp.resolve("repo"));
        commit(service, tmp.resolve("v1"), "A.1", Map.of("a.txt", "a1\na2\n"));
        commit(service, tmp.resolve("v2"), "A.1, B.1", Map.of("a.txt", "a1\na2\n", "b.txt", "b1\n"));
        return service;
    }

    /** Answers "delete contents?" and "which folder?" as told, without dialogs, and records the questions. */
    private static final class ScriptedArtifactsView extends ArtifactsView {
        final List<Path> clearAsked = new ArrayList<>();
        private final boolean clearAnswer;
        // the folder "chosen" for Checkout Selected - the repository's own, unless a test says otherwise
        Path chosen;
        int foldersAsked;

        ScriptedArtifactsView(EccoService service, boolean clearAnswer) {
            super(service, new MinimizationResults(service));
            this.clearAnswer = clearAnswer;
            this.chosen = service.getRepositoryHomeDir();
        }

        @Override
        Path chooseCheckoutDirectory() {
            this.foldersAsked++;
            return this.chosen;
        }

        @Override
        boolean confirmClearDirectory(Path baseDir) throws IOException {
            this.clearAsked.add(baseDir);
            if (this.clearAnswer)
                Files.walkFileTree(baseDir, new DeleteDirectoryVisitor(baseDir));
            return this.clearAnswer;
        }
    }

    private static ScriptedArtifactsView scripted(EccoService service, boolean clearAnswer) throws Exception {
        ScriptedArtifactsView view = onFx(() -> new ScriptedArtifactsView(service, clearAnswer));
        onFx(() -> button(view, "Refresh").fire());
        waitUntil("2 associations", () -> table(view).getItems().size() == 2 && !toolBar(view).isDisable());
        return view;
    }

    private static ArtifactsView loaded(EccoService service, int rows) throws Exception {
        return loaded(service, rows, new MinimizationResults(service));
    }

    private static ArtifactsView loaded(EccoService service, int rows, MinimizationResults minimization) throws Exception {
        ArtifactsView view = onFx(() -> new ArtifactsView(service, minimization));
        onFx(() -> button(view, "Refresh").fire());
        waitUntil(rows + " associations", () -> table(view).getItems().size() == rows && !toolBar(view).isDisable());
        return view;
    }

    @SuppressWarnings("unchecked")
    private static TableView<AssociationInfoImpl> table(ArtifactsView view) {
        return (TableView<AssociationInfoImpl>) find(view, n -> n instanceof TableView);
    }

    private static ArtifactTreeTableView treeTable(ArtifactsView view) {
        return (ArtifactTreeTableView) find(view, n -> n instanceof ArtifactTreeTableView);
    }

    private static ToolBar toolBar(ArtifactsView view) {
        return (ToolBar) view.getTop();
    }

    private static CheckBox checkBox(ArtifactsView view, String text) {
        return (CheckBox) find(view, n -> n instanceof CheckBox c && text.equals(c.getText()));
    }

    private static MenuItem menuItem(ArtifactsView view, String text) {
        MenuButton menu = (MenuButton) find(view, n -> n instanceof MenuButton);
        return menu.getItems().stream().filter(i -> text.equals(i.getText())).findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static TableColumn<AssociationInfoImpl, String> conditionColumn(ArtifactsView view) {
        return (TableColumn<AssociationInfoImpl, String>) table(view).getColumns().get(0).getColumns().stream()
                .filter(c -> "Condition".equals(c.getText())).findFirst().orElseThrow();
    }

    /** The row of the association whose artifacts include the file {@code name}. */
    private static AssociationInfoImpl row(ArtifactsView view, String name) {
        return table(view).getItems().stream()
                .filter(r -> labels(r.getAssociation().getRootNode()).stream().anyMatch(l -> l.startsWith(name + " ")))
                .findFirst().orElseThrow();
    }

    private static Set<String> labels(Node node) {
        Set<String> labels = new HashSet<>();
        node.traverse(n -> labels.add(String.valueOf(n)));
        return labels;
    }

    private static Set<String> labels(TreeItem<ArtifactTreeTableView.NodeWrapper> item) {
        Set<String> labels = new HashSet<>();
        labels.add(String.valueOf(item.getValue()));
        for (TreeItem<ArtifactTreeTableView.NodeWrapper> child : item.getChildren())
            labels.addAll(labels(child));
        return labels;
    }

    private static ToggleButton featuresButton(ArtifactsView view) {
        return (ToggleButton) find(view, n -> n instanceof ToggleButton b && "Features".equals(b.getText()));
    }

    private static CheckBox featureToggle(Parent pane, String feature) {
        return (CheckBox) find(pane, n -> n instanceof CheckBox c && feature.equals(c.getText()));
    }
}
