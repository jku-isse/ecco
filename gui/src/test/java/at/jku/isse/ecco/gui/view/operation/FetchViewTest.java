package at.jku.isse.ecco.gui.view.operation;

import at.jku.isse.ecco.core.Remote;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.service.EccoService;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
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
 * What the Fetch dialog decides: which remotes it offers, that nothing is fetched before one is
 * chosen, that a fetch stores the remote's features with the remote (not in the repository), and how
 * it reports a remote that is gone. The remote is a second local repository.
 */
public class FetchViewTest {

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
    public void offersTheRepositorysRemotesAndFetchesNothingBeforeOneIsChosen(@TempDir Path tmp) throws Exception {
        Path origin = origin(tmp);
        try (EccoService service = target(tmp, origin)) {
            service.addRemote("other", tmp.resolve("other").toString(), Remote.Type.LOCAL);
            FetchView view = show(service);
            ComboBox<Remote> remotes = onFx(() -> remoteBox(view));
            assertEquals(List.of("-", "origin", "other"), onFx(() -> sortedNames(remotes)));
            assertEquals("Remote", onFx(() -> view.headerLabel.getText()));
            assertTrue(onFx(() -> button(view, "Fetch").isDisable()));

            onFx(() -> chooseRemote(view, "origin"));
            assertFalse(onFx(() -> button(view, "Fetch").isDisable()));
            onFx(() -> remotes.getSelectionModel().select(0));
            assertTrue(onFx(() -> button(view, "Fetch").isDisable()), "the empty choice again");
        }
    }

    /** The remote's features are stored with the remote; the repository itself gets nothing. */
    @Test
    @Timeout(60)
    public void fetchStoresTheRemotesFeaturesWithTheRemote(@TempDir Path tmp) throws Exception {
        Path origin = origin(tmp);
        try (EccoService service = target(tmp, origin)) {
            assertEquals(Set.of(), remoteFeatures(service, "origin"));
            FetchView view = show(service);
            onFx(() -> {
                chooseRemote(view, "origin");
                button(view, "Fetch").fire();
            });
            waitUntil("the fetch succeeded (the dialog closed)", () -> !stage.isShowing());

            assertEquals(Set.of("A", "B"), remoteFeatures(service, "origin"));
            assertEquals(Set.of(), features(service));
        }
    }

    /** The failure is reported in the dialog - as a "pull", the words FetchView took over from PullView. */
    @Test
    @Timeout(60)
    public void aRemoteRemovedMeanwhileIsReported(@TempDir Path tmp) throws Exception {
        Path origin = origin(tmp);
        try (EccoService service = target(tmp, origin)) {
            FetchView view = show(service);
            service.removeRemote("origin");
            onFx(() -> {
                chooseRemote(view, "origin");
                button(view, "Fetch").fire();
            });
            waitUntil("the error", () -> errorText(view) != null);

            String error = onFx(() -> errorText(view));
            assertTrue(error.startsWith("Error during pull operation."), error);
            assertTrue(error.contains("Remote 'origin' does not exist."), error);
            assertTrue(onFx(stage::isShowing), "the dialog stays open with the error");
            assertFalse(onFx(view::isDisable), "enabled again so Done can be clicked");
        }
    }

    @Test
    @Timeout(60)
    public void cancelClosesTheDialogWithoutFetching(@TempDir Path tmp) throws Exception {
        Path origin = origin(tmp);
        try (EccoService service = target(tmp, origin)) {
            FetchView view = show(service);
            onFx(() -> {
                chooseRemote(view, "origin");
                button(view, "Cancel").fire();
            });
            assertFalse(onFx(stage::isShowing));
            assertEquals(Set.of(), remoteFeatures(service, "origin"));
        }
    }

    // --- helpers -------------------------------------------------------------------------------------

    /** An empty repository with {@code origin} as its LOCAL remote "origin". */
    private static EccoService target(Path tmp, Path origin) throws Exception {
        EccoService service = repository(tmp.resolve("target"));
        service.addRemote("origin", origin.toString(), Remote.Type.LOCAL);
        return service;
    }

    private static Set<String> remoteFeatures(EccoService service, String remote) {
        return service.getRemote(remote).getFeatures().stream().map(Feature::getName).collect(Collectors.toSet());
    }

    /** In an off-screen window: the dialog closes it when the fetch succeeded. */
    private FetchView show(EccoService service) throws Exception {
        return onFx(() -> {
            FetchView view = new FetchView(service);
            stage = new Stage();
            stage.setScene(new Scene(view));
            stage.setX(-10_000);
            stage.setY(-10_000);
            stage.show();
            return view;
        });
    }
}
