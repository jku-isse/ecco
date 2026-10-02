package at.jku.isse.ecco.gui.view.operation;

import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.service.RecentRepositories;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fork had no place in the GUI: ForkView existed but was never shown, and it forked by init + pull
 * rather than through EccoService.fork(). It is now on the ribbon and calls fork() like the command
 * line. A fork is only trusted once it has been reopened and checked out (see the fork-reopen bugs).
 */
public class ForkViewTest {

    private static final String RECENT_REPOS_KEY = "recentRepositoryDirs";
    private String originalRecent;

    @BeforeAll
    public static void startToolkit() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyStarted) {
            latch.countDown();
        }
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        Platform.setImplicitExit(false);
    }

    // a successful fork adds the new repository to the user's recent repositories
    @BeforeEach
    public void saveRecentRepositories() {
        originalRecent = recentPrefs().get(RECENT_REPOS_KEY, null);
    }

    @AfterEach
    public void restoreRecentRepositories() throws Exception {
        if (originalRecent == null)
            recentPrefs().remove(RECENT_REPOS_KEY);
        else
            recentPrefs().put(RECENT_REPOS_KEY, originalRecent);
        recentPrefs().flush();
    }

    @Test
    @Timeout(120)
    public void forksALocalRepositoryWithoutTheExcludedFeatureAndTheForkReopens() throws Exception {
        Path tmp = Files.createTempDirectory("fork-view");
        Path origin = Files.createDirectories(tmp.resolve("origin"));
        String excludedRevision;
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(origin.resolve(".ecco"));
            service.init();
            service.setBaseDir(origin);
            Files.writeString(origin.resolve("a.txt"), "a\n");
            service.commit("a", "A");
            Files.writeString(origin.resolve("b.txt"), "b\n");
            service.commit("a and b", "A, B");
            Feature b = service.getRepository().getFeatures().stream().filter(f -> f.getName().equals("B")).findFirst().orElseThrow();
            excludedRevision = b.getLatestRevision().toString();
        }

        // an existing, empty directory: for a missing one the view first asks to create it
        Path forkDir = Files.createDirectories(tmp.resolve("fork"));
        EccoService service = new EccoService();
        try {
            AtomicReference<Stage> stage = new AtomicReference<>();
            onFxThread(() -> {
                ForkView view = new ForkView(service);
                Stage s = new Stage();
                s.setScene(new Scene(view));
                // shown off screen: the view closes its window when the fork succeeded
                s.setX(-10_000);
                s.setY(-10_000);
                s.show();
                stage.set(s);

                List<TextField> fields = ((GridPane) view.getCenter()).getChildren().stream()
                        .filter(n -> n instanceof TextField).map(n -> (TextField) n).collect(Collectors.toList());
                assertEquals(3, fields.size());
                fields.get(0).setText(origin.toString());
                fields.get(1).setText(forkDir.resolve(".ecco").toString());
                fields.get(2).setText(excludedRevision);

                Button forkButton = (Button) view.rightButtons.getChildren().stream()
                        .filter(n -> n instanceof Button && ((Button) n).getText().equals("Fork")).findFirst().orElseThrow();
                forkButton.fire();
            });

            long deadline = System.currentTimeMillis() + 60_000;
            AtomicReference<Boolean> showing = new AtomicReference<>(true);
            while (showing.get() && System.currentTimeMillis() < deadline) {
                Thread.sleep(100);
                onFxThread(() -> showing.set(stage.get().isShowing()));
            }
            assertFalse(showing.get(), "the fork did not succeed (the dialog is still open)");
            assertTrue(service.isInitialized());
            assertEquals(forkDir.resolve(".ecco"), service.getRepositoryDir());
            assertNotNull(service.getRemote(EccoService.ORIGIN_REMOTE_NAME));
        } finally {
            service.close();
        }

        try (EccoService reopened = new EccoService()) {
            reopened.setRepositoryDir(forkDir.resolve(".ecco"));
            reopened.open();
            Set<String> features = reopened.getRepository().getFeatures().stream().map(Feature::getName).collect(Collectors.toSet());
            assertEquals(Set.of("A"), features);

            Path checkoutDir = Files.createDirectories(tmp.resolve("checkout"));
            reopened.setBaseDir(checkoutDir);
            reopened.checkout("A");
            assertEquals("a\n", Files.readString(checkoutDir.resolve("a.txt")));
            assertFalse(Files.exists(checkoutDir.resolve("b.txt")));
        }
    }

    private static void onFxThread(Runnable runnable) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                runnable.run();
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.countDown();
            }
        });
        assertTrue(done.await(30, TimeUnit.SECONDS));
        if (failure.get() != null)
            throw new AssertionError(failure.get());
    }

    private static Preferences recentPrefs() {
        return Preferences.userNodeForPackage(RecentRepositories.class);
    }
}
