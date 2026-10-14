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
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.DialogPane;
import javafx.scene.control.ToolBar;
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
 * What the Charts tab decides: the charts stay empty until Refresh, which fills each one from the
 * repository (artifacts per association, leaving out empty ones; revisions per feature; artifacts
 * per depth; modules per order raw and after absorption), and a failing refresh gives the toolbar
 * back.
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
    public void theChartsStayEmptyUntilRefresh(@TempDir Path tmp) throws Exception {
        try (EccoService service = sample(tmp)) {
            ChartsView view = loaded(service);

            assertTrue(onFx(() -> pie(view, "Artifacts per Association").getData().isEmpty()));
            assertTrue(onFx(() -> pie(view, "Revisions per Feature").getData().isEmpty()));
            assertTrue(onFx(() -> depthSeries(view).getData().isEmpty()));
            assertTrue(onFx(() -> orderSeries(view, "Raw").getData().isEmpty()));
        }
    }

    @Test
    @Timeout(60)
    public void refreshShowsTheArtifactsOfEveryAssociationThatHasAny(@TempDir Path tmp) throws Exception {
        try (EccoService service = sample(tmp)) {
            ChartsView view = refreshed(service);

            Map<String, Double> expected = service.getRepository().getAssociations().stream()
                    .filter(a -> a.getRootNode().countArtifacts() > 0)
                    .collect(Collectors.toMap(a -> "A" + a.getId(), a -> (double) a.getRootNode().countArtifacts()));
            assertFalse(expected.isEmpty());
            assertEquals(expected, onFx(() -> pieValues(pie(view, "Artifacts per Association"))));
        }
    }

    @Test
    @Timeout(60)
    public void refreshShowsHowManyRevisionsEachFeatureHas(@TempDir Path tmp) throws Exception {
        try (EccoService service = sample(tmp)) {
            ChartsView view = refreshed(service);

            assertEquals(Map.of("A", 2.0, "B", 1.0), onFx(() -> pieValues(pie(view, "Revisions per Feature"))));
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
    public void aSecondRefreshReplacesTheDataInsteadOfAddingToIt(@TempDir Path tmp) throws Exception {
        try (EccoService service = sample(tmp)) {
            ChartsView view = refreshed(service);
            Map<String, Double> first = onFx(() -> pieValues(pie(view, "Revisions per Feature")));
            int depths = onFx(() -> depthSeries(view).getData().size());

            commit(service, tmp.resolve("v4"), "C.1", Map.of("c.txt", "c\n"));
            onFx(() -> button(view, "Refresh").fire());
            waitUntil("the second refresh", () -> !toolBar(view).isDisable() && pie(view, "Revisions per Feature").getData().size() == 3);

            Map<String, Double> second = onFx(() -> pieValues(pie(view, "Revisions per Feature")));
            assertEquals(first.get("A"), second.get("A"));
            assertEquals(1.0, second.get("C"));
            assertEquals(depths, onFx(() -> depthSeries(view).getData().size()), "the same depths, not twice as many");
        }
    }

    @Test
    @Timeout(60)
    public void aRefreshThatFailsReenablesTheToolbar(@TempDir Path tmp) throws Exception {
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
            onFx(() -> button(view, "Refresh").fire());
            waitUntil("the failure is shown", () -> Window.getWindows().stream()
                    .anyMatch(w -> w.getScene() != null && w.getScene().getRoot() instanceof DialogPane));
            failing.set(false);

            assertFalse(onFx(() -> toolBar(view).isDisable()));
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
        onFx(() -> button(view, "Refresh").fire());
        waitUntil("the refresh", () -> !toolBar(view).isDisable() && !pie(view, "Revisions per Feature").getData().isEmpty());
        return view;
    }

    private static ToolBar toolBar(ChartsView view) {
        return (ToolBar) view.getTop();
    }

    private static PieChart pie(ChartsView view, String title) {
        return (PieChart) chart(view, title);
    }

    private static Chart chart(ChartsView view, String title) {
        Chart chart = (Chart) find(view, n -> n instanceof Chart c && title.equals(c.getTitle()));
        assertNotNull(chart, "no chart '" + title + "'");
        return chart;
    }

    @SuppressWarnings("unchecked")
    private static XYChart.Series<Number, Number> depthSeries(ChartsView view) {
        return ((LineChart<Number, Number>) chart(view, "Artifacts per Depth")).getData().get(0);
    }

    @SuppressWarnings("unchecked")
    private static XYChart.Series<String, Number> orderSeries(ChartsView view, String name) {
        BarChart<String, Number> chart = (BarChart<String, Number>) chart(view, "Modules per Order (raw vs. absorbed)");
        return chart.getData().stream().filter(s -> name.equals(s.getName())).findFirst().orElseThrow();
    }

    private static Map<String, Double> pieValues(PieChart chart) {
        return chart.getData().stream().collect(Collectors.toMap(PieChart.Data::getName, PieChart.Data::getPieValue));
    }

    private static Map<String, Integer> categoryValues(XYChart.Series<String, Number> series) {
        return series.getData().stream().collect(Collectors.toMap(XYChart.Data::getXValue, d -> d.getYValue().intValue()));
    }
}
