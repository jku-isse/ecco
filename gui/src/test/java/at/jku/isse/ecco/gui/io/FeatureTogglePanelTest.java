package at.jku.isse.ecco.gui.io;

import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.mining.ConstraintMiner;
import at.jku.isse.ecco.service.EccoService;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import javafx.stage.WindowEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import static at.jku.isse.ecco.gui.FxTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The feature toggle panel: every click reports the configuration of the checked features (each at
 * its latest revision), accepted MANDATORY features are locked on, and accepted REQUIRES
 * constraints pull in or drop the other feature. The panel (a Stage) is never shown.
 */
public class FeatureTogglePanelTest {

    @BeforeAll
    public static void start() throws InterruptedException {
        startToolkit();
    }

    private static CheckBox box(FeatureTogglePanel panel, String feature) {
        return (CheckBox) find(panel.getScene().getRoot(), n -> n instanceof CheckBox c && feature.equals(c.getText()));
    }

    private static List<String> boxes(FeatureTogglePanel panel) {
        return findAll(panel.getScene().getRoot(), n -> n instanceof CheckBox).stream().map(n -> ((CheckBox) n).getText()).toList();
    }

    private static Set<String> revisions(Configuration configuration) {
        return Arrays.stream(configuration.getFeatureRevisions())
                .map(r -> r.getFeature().getName() + "." + r.getId()).collect(Collectors.toSet());
    }

    /** Every commit has A (so A re-mines as MANDATORY); B never comes without C, C does without B (so B REQUIRES C). */
    private static EccoService constrained(Path tmp) throws Exception {
        EccoService service = repository(tmp.resolve("repo"));
        for (int i = 0; i < 4; i++)
            commit(service, tmp.resolve("v" + i), "A.1, B.1, C.1", Map.of("f.txt", "f " + i + "\n"));
        commit(service, tmp.resolve("w"), "A.1, C.1, D.1", Map.of("d.txt", "d\n"));
        commit(service, tmp.resolve("x"), "A.1, D.1", Map.of("x.txt", "x\n"));
        return service;
    }

    /** Each click reports the checked features, every one at its latest revision; nothing is reported before a click. */
    @Test
    @Timeout(60)
    public void eachToggleReportsTheCheckedFeaturesAtTheirLatestRevision(@TempDir Path tmp) throws Exception {
        try (EccoService service = repository(tmp.resolve("repo"))) {
            commit(service, tmp.resolve("v1"), "A.1, b.1", Map.of("a.txt", "a\n"));
            commit(service, tmp.resolve("v2"), "A.2, c.1", Map.of("a.txt", "a2\n"));
            List<Configuration> reported = new CopyOnWriteArrayList<>();
            FeatureTogglePanel panel = onFx(() -> new FeatureTogglePanel(service, reported::add));
            assertEquals(List.of("A", "b", "c"), onFx(() -> boxes(panel)));
            assertTrue(reported.isEmpty());

            onFx(() -> box(panel, "A").fire());
            assertEquals(Set.of("A.2"), revisions(reported.get(0)));
            onFx(() -> box(panel, "c").fire());
            assertEquals(Set.of("A.2", "c.1"), revisions(reported.get(1)));
            onFx(() -> box(panel, "A").fire());
            assertEquals(Set.of("c.1"), revisions(reported.get(2)));
            assertEquals(3, reported.size());
        }
    }

    /** An accepted MANDATORY feature starts checked and disabled, and is part of every reported configuration. */
    @Test
    @Timeout(60)
    public void aMandatoryFeatureIsLockedOn(@TempDir Path tmp) throws Exception {
        try (EccoService service = constrained(tmp)) {
            service.acceptConstraint(ConstraintMiner.Kind.MANDATORY, "A", null);
            List<Configuration> reported = new CopyOnWriteArrayList<>();
            FeatureTogglePanel panel = onFx(() -> new FeatureTogglePanel(service, reported::add));
            onFx(() -> {
                assertTrue(box(panel, "A").isSelected());
                assertTrue(box(panel, "A").isDisabled());
                assertFalse(box(panel, "D").isDisabled());
            });

            onFx(() -> box(panel, "D").fire());
            assertEquals(Set.of("A.1", "D.1"), revisions(reported.get(0)));
        }
    }

    /** Checking B pulls in the C it requires; unchecking C afterwards drops B rather than re-checking C. */
    @Test
    @Timeout(60)
    public void anAcceptedRequiresPullsInOrDropsTheOtherFeature(@TempDir Path tmp) throws Exception {
        try (EccoService service = constrained(tmp)) {
            service.acceptConstraint(ConstraintMiner.Kind.REQUIRES, "B", "C");
            List<Configuration> reported = new CopyOnWriteArrayList<>();
            FeatureTogglePanel panel = onFx(() -> new FeatureTogglePanel(service, reported::add));

            onFx(() -> box(panel, "B").fire());
            assertEquals(Set.of("B.1", "C.1"), revisions(reported.get(0)));
            assertTrue(onFx(() -> box(panel, "C").isSelected()));

            onFx(() -> box(panel, "C").fire());
            assertEquals(Set.of(), revisions(reported.get(1)));
            assertFalse(onFx(() -> box(panel, "B").isSelected()));
        }
    }

    /**
     * Constraints accepted after the panel was built count from its next showing on; one that is
     * no longer accepted unlocks its feature but leaves it checked.
     */
    @Test
    @Timeout(60)
    public void showingAgainPicksUpConstraintChanges(@TempDir Path tmp) throws Exception {
        try (EccoService service = constrained(tmp)) {
            FeatureTogglePanel panel = onFx(() -> new FeatureTogglePanel(service, c -> { }));
            assertFalse(onFx(() -> box(panel, "A").isSelected() || box(panel, "A").isDisabled()));

            service.acceptConstraint(ConstraintMiner.Kind.MANDATORY, "A", null);
            onFx(() -> panel.getOnShowing().handle(new WindowEvent(panel, WindowEvent.WINDOW_SHOWING)));
            assertTrue(onFx(() -> box(panel, "A").isSelected() && box(panel, "A").isDisabled()));

            service.unacceptConstraint(at.jku.isse.ecco.core.Constraint.Kind.MANDATORY, "A", null);
            onFx(() -> panel.getOnShowing().handle(new WindowEvent(panel, WindowEvent.WINDOW_SHOWING)));
            onFx(() -> {
                assertFalse(box(panel, "A").isDisabled(), "unlocked");
                assertTrue(box(panel, "A").isSelected(), "but left checked");
            });
        }
    }

    /** The filter hides (and unmanages) every checkbox whose feature name does not contain it. */
    @Test
    @Timeout(60)
    public void theFilterHidesNonMatchingFeatures(@TempDir Path tmp) throws Exception {
        try (EccoService service = constrained(tmp)) {
            FeatureTogglePanel panel = onFx(() -> new FeatureTogglePanel(service, c -> { }));
            onFx(() -> ((TextField) find(panel.getScene().getRoot(), n -> n instanceof TextField)).setText("d"));
            onFx(() -> {
                assertTrue(box(panel, "D").isVisible() && box(panel, "D").isManaged());
                for (String other : List.of("A", "B", "C"))
                    assertFalse(box(panel, other).isVisible() || box(panel, other).isManaged(), other);
            });
            onFx(() -> ((TextField) find(panel.getScene().getRoot(), n -> n instanceof TextField)).setText(""));
            assertTrue(onFx(() -> List.of("A", "B", "C", "D").stream().allMatch(f -> box(panel, f).isVisible())));
        }
    }
}
