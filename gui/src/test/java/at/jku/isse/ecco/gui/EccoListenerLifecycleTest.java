package at.jku.isse.ecco.gui;

import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.service.listener.EccoListener;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Views opened in their own window (server log, commit comparison) registered as service listeners
 * and never unregistered, leaking one listener per opened window. See EccoListenerLifecycle.
 */
public class EccoListenerLifecycleTest {

    @BeforeAll
    public static void startToolkit() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyStarted) {
            latch.countDown();
        }
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        // closing the only window below would otherwise shut the FX thread down for every later
        // test class in this JVM (their Platform.runLater calls would never run)
        Platform.setImplicitExit(false);
    }

    @Test
    @Timeout(30)
    public void listenersAreRemovedWhenTheViewsWindowIsHidden() throws Exception {
        List<EccoListener> removed = new ArrayList<>();
        EccoService service = new EccoService() {
            @Override
            public void removeListener(EccoListener listener) {
                removed.add(listener);
                super.removeListener(listener);
            }
        };
        EccoListener first = new EccoListener() {};
        EccoListener second = new EccoListener() {};
        service.addListener(first);
        service.addListener(second);

        CountDownLatch done = new CountDownLatch(1);
        List<Integer> removedWhileShowing = new ArrayList<>();
        Platform.runLater(() -> {
            Pane view = new Pane();
            EccoListenerLifecycle.removeWhenWindowHidden(view, service, first, second);
            Stage stage = new Stage();
            stage.setScene(new Scene(view)); // the scene gets its window after registration
            stage.show();
            removedWhileShowing.add(removed.size());
            stage.hide();
            done.countDown();
        });
        assertTrue(done.await(10, TimeUnit.SECONDS));

        assertEquals(0, removedWhileShowing.get(0), "nothing may be removed while the window is showing");
        assertEquals(List.of(first, second), removed, "both listeners must be removed once the window is hidden");
    }
}
