package at.jku.isse.ecco.gui.view.graph;

import javafx.application.Platform;
import org.graphstream.ui.fx_viewer.FxViewer;

/**
 * Stops a viewer's automatic (force-directed) layout after a while. The Artifact and Dependency
 * graphs ran theirs for as long as they existed, and their viewer redrew the graph on every step -
 * into a canvas whose drawing commands pile up while it is not rendered (a minimized window, another
 * view), until the heap ran out. The Feature Model and Knowledge Graph freeze theirs after 8 seconds
 * with a guard of their own; these graphs can be large, so they get longer.
 */
final class LayoutFreeze {

	/** Package-visible and not final for testing. */
	static long timeoutMs = 20_000;

	/** The latest layout started per viewer: a timer of an earlier one must not stop a later one early. Started on the FX and the Swing thread. */
	private static final java.util.Map<FxViewer, Integer> GENERATIONS = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

	private LayoutFreeze() {
	}

	/** Disables [viewer]'s automatic layout after {@link #timeoutMs} - a no-op if it was closed or disabled meanwhile. */
	static void schedule(FxViewer viewer) {
		int generation = GENERATIONS.merge(viewer, 1, Integer::sum);
		Thread thread = new Thread(() -> {
			try {
				Thread.sleep(timeoutMs);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
			Platform.runLater(() -> {
				if (GENERATIONS.getOrDefault(viewer, 0) != generation)
					return; // a later layout runs: its own timer stops it
				try {
					viewer.disableAutoLayout();
				} catch (RuntimeException closed) {
					// closed meanwhile: nothing lays out
				}
			});
		}, "layout-freeze");
		thread.setDaemon(true);
		thread.start();
	}
}
