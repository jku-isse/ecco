package at.jku.isse.ecco.gui.view;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.composition.LazyCompositionRootNode;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.service.EccoService;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.Chart;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.DialogPane;
import javafx.scene.control.TitledPane;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static at.jku.isse.ecco.gui.FxTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * What the Charts tab decides: the charts load when the tab is shown (nothing is computed while it
 * is hidden) and again after every change of the repository, each one from the repository
 * (artifacts per association, leaving out empty ones, and revisions per feature, as bars largest
 * first; artifacts per depth; modules per order raw and after absorption - computed only while its pane is open), and a failing load is shown.
 */
public class ChartsViewTest {

    @BeforeAll
    public static void start() throws InterruptedException {
        startToolkit();
    }

    @AfterEach
    public void closeWindows() throws Exception {
        onFx(() -> {
            for (Window window : List.copyOf(Window.getWindows()))
                window.hide();
        });
    }

    @Test
    @Timeout(60)
    public void theChartsStayEmptyWhileHiddenAndLoadWhenShown(@TempDir Path tmp) throws Exception {
        try (EccoService service = sample(tmp)) {
            ChartsView view = onFx(() -> {
                ChartsView hidden = new ChartsView(service);
                hidden.setTabVisible(false); // as MainView does for every view it does not show
                return hidden;
            });
            waitUntil("the view is enabled", () -> !view.isDisable());

            assertTrue(onFx(() -> bars(view, "Artifacts per Association").isEmpty()));
            assertTrue(onFx(() -> bars(view, "Revisions per Feature").isEmpty()));
            assertTrue(onFx(() -> depthSeries(view).getData().isEmpty()));
            assertTrue(onFx(() -> orderSeries(view, "Raw").getData().isEmpty()));

            onFx(() -> view.setTabVisible(true));
            waitUntil("the charts", () -> !bars(view, "Revisions per Feature").isEmpty());
        }
    }

    @Test
    @Timeout(60)
    public void refreshShowsTheArtifactsOfEveryAssociationThatHasAny(@TempDir Path tmp) throws Exception {
        try (EccoService service = sample(tmp)) {
            ChartsView view = refreshed(service);

            Map<String, Double> expected = service.getRepository().getAssociations().stream()
                    .filter(a -> a.getRootNode().countArtifacts() > 0)
                    .collect(Collectors.toMap(a -> a.getId().substring(0, 8), a -> (double) a.getRootNode().countArtifacts()));
            assertFalse(expected.isEmpty());
            assertEquals(expected, onFx(() -> barValues(view, "Artifacts per Association")));
            List<Double> topDown = onFx(() -> bars(view, "Artifacts per Association").stream().map(d -> d.getXValue().doubleValue()).toList()).reversed();
            assertEquals(topDown.stream().sorted(java.util.Comparator.reverseOrder()).toList(), topDown, "the largest at the top");
        }
    }

    /** At most the largest {@link ChartsView#MAX_ASSOCIATION_BARS}, largest first, ties by name. */
    @Test
    public void onlyTheLargestBarsAreShownLargestFirst() {
        List<ChartsView.Bar> bars = new java.util.ArrayList<>();
        for (int i = 0; i < ChartsView.MAX_ASSOCIATION_BARS + 5; i++)
            bars.add(new ChartsView.Bar("b" + i, i % 7, ""));
        List<ChartsView.Bar> shown = ChartsView.largestFirst(bars, ChartsView.MAX_ASSOCIATION_BARS);
        assertEquals(ChartsView.MAX_ASSOCIATION_BARS, shown.size());
        assertEquals(6, shown.get(0).value());
        assertEquals(List.of("b13", "b20", "b6"), shown.stream().limit(3).map(ChartsView.Bar::label).toList());
        assertTrue(shown.stream().noneMatch(b -> b.value() == 0), "the smallest are left out");
    }

    @Test
    @Timeout(60)
    public void refreshShowsHowManyRevisionsEachFeatureHas(@TempDir Path tmp) throws Exception {
        try (EccoService service = sample(tmp)) {
            ChartsView view = refreshed(service);

            assertEquals(Map.of("A", 2.0, "B", 1.0), onFx(() -> barValues(view, "Revisions per Feature")));
        }
    }

    @Test
    @Timeout(60)
    public void refreshShowsTheArtifactsPerDepthOfAllAssociationsTogether(@TempDir Path tmp) throws Exception {
        try (EccoService service = sample(tmp)) {
            ChartsView view = refreshed(service);

            LazyCompositionRootNode composition = new LazyCompositionRootNode();
            for (Association association : service.getRepository().getAssociations())
                composition.addOrigNode(association.getRootNode());
            Map<Integer, Integer> expected = new TreeMap<>(composition.countArtifactsPerDepth());
            Map<Integer, Integer> shown = onFx(() -> depthSeries(view).getData().stream()
                    .collect(Collectors.toMap(d -> d.getXValue().intValue(), d -> d.getYValue().intValue(), (a, b) -> a, TreeMap::new)));
            assertEquals(expected, shown);
            assertTrue(shown.size() > 1, "more than one depth: " + shown);
        }
    }

    @Test
    @Timeout(60)
    public void refreshShowsTheModulesPerOrderRawAndAbsorbed(@TempDir Path tmp) throws Exception {
        try (EccoService service = sample(tmp)) {
            ChartsView view = refreshed(service);
            assertTrue(onFx(() -> orderSeries(view, "Raw").getData().isEmpty()), "not computed while folded away");

            onFx(() -> advancedPane(view).setExpanded(true));
            waitUntil("computed when opened", () -> !orderSeries(view, "Raw").getData().isEmpty());
            Map<String, Integer> raw = new HashMap<>();
            for (Association association : service.getRepository().getAssociations())
                for (Module module : association.computeCondition().getModules().keySet())
                    raw.merge(Integer.toString(module.getOrder()), 1, Integer::sum);
            assertEquals(raw, onFx(() -> categoryValues(orderSeries(view, "Raw"))));

            Map<String, Integer> absorbed = onFx(() -> categoryValues(orderSeries(view, "Absorbed")));
            assertFalse(absorbed.isEmpty());
            int rawTotal = raw.values().stream().mapToInt(Integer::intValue).sum();
            int absorbedTotal = absorbed.values().stream().mapToInt(Integer::intValue).sum();
            assertTrue(absorbedTotal < rawTotal, "absorption drops redundant terms: " + absorbed + " vs " + raw);
        }
    }

    @Test
    @Timeout(60)
    public void aCommitReloadsTheChartsInsteadOfAddingToThem(@TempDir Path tmp) throws Exception {
        try (EccoService service = sample(tmp)) {
            ChartsView view = refreshed(service);
            Map<String, Double> first = onFx(() -> barValues(view, "Revisions per Feature"));
            int depths = onFx(() -> depthSeries(view).getData().size());

            commit(service, tmp.resolve("v4"), "C.1", Map.of("c.txt", "c\n"));
            waitUntil("the reload", () -> bars(view, "Revisions per Feature").size() == 3);

            Map<String, Double> second = onFx(() -> barValues(view, "Revisions per Feature"));
            assertEquals(first.get("A"), second.get("A"));
            assertEquals(1.0, second.get("C"));
            assertEquals(depths, onFx(() -> depthSeries(view).getData().size()), "the same depths, not twice as many");
        }
    }

    @Test
    @Timeout(60)
    public void aLoadThatFailsIsShown(@TempDir Path tmp) throws Exception {
        Files.createDirectories(tmp.resolve("repo"));
        AtomicBoolean failing = new AtomicBoolean(false);
        try (EccoService service = new EccoService() {
            @Override
            public synchronized Repository getRepository() {
                if (failing.get())
                    throw new EccoException("repository unavailable");
                return super.getRepository();
            }
        }) {
            service.setRepositoryDir(tmp.resolve("repo").resolve(".ecco"));
            service.init();
            commit(service, tmp.resolve("v1"), "A.1", Map.of("a.txt", "a\n"));
            ChartsView view = loaded(service);

            failing.set(true);
            onFx(() -> {
                view.setTabVisible(false);
                view.setTabVisible(true);
            });
            waitUntil("the failure is shown", () -> Window.getWindows().stream()
                    .anyMatch(w -> w.getScene() != null && w.getScene().getRoot() instanceof DialogPane));
            failing.set(false);
        }
    }

    @Test
    @Timeout(60)
    public void aClosedRepositoryDisablesTheView(@TempDir Path tmp) throws Exception {
        EccoService service = repository(tmp.resolve("repo"));
        try {
            ChartsView view = loaded(service);

            service.close();
            waitUntil("the view is disabled", view::isDisable);
        } finally {
            service.close();
        }
    }

    // --- helpers -------------------------------------------------------------------------------------

    /** Feature A in two revisions, B in one, artifacts at several depths. */
    private static EccoService sample(Path tmp) throws Exception {
        EccoService service = repository(tmp.resolve("repo"));
        commit(service, tmp.resolve("v1"), "A.1", Map.of("a.txt", "a1\na2\n"));
        commit(service, tmp.resolve("v2"), "A.1, B.1", Map.of("a.txt", "a1\na2\n", "dir/b.txt", "b\n"));
        commit(service, tmp.resolve("v3"), "A.2", Map.of("a.txt", "a1\na3\n"));
        return service;
    }

    private static ChartsView loaded(EccoService service) throws Exception {
        ChartsView view = onFx(() -> new ChartsView(service));
        waitUntil("the view is enabled", () -> !view.isDisable());
        return view;
    }

    private static ChartsView refreshed(EccoService service) throws Exception {
        ChartsView view = loaded(service);
        waitUntil("the charts", () -> !bars(view, "Revisions per Feature").isEmpty());
        return view;
    }

    @SuppressWarnings("unchecked")
    private static List<XYChart.Data<Number, String>> bars(ChartsView view, String title) {
        return ((BarChart<Number, String>) chart(view, title)).getData().get(0).getData();
    }

    /** The chart in the pane titled [title] - the charts have no titles of their own any more, they repeated the pane's. */
    private static Chart chart(ChartsView view, String title) {
        TitledPane pane = (TitledPane) find(view, n -> n instanceof TitledPane p && title.equals(p.getText()));
        assertNotNull(pane, "no pane '" + title + "'");
        Chart chart = (Chart) pane.getContent();
        assertNotNull(chart, "no chart '" + title + "'");
        return chart;
    }

    @SuppressWarnings("unchecked")
    private static XYChart.Series<Number, Number> depthSeries(ChartsView view) {
        return ((LineChart<Number, Number>) chart(view, "Artifacts per Depth")).getData().get(0);
    }

    private static TitledPane advancedPane(ChartsView view) {
        return (TitledPane) find(view, n -> n instanceof TitledPane p && "Advanced: Modules per Order (raw vs. absorbed)".equals(p.getText()));
    }

    @SuppressWarnings("unchecked")
    private static XYChart.Series<String, Number> orderSeries(ChartsView view, String name) {
        BarChart<String, Number> chart = (BarChart<String, Number>) chart(view, "Advanced: Modules per Order (raw vs. absorbed)");
        return chart.getData().stream().filter(s -> name.equals(s.getName())).findFirst().orElseThrow();
    }

    private static Map<String, Double> barValues(ChartsView view, String title) {
        return bars(view, title).stream().collect(Collectors.toMap(XYChart.Data::getYValue, d -> d.getXValue().doubleValue()));
    }

    private static Map<String, Integer> categoryValues(XYChart.Series<String, Number> series) {
        return series.getData().stream().collect(Collectors.toMap(XYChart.Data::getXValue, d -> d.getYValue().intValue()));
    }
}
