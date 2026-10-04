package at.jku.isse.ecco.gui.view.operation;

import at.jku.isse.ecco.core.Remote;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.gui.ExceptionTextArea;
import at.jku.isse.ecco.service.EccoService;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBoxTreeItem;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static at.jku.isse.ecco.gui.FxTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * What the Pull dialog decides: which remote it pulls from, which feature revisions it leaves out
 * (typed, or unticked in the remote's feature tree), and how it reports an unknown remote or an
 * exclusion that names no feature revision. The remote is a second local repository.
 */
public class PullViewTest {

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
    public void pullsEverythingFromTheChosenRemote(@TempDir Path tmp) throws Exception {
        Path origin = origin(tmp);
        try (EccoService service = target(tmp, origin)) {
            PullView view = show(service);
            onFx(() -> {
                chooseRemote(view, "origin");
                button(view, "Pull |").fire();
            });
            waitUntil("the pull succeeded (the dialog closed)", () -> !stage.isShowing());

            assertEquals(Set.of("A", "B"), features(service));
            Path out = Files.createDirectories(tmp.resolve("out"));
            service.setBaseDir(out);
            service.checkout("A, B");
            assertEquals("a\n", Files.readString(out.resolve("a.txt")));
            assertEquals("b\n", Files.readString(out.resolve("b.txt")));
        }
    }

    @Test
    @Timeout(60)
    public void aTypedExclusionIsNotPulled(@TempDir Path tmp) throws Exception {
        Path origin = origin(tmp);
        String b = revisionString(origin, "B");
        try (EccoService service = target(tmp, origin)) {
            PullView view = show(service);
            onFx(() -> {
                chooseRemote(view, "origin");
                button(view, "Select >").fire();
                deselectionField(view).setText(b);
                button(view, "Pull |").fire();
            });
            waitUntil("the pull succeeded (the dialog closed)", () -> !stage.isShowing());

            assertEquals(Set.of("A"), features(service));
        }
    }

    @Test
    @Timeout(60)
    public void anUntickedRevisionInTheRemotesFeatureTreeIsNotPulled(@TempDir Path tmp) throws Exception {
        Path origin = origin(tmp);
        String b = revisionString(origin, "B");
        try (EccoService service = target(tmp, origin)) {
            // the tree shows the remote's features as last fetched
            service.fetch("origin");
            PullView view = show(service);
            onFx(() -> {
                chooseRemote(view, "origin");
                button(view, "Select >").fire();
            });
            assertEquals("Selection", onFx(() -> view.headerLabel.getText()));
            assertEquals(Set.of("A", "B"), onFx(() -> treeFeatures(view)));
            assertTrue(onFx(() -> deselectionField(view).getText().isEmpty()), "everything is ticked to begin with");

            onFx(() -> untick(view, "B"));
            assertEquals(b, onFx(() -> deselectionField(view).getText()));
            onFx(() -> untick(view, "A"));
            assertEquals(Set.of(b, revisionString(origin, "A")), onFx(() -> Set.of(deselectionField(view).getText().split(", "))));
            onFx(() -> tick(view, "A"));
            assertEquals(b, onFx(() -> deselectionField(view).getText()), "ticked again: pulled again");

            onFx(() -> button(view, "Pull |").fire());
            waitUntil("the pull succeeded (the dialog closed)", () -> !stage.isShowing());
            assertEquals(Set.of("A"), features(service));
        }
    }

    @Test
    @Timeout(60)
    public void nothingCanBePulledBeforeARemoteIsChosen(@TempDir Path tmp) throws Exception {
        Path origin = origin(tmp);
        try (EccoService service = target(tmp, origin)) {
            service.addRemote("other", tmp.resolve("other").toString(), Remote.Type.LOCAL);
            PullView view = show(service);
            ComboBox<Remote> remotes = onFx(() -> remoteBox(view));
            assertEquals(List.of("-", "origin", "other"), onFx(() -> sortedNames(remotes)));
            assertTrue(onFx(() -> button(view, "Pull |").isDisable() && button(view, "Select >").isDisable()));

            onFx(() -> chooseRemote(view, "origin"));
            assertFalse(onFx(() -> button(view, "Pull |").isDisable() || button(view, "Select >").isDisable()));

            // back from the selection step: the same remotes, the chosen one still chosen
            onFx(() -> button(view, "Select >").fire());
            onFx(() -> button(view, "< Remote").fire());
            assertEquals(List.of("-", "origin", "other"), onFx(() -> sortedNames(remotes)));
            assertEquals("origin", onFx(() -> remotes.getValue().getName()));
        }
    }

    @Test
    @Timeout(60)
    public void anExclusionNamingNoFeatureRevisionIsReportedAndNothingIsPulled(@TempDir Path tmp) throws Exception {
        Path origin = origin(tmp);
        try (EccoService service = target(tmp, origin)) {
            PullView view = show(service);
            onFx(() -> {
                chooseRemote(view, "origin");
                button(view, "Select >").fire();
                deselectionField(view).setText("Nope.1");
                button(view, "Pull |").fire();
            });
            waitUntil("the error", () -> errorText(view) != null);

            assertTrue(onFx(() -> errorText(view)).startsWith("Error during pull operation."));
            // the cause itself ("Feature with name does not exist") is lost: RemoteSyncService.pull rolls
            // back the remote's transaction a second time, and that error replaces it
            assertTrue(onFx(() -> errorText(view)).contains("Error during pull."), onFx(() -> errorText(view)));
            assertTrue(onFx(stage::isShowing), "the dialog stays open with the error");
            assertEquals(Set.of(), features(service));
        }
    }

    @Test
    @Timeout(60)
    public void anUnknownRemoteIsReported(@TempDir Path tmp) throws Exception {
        Path origin = origin(tmp);
        try (EccoService service = target(tmp, origin)) {
            PullView view = show(service);
            // removed while the dialog is open
            service.removeRemote("origin");
            onFx(() -> {
                chooseRemote(view, "origin");
                button(view, "Pull |").fire();
            });
            waitUntil("the error", () -> errorText(view) != null);

            assertTrue(onFx(() -> errorText(view)).contains("Remote 'origin' does not exist."), onFx(() -> errorText(view)));
            assertEquals(Set.of(), features(service));
        }
    }

    // --- helpers -------------------------------------------------------------------------------------

    /** A repository with "A" (a.txt) and "A, B" (a.txt, b.txt), closed again. */
    static Path origin(Path tmp) throws Exception {
        Path dir = tmp.resolve("origin");
        try (EccoService service = repository(dir)) {
            commit(service, tmp.resolve("o1"), "A", Map.of("a.txt", "a\n"));
            commit(service, tmp.resolve("o2"), "A, B", Map.of("a.txt", "a\n", "b.txt", "b\n"));
        }
        return dir;
    }

    /** An empty repository with {@code origin} as its LOCAL remote "origin". */
    private static EccoService target(Path tmp, Path origin) throws Exception {
        EccoService service = repository(tmp.resolve("target"));
        service.addRemote("origin", origin.toString(), Remote.Type.LOCAL);
        return service;
    }

    /** As the feature tree writes it: the feature name and the (truncated) revision id. */
    static String revisionString(Path repository, String feature) {
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repository.resolve(".ecco"));
            service.open();
            return service.getRepository().getFeatures().stream().filter(f -> f.getName().equals(feature))
                    .findFirst().orElseThrow().getLatestRevision().toString();
        }
    }

    static Set<String> features(EccoService service) {
        return service.getRepository().getFeatures().stream().map(Feature::getName).collect(Collectors.toSet());
    }

    /** In an off-screen window: the dialog closes it when the pull succeeded. */
    private PullView show(EccoService service) throws Exception {
        return onFx(() -> {
            PullView view = new PullView(service);
            stage = new Stage();
            stage.setScene(new Scene(view));
            stage.setX(-10_000);
            stage.setY(-10_000);
            stage.show();
            return view;
        });
    }

    @SuppressWarnings("unchecked")
    static ComboBox<Remote> remoteBox(OperationView view) {
        return (ComboBox<Remote>) find(view, n -> n instanceof ComboBox);
    }

    static void chooseRemote(OperationView view, String name) {
        ComboBox<Remote> box = remoteBox(view);
        box.getSelectionModel().select(box.getItems().stream().filter(r -> r != null && r.getName().equals(name)).findFirst().orElseThrow());
    }

    static List<String> names(ComboBox<Remote> box) {
        return box.getItems().stream().map(r -> r == null ? "-" : r.getName()).toList();
    }

    /** the empty choice first, the remotes in any order */
    static List<String> sortedNames(ComboBox<Remote> box) {
        List<String> names = new ArrayList<>(names(box));
        names.subList(1, names.size()).sort(null);
        return names;
    }

    static TextField deselectionField(OperationView view) {
        return (TextField) find(view.getCenter(), n -> n instanceof TextField);
    }

    @SuppressWarnings("unchecked")
    private static TreeView<PullView.FeatureInfo> tree(PullView view) {
        return (TreeView<PullView.FeatureInfo>) find(view.getCenter(), n -> n instanceof TreeView);
    }

    private static Set<String> treeFeatures(PullView view) {
        return tree(view).getRoot().getChildren().stream().map(i -> i.getValue().getFeature().getName()).collect(Collectors.toSet());
    }

    private static CheckBoxTreeItem<PullView.FeatureInfo> revisionItem(PullView view, String feature) {
        TreeItem<PullView.FeatureInfo> featureItem = tree(view).getRoot().getChildren().stream()
                .filter(i -> i.getValue().getFeature().getName().equals(feature)).findFirst().orElseThrow();
        return (CheckBoxTreeItem<PullView.FeatureInfo>) featureItem.getChildren().get(0);
    }

    private static void untick(PullView view, String feature) {
        revisionItem(view, feature).setSelected(false);
    }

    private static void tick(PullView view, String feature) {
        revisionItem(view, feature).setSelected(true);
    }

    /** The error step's message and exception text, null while no error is shown. */
    static String errorText(OperationView view) {
        ExceptionTextArea exception = (ExceptionTextArea) find(view.getCenter(), n -> n instanceof ExceptionTextArea);
        if (exception == null)
            return null;
        List<String> parts = new ArrayList<>();
        Label label = (Label) find(view.getCenter(), n -> n instanceof Label);
        parts.add(label.getText());
        parts.add(exception.getText());
        assertTrue(view.rightButtons.getChildren().stream().anyMatch(n -> n instanceof Button b && b.getText().equals("Done")));
        return String.join("\n", parts);
    }
}
