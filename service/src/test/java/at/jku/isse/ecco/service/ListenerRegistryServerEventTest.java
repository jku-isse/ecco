package at.jku.isse.ecco.service;

import at.jku.isse.ecco.service.listener.EccoListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * fireServerEvent()/fireServerStartedEvent()/fireServerStoppedEvent() used to call each listener
 * directly with no per-listener try/catch, unlike every other fire*Event method in this class -- a
 * listener throwing from one of these would abort the broadcast to the remaining listeners, and (per
 * the comment on fireStatusChangedEvent()) could be mistaken by a caller's own catch-and-rollback for
 * a real operation failure (see RemoteSyncService.startServer()'s accept loop, which calls
 * fireServerEvent() inside a try block whose catch treats any exception as "Error receiving
 * request" and rolls back). Fixed by isolating each listener call the same way the others are.
 */
public class ListenerRegistryServerEventTest {

	private static class ThrowingListener implements EccoListener {
		@Override
		public void serverEvent(EccoService service, String message) {
			throw new RuntimeException("boom");
		}

		@Override
		public void serverStartEvent(EccoService service, int port) {
			throw new RuntimeException("boom");
		}

		@Override
		public void serverStopEvent(EccoService service) {
			throw new RuntimeException("boom");
		}
	}

	private static class RecordingListener implements EccoListener {
		final AtomicBoolean sawServerEvent = new AtomicBoolean(false);
		final AtomicBoolean sawServerStartEvent = new AtomicBoolean(false);
		final AtomicBoolean sawServerStopEvent = new AtomicBoolean(false);

		@Override
		public void serverEvent(EccoService service, String message) {
			this.sawServerEvent.set(true);
		}

		@Override
		public void serverStartEvent(EccoService service, int port) {
			this.sawServerStartEvent.set(true);
		}

		@Override
		public void serverStopEvent(EccoService service) {
			this.sawServerStopEvent.set(true);
		}
	}

	@Test
	@Timeout(30)
	public void aThrowingListenerDoesNotAbortTheServerEventBroadcasts() {
		EccoService owner = new EccoService();
		ListenerRegistry registry = new ListenerRegistry(owner);
		RecordingListener recording = new RecordingListener();

		// throwing listener registered FIRST, so a bug here would prevent the recording listener
		// (registered after it) from ever being reached
		registry.addListener(new ThrowingListener());
		registry.addListener(recording);

		registry.fireServerEvent("test message");
		registry.fireServerStartedEvent(1234);
		registry.fireServerStoppedEvent();

		assertTrue(recording.sawServerEvent.get(), "fireServerEvent must still reach listeners registered after a throwing one");
		assertTrue(recording.sawServerStartEvent.get(), "fireServerStartedEvent must still reach listeners registered after a throwing one");
		assertTrue(recording.sawServerStopEvent.get(), "fireServerStoppedEvent must still reach listeners registered after a throwing one");
	}
}
