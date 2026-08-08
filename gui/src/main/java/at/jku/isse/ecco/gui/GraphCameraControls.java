package at.jku.isse.ecco.gui;

import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import org.graphstream.ui.fx_viewer.FxViewPanel;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.function.Supplier;

/**
 * Zoom In / Zoom Out / Fit to Screen buttons for a GraphStream {@link FxViewPanel}'s camera - shared
 * by every graph tab ({@code KnowledgeGraphView}, {@code ArtifactGraphView}, {@code DependencyGraphView},
 * {@code FeaturesView}), all of which already support the same zoom via mouse scroll/trackpad pinch
 * (see each one's own {@code setOnScroll}/{@code setOnZoom}) but had no button-based (or fit-to-content)
 * equivalent.
 */
public final class GraphCameraControls {

	/** Matches the per-scroll-notch step each view's own {@code setOnScroll} handler already uses. */
	private static final double ZOOM_STEP = 0.1;
	/** Default {@code FontIcon} size (8px, see the class itself) reads as barely-there for an icon-only button with no label text next to it to help. */
	private static final int ICON_SIZE = 16;

	private GraphCameraControls() {
	}

	private static Button iconButton(org.kordamp.ikonli.Ikon icon, String tooltipText) {
		Button button = new Button(null, new FontIcon(icon));
		((FontIcon) button.getGraphic()).setIconSize(ICON_SIZE);
		button.setTooltip(new Tooltip(tooltipText));
		return button;
	}

	/**
	 * @param viewSupplier    reads {@code view} fresh on every click rather than once at construction
	 *                        time - every caller here tears down and recreates its {@code FxViewPanel}
	 *                        across the tab's open/close lifecycle (see each one's {@code initView}/
	 *                        {@code closeView}), so a button wired to a captured reference would end up
	 *                        pointed at a stale, already-closed view.
	 * @param minViewPercent  clamped range - pass whatever the caller's own scroll-zoom handler already
	 *                        clamps to, so the buttons feel like just another way to trigger the same zoom.
	 * @param afterZoom       run after every zoom-in/out/fit action; callers with camera-synced
	 *                        scrollbars (KnowledgeGraphView, FeaturesView) pass their sync method,
	 *                        everyone else an effective no-op.
	 * @return {Zoom In, Zoom Out, Fit to Screen}, in that order - insert into the caller's own toolbar
	 * wherever fits its existing layout.
	 */
	public static Button[] build(Supplier<FxViewPanel> viewSupplier, double minViewPercent, double maxViewPercent, Runnable afterZoom) {
		Button zoomInButton = iconButton(Feather.ZOOM_IN, "Zoom In");
		zoomInButton.setOnAction(e -> zoomBy(viewSupplier.get(), -ZOOM_STEP, minViewPercent, maxViewPercent, afterZoom));

		Button zoomOutButton = iconButton(Feather.ZOOM_OUT, "Zoom Out");
		zoomOutButton.setOnAction(e -> zoomBy(viewSupplier.get(), ZOOM_STEP, minViewPercent, maxViewPercent, afterZoom));

		Button fitButton = iconButton(Feather.MAXIMIZE_2, "Fit to Screen");
		fitButton.setOnAction(e -> {
			FxViewPanel view = viewSupplier.get();
			if (view == null) return;
			view.getCamera().resetView();
			afterZoom.run();
		});

		return new Button[]{zoomInButton, zoomOutButton, fitButton};
	}

	/** Smaller {@code viewPercent} = more zoomed in (showing a smaller slice of the graph), matching every existing scroll-zoom handler's own sign convention. */
	private static void zoomBy(FxViewPanel view, double delta, double minViewPercent, double maxViewPercent, Runnable afterZoom) {
		if (view == null) return;
		double newPercent = Math.max(minViewPercent, Math.min(maxViewPercent, view.getCamera().getViewPercent() + delta));
		view.getCamera().setViewPercent(newPercent);
		afterZoom.run();
	}
}
