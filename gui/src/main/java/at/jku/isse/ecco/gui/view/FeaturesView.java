package at.jku.isse.ecco.gui.view;

import at.jku.isse.ecco.gui.CategoricalColorPalette;
import at.jku.isse.ecco.gui.ExceptionAlert;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.service.listener.EccoListener;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Separator;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import org.graphstream.graph.Graph;
import org.graphstream.graph.Node;
import org.graphstream.graph.implementations.SingleGraph;
import org.graphstream.stream.file.FileSink;
import org.graphstream.stream.file.FileSinkFactory;
import org.graphstream.ui.fx_viewer.FxViewPanel;
import org.graphstream.ui.fx_viewer.FxViewer;
import org.graphstream.ui.javafx.FxGraphRenderer;
import org.graphstream.ui.view.Viewer;

import javax.swing.SwingUtilities;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A feature model tree (as known from software product lines), rather than a plain sortable table:
 * each feature's vertical position reflects which commit first introduced it (earlier on top), and
 * its parent is the earlier-introduced feature it most often co-occurs with in an association's
 * presence condition - so features naturally attach under the base feature(s) they were built on
 * top of. See {@link FeatureModelTree} for the actual algorithm (kept separate from this class so
 * it has no JavaFX/GraphStream dependency and can be tested on its own).
 */
public class FeaturesView extends BorderPane implements EccoListener {

	private final EccoService service;

	private final Graph graph;
	private FxViewer viewer;
	private FxViewPanel view;

	private final ToolBar toolBar;

	/** See {@link at.jku.isse.ecco.gui.view.graph.ArtifactGraphView#tabVisible} for the full rationale. */
	private volatile boolean tabVisible = true;

	private boolean showLabels = true;

	public FeaturesView(EccoService service) {
		this.service = service;

		this.toolBar = new ToolBar();
		this.setTop(toolBar);

		Button exportButton = new Button("Export");
		exportButton.setOnAction(ae -> {
			toolBar.setDisable(true);

			FileChooser fileChooser = new FileChooser();
			File selectedFile = fileChooser.showSaveDialog(FeaturesView.this.getScene().getWindow());

			if (selectedFile != null) {
				FileSink out = FileSinkFactory.sinkFor(selectedFile.toString());
				if (out != null) {
					try {
						out.writeAll(FeaturesView.this.graph, selectedFile.toString());
						out.flush();
					} catch (IOException e) {
						new ExceptionAlert(e).show();
					}
				} else {
					Alert alert = new Alert(Alert.AlertType.ERROR);
					alert.setHeaderText("Unknown file extension.");
					alert.setContentText("Unknown file extension.");
					alert.show();
				}
			}

			toolBar.setDisable(false);
		});

		CheckBox showLabelsCheckbox = new CheckBox("Show Labels");
		showLabelsCheckbox.selectedProperty().addListener((ov, oldVal, newVal) -> {
			FeaturesView.this.showLabels = newVal;
			FeaturesView.this.updateGraphStylesheet();
		});

		toolBar.getItems().setAll(exportButton, new Separator(), showLabelsCheckbox, new Separator());


		System.setProperty("org.graphstream.ui.renderer", "org.graphstream.ui.j2dviewer.J2DGraphRenderer");


		this.graph = new SingleGraph("FeatureModel");
		// deliberately no layout sink/enableAutoLayout() anywhere in this class: node positions come
		// straight from FeatureModelTree.compute() so "earlier features on top" is a rendering
		// guarantee, not an emergent property of a force simulation - unlike the other graph tabs,
		// this one is a static tree, not a physics-driven graph.

		this.setOnScroll(event -> {
			if (null != view) {
				view.getCamera().setViewPercent(Math.max(0.1, Math.min(1.0,
						view.getCamera().getViewPercent() - 0.05 * event.getDeltaY() / event.getMultiplierY())));
			}
		});

		showLabelsCheckbox.setSelected(this.showLabels);

		service.addListener(this);
		Platform.runLater(() -> statusChangedEvent(service));
	}


	private void updateGraphStylesheet() {
		String textMode = this.showLabels ? "text-mode: normal; " : "text-mode: hidden; ";
		this.graph.setAttribute("ui.stylesheet",
				"edge { " + textMode + " size: 1px; shape: line; arrow-size: 6px, 4px; fill-color: #89878188; } " +
						"node { " + textMode + " text-background-mode: plain; shape: circle; size: " + NODE_SIZE + "px; stroke-mode: plain; stroke-color: #000000; stroke-width: 1px; } ");
	}

	private void initView() {
		closeView();
		viewer = new FxViewer(graph, Viewer.ThreadingModel.GRAPH_IN_ANOTHER_THREAD);
		view = (FxViewPanel) viewer.addDefaultView(false, new FxGraphRenderer());

		setCenter(view);
	}

	private void closeView() {
		if (null == viewer) {
			return;
		}

		setCenter(null);
		try {
			viewer.close();
		} catch (Exception ignored) {
			// see ArtifactGraphView.closeView() for why this is safely ignorable
		}
		view = null;
		viewer = null;
	}

	/** Builds a fresh snapshot and queues it for rendering; see {@link at.jku.isse.ecco.gui.view.graph.ArtifactGraphView#refreshGraph()}. */
	private void refreshGraph() {
		this.refreshGraph(this.buildSnapshot());
	}

	private void refreshGraph(FeatureModelSnapshot snapshot) {
		if (this.viewer == null || this.view == null) {
			return;
		}

		this.toolBar.setDisable(true);
		SwingUtilities.invokeLater(() -> {
			if (this.viewer == null || this.view == null) {
				return;
			}
			this.applySnapshot(snapshot);
			Platform.runLater(() -> this.toolBar.setDisable(false));
		});
	}

	private void applySnapshot(FeatureModelSnapshot snapshot) {
		boolean firstRender = this.graph.getNodeCount() == 0;

		// hidden for the duration of the rebuild, same flicker-avoidance reason as
		// ArtifactGraphView.applySnapshot()
		this.view.setVisible(false);
		try {
			this.graph.clear();

			this.graph.setAttribute("ui.quality");
			this.graph.setAttribute("ui.antialias");

			for (FeatureNodeSnapshot nodeSnapshot : snapshot.nodes) {
				Node graphNode = this.graph.addNode(nodeSnapshot.id);
				graphNode.setAttribute("label", nodeSnapshot.label);
				graphNode.setAttribute("xyz", nodeSnapshot.x, nodeSnapshot.y, 0.0);
				graphNode.setAttribute("ui.style", "fill-color: " + toHexColor(CategoricalColorPalette.colorForIndex(nodeSnapshot.colorIndex)) + ";");
			}
			for (FeatureEdgeSnapshot edgeSnapshot : snapshot.edges) {
				this.graph.addEdge(edgeSnapshot.id, edgeSnapshot.parentId, edgeSnapshot.childId, true);
			}

			this.updateGraphStylesheet();

			if (firstRender) {
				this.view.getCamera().resetView();
			}
		} finally {
			this.view.setVisible(true);
		}
	}

	/** "#2a78d6" - GraphStream's inline "ui.style" attributes accept CSS hex colors directly. */
	private static String toHexColor(Color color) {
		return String.format("#%02x%02x%02x",
				(int) Math.round(color.getRed() * 255),
				(int) Math.round(color.getGreen() * 255),
				(int) Math.round(color.getBlue() * 255));
	}


	private static final double X_SPACING = 80;
	private static final double Y_SPACING = 100;
	private static final int NODE_SIZE = 24;

	private static final class FeatureModelSnapshot {
		final List<FeatureNodeSnapshot> nodes = new ArrayList<>();
		final List<FeatureEdgeSnapshot> edges = new ArrayList<>();
	}

	private static final class FeatureNodeSnapshot {
		final String id;
		final String label;
		final double x;
		final double y;
		final int colorIndex;

		FeatureNodeSnapshot(String id, String label, double x, double y, int colorIndex) {
			this.id = id;
			this.label = label;
			this.x = x;
			this.y = y;
			this.colorIndex = colorIndex;
		}
	}

	private static final class FeatureEdgeSnapshot {
		final String id;
		final String parentId;
		final String childId;

		FeatureEdgeSnapshot(String parentId, String childId) {
			this.id = parentId + "->" + childId;
			this.parentId = parentId;
			this.childId = childId;
		}
	}

	/** Converts {@link FeatureModelTree#compute}'s placements into GraphStream-ready node/edge data. */
	private FeatureModelSnapshot buildSnapshot() {
		FeatureModelSnapshot snapshot = new FeatureModelSnapshot();
		for (FeatureModelTree.Placement placement : FeatureModelTree.compute(this.service.getRepository())) {
			double x = placement.x * X_SPACING;
			double y = -placement.depth * Y_SPACING;
			snapshot.nodes.add(new FeatureNodeSnapshot(placement.feature.getId(), placement.feature.getName(), x, y, placement.rootIndex));
			if (placement.parent != null) {
				snapshot.edges.add(new FeatureEdgeSnapshot(placement.parent.getId(), placement.feature.getId()));
			}
		}
		return snapshot;
	}

	/**
	 * Called by the containing tab whenever it's selected or deselected - see
	 * {@link at.jku.isse.ecco.gui.view.graph.ArtifactGraphView#setTabVisible} for the full rationale
	 * (skip snapshot work while hidden, catch up with one fresh render when shown again).
	 */
	public void setTabVisible(boolean tabVisible) {
		boolean becameVisible = tabVisible && !this.tabVisible;
		this.tabVisible = tabVisible;
		if (becameVisible && this.service.isInitialized()) {
			Platform.runLater(() -> {
				if (this.viewer == null || this.view == null) {
					initView();
				}
				this.setDisable(false);
				this.refreshGraph();
			});
		}
	}

	/**
	 * See {@link at.jku.isse.ecco.gui.view.graph.ArtifactGraphView#statusChangedEvent} - the same
	 * rationale applies here: this is what actually fires after every commit (not
	 * {@code commitsChangedEvent}, which {@link EccoService} never fires), the snapshot is built
	 * synchronously on the calling thread for point-in-time correctness across a multi-commit burst,
	 * and it's skipped entirely while the tab is hidden.
	 */
	@Override
	public void statusChangedEvent(EccoService service) {
		if (service.isInitialized()) {
			if (!this.tabVisible) {
				Platform.runLater(() -> this.setDisable(false));
				return;
			}
			FeatureModelSnapshot snapshot = this.buildSnapshot();
			Platform.runLater(() -> {
				if (this.viewer == null || this.view == null) {
					initView();
				}
				this.setDisable(false);
				this.refreshGraph(snapshot);
			});
		} else {
			Platform.runLater(() -> {
				closeView();
				this.setDisable(true);
			});
		}
	}

}
