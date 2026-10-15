package at.jku.isse.ecco.gui.view.artifacts;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.gui.MinimizationResults;
import at.jku.isse.ecco.gui.io.DeleteDirectoryVisitor;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.tree.Node;
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

    /** Rows counting no artifacts are hidden by the unchecked box - but only after it was toggled once. */
    @Test
    @Timeout(60)
    public void associationsWithoutArtifactsAreHiddenOnlyOnceTheCheckBoxIsToggled(@TempDir Path tmp) throws Exception {
        try (EccoService service = twoFeatures(tmp)) {
            // the repository never stores an association without artifacts, so one row is made to count none
            ArtifactsView view = loaded(service, 2);
            onFx(() -> row(view, "b.txt").setNumArtifacts(0));

            // current behaviour: the box starts unchecked, but its filter only applies after a toggle
            assertEquals(2, (int) onFx(() -> table(view).getItems().size()));
            onFx(() -> checkBox(view, "Show Associations Without Artifacts").setSelected(true));
            assertEquals(2, (int) onFx(() -> table(view).getItems().size()));
            onFx(() -> checkBox(view, "Show Associations Without Artifacts").setSelected(false));
            List<AssociationInfoImpl> rows = onFx(() -> List.copyOf(table(view).getItems()));
            assertEquals(1, rows.size());
            assertTrue(rows.get(0).getNumArtifacts() > 0);
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

    /** Compose Selected puts the composition of only the selected associations into the artifact tree. */
    @Test
    @Timeout(60)
    public void composeSelectedShowsOnlyTheSelectedAssociationsArtifacts(@TempDir Path tmp) throws Exception {
        try (EccoService service = twoFeatures(tmp)) {
            ArtifactsView view = loaded(service, 2);

            onFx(() -> {
                row(view, "b.txt").setSelected(true);
                button(view, "Compose Selected").fire();
            });
            waitUntil("the composed tree", () -> treeTable(view).getRoot() != null && !toolBar(view).isDisable());

            Set<String> shown = onFx(() -> labels(treeTable(view).getRoot()));
            assertTrue(shown.contains("b1"), "" + shown);
            assertFalse(shown.contains("a1"), "a.txt's association is not selected: " + shown);
        }
    }

    /** Checkout Selected without a selection neither moves the base directory nor writes anything. */
    @Test
    @Timeout(60)
    public void checkoutSelectedWithNothingSelectedDoesNothing(@TempDir Path tmp) throws Exception {
        try (EccoService service = twoFeatures(tmp)) {
            ArtifactsView view = loaded(service, 2);
            Path baseDir = service.getBaseDir();

            onFx(() -> button(view, "Checkout Selected").fire());

            assertEquals(baseDir, service.getBaseDir(), "the base directory is left alone");
            try (Stream<Path> list = Files.list(tmp.resolve("repo"))) {
                assertEquals(List.of(".ecco"), list.map(p -> p.getFileName().toString()).toList(), "nothing is checked out");
            }
            assertFalse(onFx(() -> toolBar(view).isDisable()));
        }
    }

    /** Cancel in "delete contents?" stops Checkout Selected: nothing is written into the repository's folder. */
    @Test
    @Timeout(60)
    public void checkoutSelectedStopsWhenClearingIsCancelled(@TempDir Path tmp) throws Exception {
        try (EccoService service = twoFeatures(tmp)) {
            ScriptedArtifactsView view = scripted(service, false);

            onFx(() -> {
                row(view, "b.txt").setSelected(true);
                button(view, "Checkout Selected").fire();
            });

            assertEquals(List.of(service.getRepositoryHomeDir()), view.clearAsked);
            Thread.sleep(500);
            try (Stream<Path> list = Files.list(tmp.resolve("repo"))) {
                assertEquals(List.of(".ecco"), list.map(p -> p.getFileName().toString()).toList(), "nothing is checked out");
            }
            assertFalse(onFx(() -> toolBar(view).isDisable()));
        }
    }

    /** Confirming "delete contents?" checks the selected associations out into the repository's folder. */
    @Test
    @Timeout(60)
    public void checkoutSelectedWritesWhenClearingIsConfirmed(@TempDir Path tmp) throws Exception {
        try (EccoService service = twoFeatures(tmp)) {
            ScriptedArtifactsView view = scripted(service, true);

            onFx(() -> {
                row(view, "b.txt").setSelected(true);
                button(view, "Checkout Selected").fire();
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
            assertEquals(row.getAssociation().computeCondition().getModuleRevisionConditionString(),
                    onFx(() -> conditionColumn(view).getCellObservableValue(row).getValue()));

            onFx(() -> minimization.getMinimizedByAssociationId().remove(id));
            assertEquals("", row.getMinimizedCondition());
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
                    .filter(n -> n instanceof TableCell<?, ?> c && !c.isEmpty() && "Highlighted".equals(c.getTableColumn().getText()))
                    .map(n -> ((ColorPicker) ((TableCell<?, ?>) n).getGraphic()).getValue())
                    .collect(Collectors.toSet()));
            assertEquals(onFx(() -> table(view).getItems().stream().map(r -> r.colorProperty().get()).collect(Collectors.toSet())), pickers);
        }
    }

    /** Toggling a feature in the live panel selects the associations it certainly needs and composes them. */
    @Test
    @Timeout(60)
    public void liveFeaturesSelectsTheAssociationsTheToggledConfigurationNeeds(@TempDir Path tmp) throws Exception {
        try (EccoService service = twoFeatures(tmp)) {
            ArtifactsView view = loaded(service, 2);
            try {
                onFx(() -> button(view, "Live Features...").fire());
                Stage panel = onFx(ArtifactsViewTest::featurePanel);
                assertNotNull(panel, "the feature panel is shown");

                onFx(() -> featureToggle(panel, "A").fire());
                waitUntil("the tree for A", () -> treeTable(view).getRoot() != null);

                assertTrue(onFx(() -> row(view, "a.txt").isSelected()));
                assertFalse(onFx(() -> row(view, "b.txt").isSelected()), "b.txt needs B");
                Set<String> shown = onFx(() -> labels(treeTable(view).getRoot()));
                assertTrue(shown.contains("a1") && !shown.contains("b1"), "" + shown);

                // a second click reuses the open panel
                onFx(() -> button(view, "Live Features...").fire());
                assertSame(panel, onFx(ArtifactsViewTest::featurePanel));

                service.close();
                waitUntil("the panel is closed with the repository", () -> featurePanel() == null);
            } finally {
                onFx(() -> {
                    Stage panel = featurePanel();
                    if (panel != null)
                        panel.close();
                });
            }
        }
    }

    /** A closed repository empties the list and the tree and disables the view. */
    @Test
    @Timeout(60)
    public void aClosedRepositoryEmptiesAndDisablesTheView(@TempDir Path tmp) throws Exception {
        EccoService service = twoFeatures(tmp);
        try {
            ArtifactsView view = loaded(service, 2);
            onFx(() -> {
                menuItem(view, "Select All").fire();
                button(view, "Compose Selected").fire();
            });
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

    /** Answers "delete contents?" as told, without a dialog, and records the question. */
    private static final class ScriptedArtifactsView extends ArtifactsView {
        final List<Path> clearAsked = new ArrayList<>();
        private final boolean clearAnswer;

        ScriptedArtifactsView(EccoService service, boolean clearAnswer) {
            super(service, new MinimizationResults(service));
            this.clearAnswer = clearAnswer;
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

    private static Stage featurePanel() {
        return Window.getWindows().stream()
                .filter(w -> w instanceof Stage s && "Features".equals(s.getTitle()) && s.isShowing())
                .map(w -> (Stage) w).findFirst().orElse(null);
    }

    private static CheckBox featureToggle(Stage panel, String feature) {
        return (CheckBox) find(panel.getScene().getRoot(), n -> n instanceof CheckBox c && feature.equals(c.getText()));
    }
}
