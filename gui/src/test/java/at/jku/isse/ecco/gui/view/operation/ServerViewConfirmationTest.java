package at.jku.isse.ecco.gui.view.operation;

import at.jku.isse.ecco.service.EccoService;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ToolBar;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Synchronization has no authentication, so the server listens on other machines' connections only
 * after the user said yes to a warning (NFR-S3): with "Accept connections from other machines"
 * ticked, Start asks first, and a no starts nothing. (A yes is not tried here: it would open a port
 * on every interface of the machine running the tests.)
 */
public class ServerViewConfirmationTest {

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
    public void acceptingOtherMachinesIsAskedAboutAndANoStartsNothing() throws Exception {
        Path workDir = Files.createTempDirectory("server-view-confirmation");
        EccoService service = new EccoService();
        service.setRepositoryDir(workDir.resolve(".ecco"));
        service.init();
        try {
            AtomicReference<ScriptedServerView> view = new AtomicReference<>();
            onFxThread(() -> {
                ScriptedServerView serverView = new ScriptedServerView(service);
                new Scene(serverView);
                view.set(serverView);
            });

            onFxThread(() -> {
                ((CheckBox) find(view.get(), n -> n instanceof CheckBox box && box.getText().equals("Accept connections from other machines"))).setSelected(true);
                ((Button) find(view.get(), n -> n instanceof Button button && button.getText().equals("Start"))).fire();
            });

            assertEquals(1, view.get().asked.size(), "asked once, with the port");
            Thread.sleep(500); // a server task, had one started, would be running by now
            assertFalse(service.serverRunning(), "a no starts no server");
            assertEquals(-1, service.serverPort());
        } finally {
            if (service.serverRunning())
                service.stopServer();
            service.close();
        }
    }

    private static final class ScriptedServerView extends ServerView {
        final List<Integer> asked = new ArrayList<>();

        ScriptedServerView(EccoService service) {
            super(service);
        }

        @Override
        boolean confirmAllInterfaces(int port) {
            this.asked.add(port);
            return false;
        }
    }

    // a ToolBar's items only become its children once a skin exists
    private static Node find(Node node, Predicate<Node> matches) {
        if (matches.test(node))
            return node;
        if (node instanceof ToolBar toolBar)
            for (Node item : toolBar.getItems()) {
                Node found = find(item, matches);
                if (found != null)
                    return found;
            }
        if (node instanceof Parent parent)
            for (Node child : parent.getChildrenUnmodifiable()) {
                Node found = find(child, matches);
                if (found != null)
                    return found;
            }
        return null;
    }

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
