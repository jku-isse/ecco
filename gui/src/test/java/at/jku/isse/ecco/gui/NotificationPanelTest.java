package at.jku.isse.ecco.gui;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;

import static at.jku.isse.ecco.gui.FxTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The notification overlay: each message becomes a card styled by its type, a card goes away when
 * closed or by itself after a few seconds, and {@link Notifications} reaches the installed panel
 * from any thread (and does nothing without one).
 */
public class NotificationPanelTest {

    @BeforeAll
    public static void start() throws InterruptedException {
        startToolkit();
    }

    private static List<String> messages(NotificationPanel panel) {
        return panel.getChildren().stream()
                .map(card -> ((Label) ((HBox) card).getChildren().get(0)).getText()).toList();
    }

    /** Each shown message is a card with the type's style class; the overlay itself lets clicks through. */
    @Test
    @Timeout(30)
    public void eachMessageIsACardStyledByItsType() throws Exception {
        onFx(() -> {
            NotificationPanel panel = new NotificationPanel();
            assertTrue(panel.isMouseTransparent());
            panel.show(NotificationPanel.Type.SUCCESS, "saved");
            panel.show(NotificationPanel.Type.ERROR, "failed");
            panel.show(NotificationPanel.Type.INFO, "fyi");
            assertEquals(List.of("saved", "failed", "fyi"), messages(panel));
            assertTrue(panel.getChildren().get(0).getStyleClass().containsAll(List.of("notification-card", "notification-success")));
            assertTrue(panel.getChildren().get(1).getStyleClass().contains("notification-error"));
            assertTrue(panel.getChildren().get(2).getStyleClass().contains("notification-info"));
            assertFalse(panel.getChildren().get(0).isMouseTransparent(), "a card can be clicked");
        });
    }

    /** The close button removes only its own card (after a short fade). */
    @Test
    @Timeout(30)
    public void closingACardRemovesOnlyThatCard() throws Exception {
        NotificationPanel panel = onFx(() -> {
            NotificationPanel p = new NotificationPanel();
            p.show(NotificationPanel.Type.INFO, "first");
            p.show(NotificationPanel.Type.INFO, "second");
            return p;
        });
        onFx(() -> ((Button) ((HBox) panel.getChildren().get(0)).getChildren().get(1)).fire());
        waitUntil("the closed card is gone", () -> panel.getChildren().size() == 1);
        assertEquals(List.of("second"), onFx(() -> messages(panel)));
    }

    /** A card dismisses itself after it has been visible for a few seconds. */
    @Test
    @Timeout(30)
    public void aCardGoesAwayByItself() throws Exception {
        NotificationPanel panel = onFx(() -> {
            NotificationPanel p = new NotificationPanel();
            p.show(NotificationPanel.Type.SUCCESS, "done");
            return p;
        });
        assertEquals(1, onFx(() -> panel.getChildren().size()));
        waitUntil("the card dismissed itself", () -> panel.getChildren().isEmpty());
    }

    /** Notifications posts to the installed panel from a background thread, and is a no-op with none installed. */
    @Test
    @Timeout(30)
    public void notificationsReachTheInstalledPanelFromAnyThread() throws Exception {
        NotificationPanel panel = onFx(NotificationPanel::new);
        try {
            Notifications.install(null);
            Notifications.error("nobody listens");
            Notifications.install(panel);
            Notifications.success("from a background thread");
            Notifications.info("another");
            waitUntil("both messages", () -> panel.getChildren().size() == 2);
            assertEquals(List.of("from a background thread", "another"), onFx(() -> messages(panel)));
            onFx(() -> Notifications.error("on the FX thread"));
            assertTrue(onFx(() -> messages(panel).contains("on the FX thread")));
            assertTrue(onFx(() -> panel.getChildren().get(2).getStyleClass().contains("notification-error")));
        } finally {
            Notifications.install(null);
        }
    }
}
