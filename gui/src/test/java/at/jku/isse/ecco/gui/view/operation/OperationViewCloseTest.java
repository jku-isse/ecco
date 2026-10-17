package at.jku.isse.ecco.gui.view.operation;

import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static at.jku.isse.ecco.gui.FxTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Closing a dialog while its operation runs: it closed at once and the operation went on unseen -
 * an import kept importing with no window. Now closing asks first: to stop what can be stopped, or
 * whether to close although it finishes in the background. Nothing running: it closes at once.
 */
public class OperationViewCloseTest {

    @BeforeAll
    public static void start() throws InterruptedException {
        startToolkit();
    }

    /** A dialog whose answers to the close question are given in advance. */
    static final class ProbeView extends OperationView {
        final List<String> asked = new ArrayList<>();
        boolean answer;
        int closed;

        @Override
        boolean askToClose(String question, String closeLabel) {
            this.asked.add(question + " [" + closeLabel + "]");
            return this.answer;
        }

        @Override
        protected void closed() {
            this.closed++;
        }
    }

    private static Stage show(OperationView view) throws Exception {
        return onFx(() -> {
            Stage stage = new Stage();
            stage.setScene(new Scene(view));
            stage.setX(-10_000);
            stage.show();
            return stage;
        });
    }

    private static boolean requestClose(Stage stage) throws Exception {
        WindowEvent request = new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST);
        onFx(() -> stage.getOnCloseRequest().handle(request));
        return !request.isConsumed();
    }

    @Test
    @Timeout(60)
    public void nothingRunningClosesWithoutAsking() throws Exception {
        ProbeView view = onFx(ProbeView::new);
        Stage stage = show(view);
        assertTrue(requestClose(stage));
        assertEquals(List.of(), view.asked);
        onFx(stage::close);
        assertEquals(1, view.closed, "told it was closed");
    }

    @Test
    @Timeout(60)
    public void whatCanBeStoppedIsStoppedOnlyIfTheUserSaysSo() throws Exception {
        ProbeView view = onFx(ProbeView::new);
        Stage stage = show(view);
        AtomicInteger stops = new AtomicInteger();
        onFx(() -> view.running("the import", stops::incrementAndGet));

        view.answer = false;
        assertFalse(requestClose(stage), "kept open");
        assertEquals(0, stops.get());
        assertEquals(List.of("Stop the import? [Stop]"), view.asked);

        view.answer = true;
        assertTrue(requestClose(stage));
        assertEquals(1, stops.get(), "stopped");
        onFx(stage::close);
    }

    @Test
    @Timeout(60)
    public void whatCannotBeStoppedSaysItGoesOnAndEscAsksToo() throws Exception {
        ProbeView view = onFx(ProbeView::new);
        Stage stage = show(view);
        onFx(() -> view.running("The commit", null));

        view.answer = false;
        onFx(() -> view.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE, false, false, false, false)));
        assertTrue(onFx(stage::isShowing), "Esc asked, and the answer was to keep it open");
        assertEquals(List.of("The commit is still running - it finishes even if you close this window. Close anyway? [Close]"), view.asked);

        onFx(view::notRunning);
        assertTrue(requestClose(stage), "done: nothing to ask");
        assertEquals(1, view.asked.size());
        onFx(stage::close);
    }
}
