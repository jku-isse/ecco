package at.jku.isse.ecco.gui.view;

import at.jku.isse.ecco.core.Constraint;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.gui.MinimizationResults;
import at.jku.isse.ecco.mining.ConstraintMiner;
import at.jku.isse.ecco.service.EccoService;
import javafx.geometry.Orientation;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import org.graphstream.graph.Edge;
import org.graphstream.graph.Node;
import org.graphstream.ui.fx_viewer.FxViewPanel;
import org.graphstream.ui.graphicGraph.GraphicGraph;
import org.graphstream.ui.graphicGraph.GraphicNode;
import org.graphstream.ui.graphicGraph.stylesheet.StyleConstants;
import org.graphstream.ui.view.camera.Camera;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static at.jku.isse.ecco.gui.FxTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * What the Feature Model tab draws: one node per feature, the {@link FeatureModelTree} parent edges,
 * accepted REQUIRES/EXCLUDES as edges of their own kind and accepted MANDATORY as a decoration; in
 * the tree layout the positions {@link FeatureModelTree} computes; and when it draws again (status
 * events, a layout switch, the tab becoming visible after being hidden). Read from the graph the
 * viewer renders. Export opens a file chooser, so it is not driven here.
 *
 * <p>The sample commits Base, then Base with A, then Base, A, B, then Base with C: the tree is
 * Base -> A -> B and Base -> C (each feature under the latest earlier feature it was committed with).
 */
public class FeaturesViewTest {

    private static final double X_SPACING = 130, Y_SPACING = 110, NODE_WIDTH = 90, NODE_HEIGHT = 32;

    @BeforeAll
    public static void start() throws InterruptedException {
        startToolkit();
    }

    @Test
    @Timeout(60)
    public void drawsEveryFeatureWithItsParentEdges(@TempDir Path tmp) throws Exception {
        try (EccoService service = sample(tmp)) {
            FeaturesView view = loaded(service);

            assertEquals(Set.of("Base", "A", "B", "C"), onFx(() -> labels(view)));
            Set<String> expected = FeatureModelTree.compute(service.getRepository()).stream()
                    .filter(p -> p.parent != null)
                    .map(p -> p.parent.getName() + "->" + p.feature.getName()).collect(Collectors.toSet());
            assertEquals(Set.of("Base->A", "A->B", "Base->C"), expected, "the fixture's tree");
            assertEquals(expected, onFx(() -> edges(view, "tree")));
            assertFalse(onFx(() -> toolBar(view).isDisable()));
        }
    }

    @Test
    @Timeout(60)
    public void acceptedConstraintsAreDrawnAgainstTheTree(@TempDir Path tmp) throws Exception {
        try (EccoService service = sample(tmp)) {
            service.acceptConstraint(Constraint.Kind.REQUIRES, "B", "A");
            service.acceptConstraint(Constraint.Kind.EXCLUDES, "A", "C");
            service.acceptConstraint(Constraint.Kind.MANDATORY, "Base", null);
            // about a feature the repository does not know: nothing to draw it against
            service.acceptConstraint(Constraint.Kind.REQUIRES, "B", "Unknown");
            service.acceptConstraint(Constraint.Kind.MANDATORY, "Unknown", null);
            FeaturesView view = loaded(service);
            onFx(() -> layoutChoice(view).getSelectionModel().select(1));
            waitUntil("the force-directed graph", () -> !toolBar(view).isDisable() && graphic(view).getNodeCount() == 4);

            assertEquals(Set.of("B->A"), onFx(() -> edges(view, "requires")));
            assertEquals(Set.of("A->C"), onFx(() -> edges(view, "excludes")));
            assertEquals(4, onFx(() -> graphic(view).getNodeCount()), "no node for Unknown, no marker node in this layout");
            // force-directed: a mandatory feature gets a bold outline
            assertTrue(onFx(() -> style(view, "Base")).contains("stroke-width: 3px"));
            for (String feature : List.of("A", "B", "C"))
                assertFalse(onFx(() -> style(view, feature)).contains("stroke-width"), feature);
        }
    }

    @Test
    @Timeout(60)
    public void aConstraintAcceptedLaterIsDrawnThroughTheStatusEvent(@TempDir Path tmp) throws Exception {
        try (EccoService service = sample(tmp)) {
            FeaturesView view = loaded(service);
            assertEquals(Set.of(), onFx(() -> edges(view, "requires")));

            service.acceptConstraint(Constraint.Kind.REQUIRES, "C", "Base");
            waitUntil("the REQUIRES edge", () -> edges(view, "requires").equals(Set.of("C->Base")));
            assertEquals(Set.of("Base->A", "A->B", "Base->C"), onFx(() -> edges(view, "tree")), "the tree is drawn again with it");
        }
    }

    @Test
    @Timeout(60)
    public void theTreeLayoutPlacesEachFeatureWhereTheModelTreeSays(@TempDir Path tmp) throws Exception {
        try (EccoService service = sample(tmp)) {
            service.acceptConstraint(Constraint.Kind.MANDATORY, "Base", null);
            FeaturesView view = loaded(service);

            // the default layout
            assertEquals("Tree layout", onFx(() -> String.valueOf(layoutChoice(view).getValue())));
            waitUntil("the mandatory marker", () -> graphic(view).getNode("mandatory:" + id(service, "Base")) != null
                    && !toolBar(view).isDisable());

            Map<String, double[]> expected = new HashMap<>();
            for (FeatureModelTree.Placement placement : FeatureModelTree.compute(service.getRepository()))
                expected.put(placement.feature.getName(), new double[]{placement.x * X_SPACING, -placement.depth * Y_SPACING});
            waitUntil("the tree positions", () -> expected.entrySet().stream().allMatch(e -> {
                GraphicNode node = node(view, e.getKey());
                return node.getX() == e.getValue()[0] && node.getY() == e.getValue()[1];
            }));

            // a filled dot at the top edge of the mandatory feature, instead of a bold outline
            GraphicNode base = onFx(() -> node(view, "Base"));
            GraphicNode marker = onFx(() -> (GraphicNode) graphic(view).getNode("mandatory:" + base.getId()));
            assertEquals(base.getX(), marker.getX());
            assertEquals(base.getY() + NODE_HEIGHT / 2, marker.getY());
            assertFalse(onFx(() -> style(view, "Base")).contains("stroke-width"));

            // the scroll bars span the drawn boxes
            double minX = expected.values().stream().mapToDouble(p -> p[0]).min().orElseThrow() - NODE_WIDTH / 2;
            double maxX = expected.values().stream().mapToDouble(p -> p[0]).max().orElseThrow() + NODE_WIDTH / 2;
            double minY = expected.values().stream().mapToDouble(p -> p[1]).min().orElseThrow() - NODE_HEIGHT / 2;
            double maxY = expected.values().stream().mapToDouble(p -> p[1]).max().orElseThrow() + NODE_HEIGHT / 2;
            waitUntil("the scroll bar ranges", () -> scrollBar(view, Orientation.HORIZONTAL).getMin() == minX
                    && scrollBar(view, Orientation.HORIZONTAL).getMax() == maxX
                    && scrollBar(view, Orientation.VERTICAL).getMin() == minY
                    && scrollBar(view, Orientation.VERTICAL).getMax() == maxY);

            // and to force-directed: the marker goes, the outline comes
            onFx(() -> layoutChoice(view).getSelectionModel().select(1));
            waitUntil("the force-directed graph", () -> graphic(view).getNode("mandatory:" + base.getId()) == null
                    && style(view, "Base").contains("stroke-width: 3px"));
        }
    }

    @Test
    @Timeout(60)
    public void theScrollBarsMoveTheCameraWithTheVerticalOneInverted(@TempDir Path tmp) throws Exception {
        try (EccoService service = sample(tmp)) {
            FeaturesView view = loaded(service);
            waitUntil("the tree scroll ranges", () -> !toolBar(view).isDisable()
                    && scrollBar(view, Orientation.VERTICAL).getMin() < scrollBar(view, Orientation.VERTICAL).getMax()
                    && scrollBar(view, Orientation.HORIZONTAL).getMin() < scrollBar(view, Orientation.HORIZONTAL).getMax());

            double[] expected = onFx(() -> {
                ScrollBar horizontal = scrollBar(view, Orientation.HORIZONTAL);
                ScrollBar vertical = scrollBar(view, Orientation.VERTICAL);
                horizontal.setValue(horizontal.getMax());
                // scroll bar down (its maximum) shows the bottom of the tree, i.e. the lowest y
                vertical.setValue(vertical.getMax());
                return new double[]{horizontal.getMax(), vertical.getMin()};
            });
            assertEquals(expected[0], onFx(() -> camera(view).getViewCenter().x));
            assertEquals(expected[1], onFx(() -> camera(view).getViewCenter().y));
        }
    }

    @Test
    @Timeout(60)
    public void showLabelsTogglesTheNodeText(@TempDir Path tmp) throws Exception {
        try (EccoService service = sample(tmp)) {
            FeaturesView view = loaded(service);
            assertEquals(StyleConstants.TextMode.NORMAL, onFx(() -> node(view, "A").getStyle().getTextMode()));

            onFx(() -> showLabels(view).setSelected(false));
            waitUntil("hidden labels", () -> node(view, "A").getStyle().getTextMode() == StyleConstants.TextMode.HIDDEN);
            onFx(() -> showLabels(view).setSelected(true));
            waitUntil("shown labels", () -> node(view, "A").getStyle().getTextMode() == StyleConstants.TextMode.NORMAL);
        }
    }

    @Test
    @Timeout(60)
    public void aHiddenTabDrawsNothingUntilItIsShownAgain(@TempDir Path tmp) throws Exception {
        try (EccoService service = sample(tmp)) {
            FeaturesView view = loaded(service);

            view.setTabVisible(false);
            commit(service, tmp.resolve("v4"), "Base, D", Map.of("d.txt", "d\n"));
            onFx(() -> null); // the status event's own runLater has run
            Thread.sleep(500);
            assertEquals(Set.of("Base", "A", "B", "C"), onFx(() -> labels(view)), "not drawn while hidden");
            assertFalse(onFx(() -> view.isDisable()));

            view.setTabVisible(true);
            waitUntil("the new feature", () -> labels(view).contains("D"));
            assertEquals(Set.of("Base->A", "A->B", "Base->C", "Base->D"), onFx(() -> edges(view, "tree")));
        }
    }

    @Test
    @Timeout(60)
    public void aClosedRepositoryDisablesTheViewAndDropsTheGraph(@TempDir Path tmp) throws Exception {
        EccoService service = sample(tmp);
        try {
            FeaturesView view = loaded(service);

            service.close();
            waitUntil("the view is disabled", () -> view.isDisable() && find(view, n -> n instanceof FxViewPanel) == null);
        } finally {
            service.close();
        }
    }

    // --- helpers -------------------------------------------------------------------------------------

    /** The first view of the tree frames whole boxes, not just their centres: none is cut off at the edges. */
    @Test
    @Timeout(60)
    public void theFirstTreeViewShowsEveryBoxWhole(@TempDir Path tmp) throws Exception {
        try (EccoService service = sample(tmp)) {
            FeaturesView view = loaded(service);
            javafx.stage.Stage stage = onFx(() -> {
                javafx.stage.Stage s = new javafx.stage.Stage();
                s.setScene(new javafx.scene.Scene(view, 800, 500));
                s.show();
                return s;
            });
            try {
                waitUntil("a rendered frame", () -> camera(view).getMetrics().viewport[2] > 0);
                double width = onFx(() -> camera(view).getMetrics().viewport[2]);
                double height = onFx(() -> camera(view).getMetrics().viewport[3]);
                for (FeatureModelTree.Placement p : FeatureModelTree.compute(service.getRepository())) {
                    for (double dx : new double[]{-NODE_WIDTH / 2, NODE_WIDTH / 2}) {
                        for (double dy : new double[]{-NODE_HEIGHT / 2, NODE_HEIGHT / 2}) {
                            org.graphstream.ui.geom.Point3 px = onFx(() -> camera(view).transformGuToPx(p.x * X_SPACING + dx, -p.depth * Y_SPACING + dy, 0));
                            String where = p.feature.getName() + " corner at " + px;
                            assertTrue(px.x >= 0 && px.x <= width, where);
                            assertTrue(px.y >= 0 && px.y <= height, where);
                        }
                    }
                }
            } finally {
                onFx(stage::close);
            }
        }
    }

    /**
     * Suggested subfeatures make the hierarchy: an import commits all features so far each time,
     * which the heuristic alone turns into a chain BASE -> A -> B -> C. With A and B suggested
     * under BASE and C under A, BASE branches; the suggested parents' edges have the suggestion's colour.
     */
    @Test
    @Timeout(60)
    public void suggestedSubfeaturesMakeTheHierarchy(@TempDir Path tmp) throws Exception {
        String location = System.getProperty(at.jku.isse.ecco.service.llm.FeatureSuggestionLog.LOCATION_PROPERTY);
        Path log = tmp.resolve("suggestions.jsonl");
        System.setProperty(at.jku.isse.ecco.service.llm.FeatureSuggestionLog.LOCATION_PROPERTY, log.toString());
        try (EccoService service = repository(tmp.resolve("repo"))) {
            commit(service, tmp.resolve("v0"), "BASE", Map.of("base.txt", "base\n"));
            commit(service, tmp.resolve("v1"), "BASE, A", Map.of("a.txt", "a\n"));
            commit(service, tmp.resolve("v2"), "BASE, A, B", Map.of("b.txt", "b\n"));
            commit(service, tmp.resolve("v3"), "BASE, A, B, C", Map.of("c.txt", "c\n"));
            assertEquals(Set.of("BASE->A", "A->B", "B->C"), FeatureModelTree.compute(service.getRepository()).stream().filter(p -> p.parent != null)
                    .map(p -> p.parent.getName() + "->" + p.feature.getName()).collect(Collectors.toSet()), "the heuristic: a chain");
            String repo = service.getRepositoryDir().toString().replace("\\", "\\\\");
            java.nio.file.Files.write(log, List.of(
                    entry(repo, "BASE", "BASE, A", "{\"A\":\"BASE\"}"),
                    entry(repo, "BASE, A", "BASE, A, B", "{\"B\":\"BASE\"}"),
                    entry(repo, "BASE, A, B", "BASE, A, B, C", "{\"C\":\"A\"}")));

            FeaturesView view = loaded(service);

            waitUntil("the suggested hierarchy", () -> edges(view, "suggested_subfeature").equals(Set.of("BASE->A", "BASE->B", "A->C")));
            assertEquals(Set.of(), onFx(() -> edges(view, "tree")), "no other parents");
            GraphicNode base = onFx(() -> node(view, "BASE")), a = onFx(() -> node(view, "A")), b = onFx(() -> node(view, "B"));
            assertEquals(a.getY(), b.getY(), "A and B side by side");
            assertTrue(a.getY() < base.getY(), "below BASE");
        } finally {
            if (location == null)
                System.clearProperty(at.jku.isse.ecco.service.llm.FeatureSuggestionLog.LOCATION_PROPERTY);
            else
                System.setProperty(at.jku.isse.ecco.service.llm.FeatureSuggestionLog.LOCATION_PROPERTY, location);
        }
    }

    /** A log line of an imported commit with the parents the LLM named. */
    private static String entry(String eccoRepository, String before, String committed, String parents) {
        return "{\"eccoRepository\":\"" + eccoRepository + "\",\"gitRepository\":\"/g\",\"before\":\"" + before + "\",\"committed\":\"" + committed
                + "\",\"outcome\":\"auto\",\"parents\":" + parents + ",\"positions\":{}}";
    }

    /** One parent per feature: what the LLM and the code agree on, then the code's, then the LLM's - of equals the larger share. */
    @Test
    public void theStrongestSuggestionPlacesAFeature() {
        var code = new at.jku.isse.ecco.service.llm.ImportedFeatureTraces.RelationSuggestion("A", "X", at.jku.isse.ecco.service.llm.ImportedFeatureTraces.Relation.SUBFEATURE, 0.3, 6, 0);
        var llm = new at.jku.isse.ecco.service.llm.ImportedFeatureTraces.RelationSuggestion("A", "BASE", at.jku.isse.ecco.service.llm.ImportedFeatureTraces.Relation.SUBFEATURE, 0.9, 1, 0,
                at.jku.isse.ecco.service.llm.ImportedFeatureTraces.Basis.LLM);
        var both = new at.jku.isse.ecco.service.llm.ImportedFeatureTraces.RelationSuggestion("B", "Y", at.jku.isse.ecco.service.llm.ImportedFeatureTraces.Relation.SUBFEATURE, 0.2, 6, 0,
                at.jku.isse.ecco.service.llm.ImportedFeatureTraces.Basis.LLM_AND_CODE);
        var stronger = new at.jku.isse.ecco.service.llm.ImportedFeatureTraces.RelationSuggestion("B", "Z", at.jku.isse.ecco.service.llm.ImportedFeatureTraces.Relation.SUBFEATURE, 0.8, 9, 0);
        var requires = new at.jku.isse.ecco.service.llm.ImportedFeatureTraces.RelationSuggestion("C", "X", at.jku.isse.ecco.service.llm.ImportedFeatureTraces.Relation.REQUIRES, 0.5, 9, 2);

        assertEquals(Map.of("A", "X", "B", "Y"), FeaturesView.suggestedParents(List.of(llm, code, stronger, both, requires)));
    }

    private static EccoService sample(Path tmp) throws Exception {
        EccoService service = repository(tmp.resolve("repo"));
        commit(service, tmp.resolve("v0"), "Base", Map.of("base.txt", "base\n"));
        commit(service, tmp.resolve("v1"), "Base, A", Map.of("a.txt", "a\n"));
        commit(service, tmp.resolve("v2"), "Base, A, B", Map.of("b.txt", "b\n"));
        commit(service, tmp.resolve("v3"), "Base, C", Map.of("c.txt", "c\n"));
        return service;
    }

    private static FeaturesView loaded(EccoService service) throws Exception {
        int features = service.getRepository().getFeatures().size();
        FeaturesView view = onFx(() -> new FeaturesView(service, new MinimizationResults(service)));
        waitUntil("the drawn graph", () -> find(view, n -> n instanceof FxViewPanel) != null
                && graphic(view).getNodeCount() >= features && !toolBar(view).isDisable());
        return view;
    }

    private static String id(EccoService service, String feature) {
        return service.getRepository().getFeatures().stream().filter(f -> f.getName().equals(feature))
                .map(Feature::getId).findFirst().orElseThrow();
    }

    /** What the viewer renders: the view's own graph as mirrored into the viewer. */
    private static GraphicGraph graphic(FeaturesView view) {
        FxViewPanel panel = (FxViewPanel) find(view, n -> n instanceof FxViewPanel);
        assertNotNull(panel, "no graph shown");
        return panel.getViewer().getGraphicGraph();
    }

    private static Set<String> labels(FeaturesView view) {
        return graphic(view).nodes().filter(n -> !n.getId().startsWith("mandatory:"))
                .map(FeaturesViewTest::label).collect(Collectors.toSet());
    }

    private static String label(Node node) {
        return String.valueOf(node.getAttribute("label"));
    }

    private static GraphicNode node(FeaturesView view, String label) {
        return (GraphicNode) graphic(view).nodes().filter(n -> label.equals(label(n))).findFirst().orElseThrow();
    }

    private static String style(FeaturesView view, String label) {
        return String.valueOf(node(view, label).getAttribute("ui.style"));
    }

    /** "parent->child" by label, for the edges of one class (tree, requires, excludes). */
    private static Set<String> edges(FeaturesView view, String kind) {
        return graphic(view).edges().filter(e -> kind.equals(e.getAttribute("ui.class")))
                .map((Edge e) -> label(e.getSourceNode()) + "->" + label(e.getTargetNode())).collect(Collectors.toSet());
    }

    private static ToolBar toolBar(FeaturesView view) {
        return (ToolBar) view.getTop();
    }

    private static ChoiceBox<?> layoutChoice(FeaturesView view) {
        return (ChoiceBox<?>) find(toolBar(view), n -> n instanceof ChoiceBox);
    }

    private static CheckBox showLabels(FeaturesView view) {
        return (CheckBox) find(toolBar(view), n -> n instanceof CheckBox c && "Show Labels".equals(c.getText()));
    }

    private static Camera camera(FeaturesView view) {
        return ((FxViewPanel) find(view, n -> n instanceof FxViewPanel)).getCamera();
    }

    /** The graph's own scroll bars, below and right of it. */
    private static ScrollBar scrollBar(FeaturesView view, Orientation orientation) {
        BorderPane container = (BorderPane) find(view, n -> n instanceof FxViewPanel).getParent();
        return (ScrollBar) (orientation == Orientation.HORIZONTAL ? container.getBottom() : container.getRight());
    }
}
