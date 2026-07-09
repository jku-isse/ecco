package at.jku.isse.ecco.gui;

import at.jku.isse.ecco.service.EccoService;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.stage.Window;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies Cmd+O (Ctrl+O off macOS - KeyCombination.SHORTCUT_DOWN is platform-aware) opens the same
 * "Open" dialog as clicking the Open button, and that the shortcut respects the button's disabled
 * state rather than bypassing it.
 */
public class MainViewOpenShortcutTest {

	// the JavaFX toolkit can only be started once per JVM - Platform.startup() throws
	// IllegalStateException on a second call, which multiple @Test methods in one class would
	// otherwise hit
	private static final AtomicBoolean TOOLKIT_STARTED = new AtomicBoolean(false);

	private void runOnFxThread(FxAction action) throws Exception {
		final Exception[] failure = {null};
		CountDownLatch latch = new CountDownLatch(1);
		Runnable task = () -> {
			try {
				action.run();
			} catch (Exception e) {
				failure[0] = e;
			} finally {
				latch.countDown();
			}
		};
		if (TOOLKIT_STARTED.compareAndSet(false, true)) {
			Platform.startup(task);
		} else {
			Platform.runLater(task);
		}
		latch.await();
		if (failure[0] != null) throw failure[0];
	}

	private interface FxAction {
		void run() throws Exception;
	}

	@Test
	public void cmdOOpensDialog_whenOpenButtonEnabled() throws Exception {
		runOnFxThread(() -> {
			EccoService service = new EccoService();
			MainView mainView = new MainView(service); // not initialized -> Open button enabled
			Scene scene = new Scene(mainView);

			int windowCountBefore = Window.getWindows().size();

			Runnable accelerator = scene.getAccelerators().get(
					new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN));
			assertTrue(accelerator != null, "expected Cmd/Ctrl+O to be registered as a Scene accelerator");
			accelerator.run();

			assertEquals(windowCountBefore + 1, Window.getWindows().size(),
					"expected the shortcut to open exactly one new (dialog) window");
		});
	}

	@Test
	public void cmdODoesNothing_whenOpenButtonDisabled() throws Exception {
		runOnFxThread(() -> {
			EccoService service = new EccoService();
			// a real, already-initialized repository - matches how MainView's own
			// statusChangedEvent listener disables the Open button once isInitialized() is true
			service.setRepositoryDir(Files.createTempDirectory("open-shortcut-repo").resolve(".ecco"));
			service.init();

			MainView mainView = new MainView(service);
			Scene scene = new Scene(mainView);

			int windowCountBefore = Window.getWindows().size();

			Runnable accelerator = scene.getAccelerators().get(
					new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN));
			assertTrue(accelerator != null, "expected Cmd/Ctrl+O to be registered as a Scene accelerator");
			accelerator.run();

			assertEquals(windowCountBefore, Window.getWindows().size(),
					"expected the shortcut to be a no-op while the Open button is disabled (a repository is already open)");

			service.close();
		});
	}
}
