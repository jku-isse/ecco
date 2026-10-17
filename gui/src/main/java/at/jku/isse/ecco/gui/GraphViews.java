package at.jku.isse.ecco.gui;

import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.stage.FileChooser;
import org.graphstream.graph.Graph;
import org.graphstream.stream.file.FileSink;
import org.graphstream.stream.file.FileSinkFactory;
import org.graphstream.ui.fx_viewer.FxViewPanel;
import org.graphstream.ui.fx_viewer.FxViewer;

import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * What the GraphStream views - Feature Model, Knowledge, Artifact and Dependency Graph - share: the
 * Export button, closing a viewer, zooming with the mouse wheel and a pinch. Each had a copy of its own.
 */
public final class GraphViews {

	/** How much of the view one scroll notch zooms. */
	private static final double SCROLL_ZOOM_STEP = 0.05;

	private GraphViews() {
	}

	/**
	 * An Export button: saves [graph] in a format GraphStream writes, chosen by the file's extension -
	 * GraphML, GEXF, DOT or DGS; another extension is refused. [busy] is told while the save dialog is
	 * open (e.g. to disable the toolbar), may be null.
	 */
	public static Button exportButton(Supplier<Graph> graph, String initialFileName, Consumer<Boolean> busy) {
		Button button = new Button("Export");
		button.setOnAction(event -> {
			if (busy != null)
				busy.accept(true);
			try {
				FileChooser fileChooser = new FileChooser();
				// FileSinkFactory.sinkFor() picks a writer by the extension alone, and has none for
				// others: the formats GraphStream writes, so the save dialog does not offer another
				fileChooser.getExtensionFilters().addAll(
						new FileChooser.ExtensionFilter("GraphML (*.graphml)", "*.graphml"),
						new FileChooser.ExtensionFilter("GEXF (*.gexf)", "*.gexf"),
						new FileChooser.ExtensionFilter("DOT (*.dot)", "*.dot"),
						new FileChooser.ExtensionFilter("DGS (*.dgs)", "*.dgs"));
				fileChooser.setInitialFileName(initialFileName);
				File selectedFile = fileChooser.showSaveDialog(button.getScene() == null ? null : button.getScene().getWindow());
				if (selectedFile == null)
					return;
				FileSink out = FileSinkFactory.sinkFor(selectedFile.toString());
				if (out == null) {
					Alert alert = new OwnedAlert(Alert.AlertType.ERROR);
					alert.setHeaderText("Unknown file extension.");
					alert.setContentText("Save as .graphml, .gexf, .dot or .dgs.");
					alert.show();
					return;
				}
				try {
					out.writeAll(graph.get(), selectedFile.toString());
					out.flush();
				} catch (IOException e) {
					new ExceptionAlert(e).show();
				}
			} finally {
				if (busy != null)
					busy.accept(false);
			}
		});
		return button;
	}

	/**
	 * Closes [viewer], a viewer of [graph], and stops it listening: its pipe stayed registered with the
	 * graph and queued every later change of it - a viewer made for each commit piled them up.
	 * GraphStream's renderer can throw closing a view never rendered (e.g. closed again at once by
	 * back-to-back status events) - harmless, it is discarded anyway.
	 */
	public static void closeViewer(FxViewer viewer, Graph graph) {
		try {
			viewer.close();
		} catch (Exception ignored) {
			// see above
		}
		graph.clearSinks();
	}

	/**
	 * Zooms [view]'s camera with the mouse wheel over [target], between [min] and [max] of the view;
	 * [after] runs after each step (may be null).
	 */
	public static void zoomOnScroll(Node target, Supplier<FxViewPanel> view, double min, double max, Runnable after) {
		target.setOnScroll(event -> {
			FxViewPanel panel = view.get();
			if (panel != null) {
				panel.getCamera().setViewPercent(Math.max(min, Math.min(max,
						panel.getCamera().getViewPercent() - SCROLL_ZOOM_STEP * event.getDeltaY() / event.getMultiplierY())));
				if (after != null)
					after.run();
			}
		});
	}

	/** As {@link #zoomOnScroll}, with a trackpad's pinch. */
	public static void zoomOnPinch(Node target, Supplier<FxViewPanel> view, double min, double max, Runnable after) {
		target.setOnZoom(event -> {
			FxViewPanel panel = view.get();
			if (panel != null) {
				panel.getCamera().setViewPercent(Math.max(min, Math.min(max, panel.getCamera().getViewPercent() / event.getZoomFactor())));
				if (after != null)
					after.run();
			}
		});
	}
}
