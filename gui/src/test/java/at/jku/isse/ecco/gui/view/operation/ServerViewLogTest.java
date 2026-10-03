package at.jku.isse.ecco.gui.view.operation;

import at.jku.isse.ecco.service.EccoService;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TitledPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The server log showed rows but no text: the message column always rendered "" and the time column
 * had no value at all.
 */
public class ServerViewLogTest {

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

    @Test
    @Timeout(30)
    public void logShowsTimeAndMessageOfEachServerEvent() throws Exception {
        EccoService service = new EccoService();
        AtomicReference<ServerView> view = new AtomicReference<>();
        onFxThread(() -> {
            ServerView serverView = new ServerView(service);
            new Scene(serverView);
            view.set(serverView);
        });

        // what the service fires, from its server thread
        view.get().serverStartEvent(service, 3770);
        view.get().serverEvent(service, "New connection from /127.0.0.1 with command 'FETCH'.");

        AtomicReference<List<String>> cells = new AtomicReference<>();
        onFxThread(() -> {
            @SuppressWarnings("unchecked")
            TableView<ServerView.LogEntry> table = (TableView<ServerView.LogEntry>) findTable(view.get());
            assertNotNull(table, "the running step shows the log table");
            ServerView.LogEntry row = table.getItems().get(0);
            List<TableColumn<ServerView.LogEntry, ?>> columns = table.getColumns();
            cells.set(columns.stream().map(column -> String.valueOf(column.getCellData(row))).toList());
        });

        assertEquals(2, cells.get().size());
        assertTrue(cells.get().get(0).matches("\\d\\d:\\d\\d:\\d\\d"), cells.get().get(0));
        assertEquals("New connection from /127.0.0.1 with command 'FETCH'.", cells.get().get(1));
    }

    // the table sits in a TitledPane, whose content only becomes a child once a skin exists
    private static TableView<?> findTable(Node node) {
        if (node instanceof TableView<?> table)
            return table;
        if (node instanceof TitledPane pane)
            return findTable(pane.getContent());
        if (node instanceof Parent parent)
            for (Node child : parent.getChildrenUnmodifiable()) {
                TableView<?> table = findTable(child);
                if (table != null)
                    return table;
            }
        return null;
    }

    // runs the action and then waits for everything queued before it (e.g. the views' runLater calls)
    private static void onFxThread(Runnable action) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.countDown();
            }
        });
        assertTrue(done.await(10, TimeUnit.SECONDS));
        if (failure.get() instanceof Error error)
            throw error;
        if (failure.get() != null)
            throw new Exception(failure.get());
    }
}
