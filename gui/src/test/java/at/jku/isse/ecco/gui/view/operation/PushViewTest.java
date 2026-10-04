package at.jku.isse.ecco.gui.view.operation;

import at.jku.isse.ecco.core.Remote;
import at.jku.isse.ecco.service.EccoService;
import javafx.scene.Scene;
import javafx.scene.control.CheckBoxTreeItem;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static at.jku.isse.ecco.gui.FxTestSupport.*;
import static at.jku.isse.ecco.gui.view.operation.PullViewTest.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * What the Push dialog decides: which remote it pushes to, which of this repository's feature
 * revisions it leaves out (typed, or unticked in the feature tree), and how it reports an unknown
 * remote or an exclusion that names no feature revision. The remote is a second local repository.
 */
public class PushViewTest {

    private Stage stage;

    @BeforeAll
    public static void start() throws InterruptedException {
        startToolkit();
    }

    @AfterEach
    public void closeStage() throws Exception {
        if (stage != null)
            onFx(() -> stage.close());
    }

    @Test
    @Timeout(60)
    public void pushesEverythingToTheChosenRemote(@TempDir Path tmp) throws Exception {
        Path parent = parent(tmp);
        try (EccoService service = source(tmp, parent)) {
            PushView view = show(service);
            onFx(() -> {
                chooseRemote(view, "parent");
                button(view, "Push |").fire();
            });
            waitUntil("the push succeeded (the dialog closed)", () -> !stage.isShowing());
            assertFalse(onFx(view::isDisable));
        }
        assertEquals(Set.of("A", "B"), parentFeatures(parent));
    }

    @Test
    @Timeout(60)
    public void anUntickedRevisionOfThisRepositoryIsNotPushed(@TempDir Path tmp) throws Exception {
        Path parent = parent(tmp);
        try (EccoService service = source(tmp, parent)) {
            String b = revisionString(tmp.resolve("origin"), "B");
            PushView view = show(service);
            onFx(() -> {
                chooseRemote(view, "parent");
                button(view, "Select >").fire();
            });
            // this repository's own features, no fetch needed
            assertEquals(Set.of("A", "B"), onFx(() -> treeFeatures(view)));
            assertTrue(onFx(() -> deselectionField(view).getText().isEmpty()));

            onFx(() -> untick(view, "B"));
            assertEquals(b, onFx(() -> deselectionField(view).getText()));
            onFx(() -> button(view, "Push |").fire());
            waitUntil("the push succeeded (the dialog closed)", () -> !stage.isShowing());
        }
        assertEquals(Set.of("A"), parentFeatures(parent));
    }

    @Test
    @Timeout(60)
    public void nothingCanBePushedBeforeARemoteIsChosen(@TempDir Path tmp) throws Exception {
        Path parent = parent(tmp);
        try (EccoService service = source(tmp, parent)) {
            service.addRemote("other", tmp.resolve("other").toString(), Remote.Type.LOCAL);
            PushView view = show(service);
            ComboBox<Remote> remotes = onFx(() -> remoteBox(view));
            assertEquals(List.of("-", "other", "parent"), onFx(() -> sortedNames(remotes)));
            assertTrue(onFx(() -> button(view, "Push |").isDisable() && button(view, "Select >").isDisable()));

            onFx(() -> chooseRemote(view, "parent"));
            assertFalse(onFx(() -> button(view, "Push |").isDisable() || button(view, "Select >").isDisable()));

            // back from the selection step: the same remotes, the chosen one still chosen
            onFx(() -> button(view, "Select >").fire());
            onFx(() -> button(view, "< Remote").fire());
            assertEquals(List.of("-", "other", "parent"), onFx(() -> sortedNames(remotes)));
            assertEquals("parent", onFx(() -> remotes.getValue().getName()));
        }
    }

    @Test
    @Timeout(60)
    public void anExclusionNamingNoFeatureRevisionIsReportedAndNothingIsPushed(@TempDir Path tmp) throws Exception {
        Path parent = parent(tmp);
        try (EccoService service = source(tmp, parent)) {
            PushView view = show(service);
            onFx(() -> {
                chooseRemote(view, "parent");
                button(view, "Select >").fire();
                deselectionField(view).setText("B");
                button(view, "Push |").fire();
            });
            waitUntil("the error", () -> errorText(view) != null);

            assertTrue(onFx(() -> errorText(view)).startsWith("Error during push operation."));
            assertTrue(onFx(() -> errorText(view)).contains("Invalid feature revisions string provided."), onFx(() -> errorText(view)));
            assertFalse(onFx(view::isDisable), "usable again");
            assertTrue(onFx(stage::isShowing), "the dialog stays open with the error");
        }
        assertEquals(Set.of(), parentFeatures(parent));
    }

    @Test
    @Timeout(60)
    public void anUnknownRemoteIsReported(@TempDir Path tmp) throws Exception {
        Path parent = parent(tmp);
        try (EccoService service = source(tmp, parent)) {
            PushView view = show(service);
            // removed while the dialog is open
            service.removeRemote("parent");
            onFx(() -> {
                chooseRemote(view, "parent");
                button(view, "Push |").fire();
            });
            waitUntil("the error", () -> errorText(view) != null);

            assertTrue(onFx(() -> errorText(view)).contains("Remote 'parent' does not exist."), onFx(() -> errorText(view)));
        }
        assertEquals(Set.of(), parentFeatures(parent));
    }

    // --- helpers -------------------------------------------------------------------------------------

    /** An empty repository, closed again. */
    private static Path parent(Path tmp) throws Exception {
        Path dir = tmp.resolve("parent");
        repository(dir).close();
        return dir;
    }

    /** The repository with "A" and "A, B" (see PullViewTest#origin), open, with {@code parent} as its LOCAL remote "parent". */
    private static EccoService source(Path tmp, Path parent) throws Exception {
        Path dir = origin(tmp);
        EccoService service = new EccoService();
        service.setRepositoryDir(dir.resolve(".ecco"));
        service.open();
        service.addRemote("parent", parent.toString(), Remote.Type.LOCAL);
        return service;
    }

    private static Set<String> parentFeatures(Path parent) {
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(parent.resolve(".ecco"));
            service.open();
            return features(service);
        }
    }

    /** In an off-screen window: the dialog closes it when the push succeeded. */
    private PushView show(EccoService service) throws Exception {
        return onFx(() -> {
            PushView view = new PushView(service);
            stage = new Stage();
            stage.setScene(new Scene(view));
            stage.setX(-10_000);
            stage.setY(-10_000);
            stage.show();
            return view;
        });
    }

    @SuppressWarnings("unchecked")
    private static TreeView<PushView.FeatureInfo> tree(PushView view) {
        return (TreeView<PushView.FeatureInfo>) find(view.getCenter(), n -> n instanceof TreeView);
    }

    private static Set<String> treeFeatures(PushView view) {
        return tree(view).getRoot().getChildren().stream().map(i -> i.getValue().getFeature().getName()).collect(Collectors.toSet());
    }

    private static void untick(PushView view, String feature) {
        TreeItem<PushView.FeatureInfo> featureItem = tree(view).getRoot().getChildren().stream()
                .filter(i -> i.getValue().getFeature().getName().equals(feature)).findFirst().orElseThrow();
        ((CheckBoxTreeItem<PushView.FeatureInfo>) featureItem.getChildren().get(0)).setSelected(false);
    }
}
