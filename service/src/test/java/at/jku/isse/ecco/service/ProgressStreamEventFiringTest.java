package at.jku.isse.ecco.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ProgressInputStream/ProgressOutputStream.fireProgressEvent() used to guard on
 * "this.lastProgress - progress >= 1.0" -- since progress only increases and lastProgress is always
 * <= it, that subtraction is reversed and the condition can never be true, so registered
 * ProgressListeners never fired during a real remote sync (RemoteSyncService.fetch()/pull()/push()
 * wire these up with the real payload size and EccoService itself as the listener, feeding the GUI's
 * progress bar). Fixed to progress - this.lastProgress >= PROGRESS_EVENT_THRESHOLD.
 */
public class ProgressStreamEventFiringTest {

	@Test
	@Timeout(30)
	public void progressInputStreamFiresListenersWhileReading() throws IOException {
		byte[] data = new byte[10_000];
		List<Double> observedProgress = new ArrayList<>();

		try (ProgressInputStream in = new ProgressInputStream(new ByteArrayInputStream(data), data.length)) {
			in.addListener((progress, bytes) -> observedProgress.add(progress));

			byte[] buffer = new byte[100];
			while (in.read(buffer) != -1) {
				// drain in small chunks so progress advances gradually, exercising the threshold check
			}
		}

		assertFalse(observedProgress.isEmpty(), "at least one progress event must fire while reading");
		assertTrue(observedProgress.get(observedProgress.size() - 1) >= 0.9,
				"the last reported progress should be near completion, got: " + observedProgress);
	}

	@Test
	@Timeout(30)
	public void progressOutputStreamFiresListenersWhileWriting() throws IOException {
		byte[] data = new byte[10_000];
		List<Double> observedProgress = new ArrayList<>();

		try (ProgressOutputStream out = new ProgressOutputStream(new ByteArrayOutputStream(), data.length)) {
			out.addListener((progress, bytes) -> observedProgress.add(progress));

			byte[] buffer = new byte[100];
			for (int offset = 0; offset < data.length; offset += buffer.length) {
				out.write(buffer);
			}
		}

		assertFalse(observedProgress.isEmpty(), "at least one progress event must fire while writing");
		assertTrue(observedProgress.get(observedProgress.size() - 1) >= 0.9,
				"the last reported progress should be near completion, got: " + observedProgress);
	}
}
