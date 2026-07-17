package at.jku.isse.ecco.gui.view;

import at.jku.isse.ecco.core.Constraint;
import at.jku.isse.ecco.gui.CategoricalColorPalette;
import at.jku.isse.ecco.gui.ExceptionAlert;
import at.jku.isse.ecco.gui.MinimizationResults;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.service.listener.EccoListener;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.ToolBar;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import org.graphstream.graph.Graph;
import org.graphstream.graph.Node;
import org.graphstream.graph.implementations.MultiGraph;
import org.graphstream.stream.file.FileSink;
import org.graphstream.stream.file.FileSinkFactory;
import org.graphstream.ui.fx_viewer.FxViewPanel;
import org.graphstream.ui.fx_viewer.FxViewer;
import org.graphstream.ui.geom.Point3;
import org.graphstream.ui.graphicGraph.GraphicGraph;
import org.graphstream.ui.javafx.FxGraphRenderer;
import org.graphstream.ui.view.View;
import org.graphstream.ui.view.Viewer;
import org.graphstream.ui.view.camera.Camera;
import org.graphstream.ui.view.util.InteractiveElement;
import org.graphstream.ui.view.util.MouseManager;

import javax.swing.SwingUtilities;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

	private final SplitPane splitPane;
	private final BorderPane graphContainer;
	private final ScrollBar horizontalScrollBar = new ScrollBar();
	private final ScrollBar verticalScrollBar = new ScrollBar();
	private boolean syncingScrollBars = false;
	private double contentMinX, contentMaxX, contentMinY, contentMaxY;

	private final ConstraintSuggestionsView suggestionsView;

	private final ToolBar toolBar;

	/** See {@link at.jku.isse.ecco.gui.view.graph.ArtifactGraphView#tabVisible} for the full rationale. */
	private volatile boolean tabVisible = true;

	private boolean showLabels = true;

	public FeaturesView(EccoService service, MinimizationResults minimizationResults) {
		this.service = service;

		this.toolBar = new ToolBar();
		this.setTop(toolBar);

		Button exportButton = new Button("Export");
		exportButton.setOnAction(ae -> {
			toolBar.setDisable(true);

			FileChooser fileChooser = new FileChooser();
			// FileSinkFactory.sinkFor() picks a writer purely off the file extension and returns
			// null (see the "Unknown file extension" branch below) for anything it doesn't
			// recognize - list the formats GraphStream actually supports so the save dialog
			// doesn't let that happen by default.
			fileChooser.getExtensionFilters().addAll(
					new FileChooser.ExtensionFilter("GraphML (*.graphml)", "*.graphml"),
					new FileChooser.ExtensionFilter("GEXF (*.gexf)", "*.gexf"),
					new FileChooser.ExtensionFilter("DOT (*.dot)", "*.dot"),
					new FileChooser.ExtensionFilter("DGS (*.dgs)", "*.dgs"));
			fileChooser.setInitialFileName("feature-model.graphml");
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


		// MultiGraph, not SingleGraph: an accepted requires/excludes edge can land on the exact same
		// feature pair as the tree edge (or as another constraint edge), which SingleGraph rejects.
		this.graph = new MultiGraph("FeatureModel");
		// deliberately no layout sink/enableAutoLayout() anywhere in this class: node positions come
		// straight from FeatureModelTree.compute() so "earlier features on top" is a rendering
		// guarantee, not an emergent property of a force simulation - unlike the other graph tabs,
		// this one is a static tree, not a physics-driven graph.

		this.setOnScroll(event -> {
			if (null != view) {
				view.getCamera().setViewPercent(Math.max(0.05, Math.min(1.0,
						view.getCamera().getViewPercent() - 0.05 * event.getDeltaY() / event.getMultiplierY())));
				this.syncScrollBarsFromCamera();
			}
		});
		// trackpad pinch: a separate gesture from two-finger scroll, so it needs its own handler.
		this.setOnZoom(event -> {
			if (null != view) {
				view.getCamera().setViewPercent(Math.max(0.05, Math.min(1.0,
						view.getCamera().getViewPercent() / event.getZoomFactor())));
				this.syncScrollBarsFromCamera();
			}
		});

		showLabelsCheckbox.setSelected(this.showLabels);

		this.horizontalScrollBar.setOrientation(Orientation.HORIZONTAL);
		this.horizontalScrollBar.valueProperty().addListener((obs, oldV, newV) -> {
			if (this.syncingScrollBars || null == view) return;
			Point3 center = view.getCamera().getViewCenter();
			view.getCamera().setViewCenter(newV.doubleValue(), center.y, center.z);
		});
		this.verticalScrollBar.setOrientation(Orientation.VERTICAL);
		this.verticalScrollBar.valueProperty().addListener((obs, oldV, newV) -> {
			if (this.syncingScrollBars || null == view) return;
			Point3 center = view.getCamera().getViewCenter();
			// scrollbar convention (down = higher value) is the opposite of this graph's y axis
			// (up = higher value, "earlier features on top"), so invert.
			view.getCamera().setViewCenter(center.x, this.contentMinY + this.contentMaxY - newV.doubleValue(), center.z);
		});

		Label legend = new Label("Legend:  rectangle = feature  |  rectangle with a filled circle on top = mandatory (accepted)  |  "
				+ "grey arrow between features = positioning hint only (co-occurrence order), not a dependency or constraint  |  "
				+ "accepted requires/excludes constraints are listed in the panel on the right, not drawn on the graph");
		legend.setWrapText(true);
		legend.setPadding(new Insets(4, 8, 4, 8));
		legend.setStyle("-fx-font-size: 11px; -fx-font-style: italic;");

		this.graphContainer = new BorderPane();
		this.graphContainer.setTop(legend);
		this.graphContainer.setBottom(horizontalScrollBar);
		this.graphContainer.setRight(verticalScrollBar);

		this.suggestionsView = new ConstraintSuggestionsView(service, this::refreshNow, minimizationResults);
		this.splitPane = new SplitPane(graphContainer, suggestionsView);
		this.splitPane.setDividerPositions(0.7);
		this.setCenter(this.splitPane);

		service.addListener(this);
		Platform.runLater(() -> statusChangedEvent(service));
	}


	private void updateGraphStylesheet() {
		String textMode = this.showLabels ? "text-mode: normal; " : "text-mode: hidden; ";
		this.graph.setAttribute("ui.stylesheet",
				"edge { " + textMode + " size: 1px; shape: line; arrow-size: 6px, 4px; fill-color: #89878188; } " +
						// "gu" (graph units), not "px": a fixed pixel size stays constant on screen
						// regardless of zoom, so once the camera zooms out to fit more nodes, fixed-px
						// boxes start overlapping even though their (gu) centers are correctly spaced by
						// X_SPACING/Y_SPACING. "gu" keeps the box's visual footprint in the same
						// coordinate space as the layout, matching what computeContentBounds() below
						// already assumes.
						"node { " + textMode + " text-background-mode: plain; shape: box; size: " + NODE_WIDTH + "gu, " + NODE_HEIGHT + "gu; stroke-mode: plain; stroke-color: #000000; stroke-width: 1px; } ");
	}

	private void initView() {
		closeView();
		viewer = new FxViewer(graph, Viewer.ThreadingModel.GRAPH_IN_ANOTHER_THREAD);
		view = (FxViewPanel) viewer.addDefaultView(false, new FxGraphRenderer());
		// replaces the default manager's node-drag/rubber-band-select behavior (which would let a
		// drag silently detach a feature from its meaningful tree position) with plain click-drag
		// panning, since this view has no other use for mouse drag.
		view.setMouseManager(new PanMouseManager());

		this.graphContainer.setCenter(view);
	}

	private void closeView() {
		if (null == viewer) {
			return;
		}

		this.graphContainer.setCenter(new Pane());
		try {
			viewer.close();
		} catch (Exception ignored) {
			// see ArtifactGraphView.closeView() for why this is safely ignorable
		}
		view = null;
		viewer = null;
	}

	/**
	 * Click-drag pans the camera; replaces the default {@code MouseManager}, which instead drags
	 * whatever node/sprite is under the cursor (undesirable here - node positions come straight
	 * from {@link FeatureModelTree} and dragging one would visually detach it from its meaning)
	 * and rubber-band-selects on empty space (not used by this view at all).
	 */
	private final class PanMouseManager implements MouseManager {

		private View managedView;
		private double lastPxX, lastPxY;

		private final EventHandler<MouseEvent> pressed = event -> {
			this.managedView.requireFocus();
			this.lastPxX = event.getX();
			this.lastPxY = event.getY();
		};

		private final EventHandler<MouseEvent> dragged = event -> {
			Camera camera = this.managedView.getCamera();
			Point3 from = camera.transformPxToGu(this.lastPxX, this.lastPxY);
			Point3 to = camera.transformPxToGu(event.getX(), event.getY());
			Point3 center = camera.getViewCenter();
			camera.setViewCenter(center.x - (to.x - from.x), center.y - (to.y - from.y), center.z);
			this.lastPxX = event.getX();
			this.lastPxY = event.getY();
			FeaturesView.this.syncScrollBarsFromCamera();
		};

		private final EventHandler<MouseEvent> released = event -> {
		};

		@Override
		public void init(GraphicGraph graph, View view) {
			this.managedView = view;
			view.addListener(MouseEvent.MOUSE_PRESSED, pressed);
			view.addListener(MouseEvent.MOUSE_DRAGGED, dragged);
			view.addListener(MouseEvent.MOUSE_RELEASED, released);
		}

		@Override
		public void release() {
			this.managedView.removeListener(MouseEvent.MOUSE_PRESSED, pressed);
			this.managedView.removeListener(MouseEvent.MOUSE_DRAGGED, dragged);
			this.managedView.removeListener(MouseEvent.MOUSE_RELEASED, released);
		}

		@Override
		public EnumSet<InteractiveElement> getManagedTypes() {
			return EnumSet.noneOf(InteractiveElement.class);
		}
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

				if (snapshot.mandatoryNodeIds.contains(nodeSnapshot.id)) {
					// classic feature-diagram notation: a filled circle marks a mandatory feature.
					// Implemented as a small plain node sitting at the top edge of the feature's box
					// (not a GraphStream Sprite) so it reuses the exact node-positioning/styling path
					// already used above, instead of a second, unverified rendering mechanism.
					Node marker = this.graph.addNode("mandatory:" + nodeSnapshot.id);
					marker.setAttribute("label", "");
					marker.setAttribute("xyz", nodeSnapshot.x, nodeSnapshot.y + NODE_HEIGHT / 2.0, 0.1);
					marker.setAttribute("ui.style", "shape: circle; size: " + MANDATORY_MARKER_SIZE + "gu; fill-color: #000000; stroke-mode: none;");
				}
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

		computeContentBounds(snapshot);
		Platform.runLater(this::updateScrollBarRanges);
	}

	/**
	 * Bounds are computed from the snapshot's own coordinates, not from
	 * {@link org.graphstream.ui.view.camera.Camera#getMetrics()} - GraphStream only updates camera
	 * metrics during an actual render pass, so reading them synchronously right after mutating the
	 * graph could see stale/zeroed values. This is exact and available immediately since this class
	 * already computes every node's position itself.
	 */
	private void computeContentBounds(FeatureModelSnapshot snapshot) {
		if (snapshot.nodes.isEmpty()) {
			this.contentMinX = this.contentMaxX = this.contentMinY = this.contentMaxY = 0;
			return;
		}
		double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
		double minY = Double.POSITIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
		for (FeatureNodeSnapshot node : snapshot.nodes) {
			minX = Math.min(minX, node.x - NODE_WIDTH / 2.0);
			maxX = Math.max(maxX, node.x + NODE_WIDTH / 2.0);
			minY = Math.min(minY, node.y - NODE_HEIGHT / 2.0);
			maxY = Math.max(maxY, node.y + NODE_HEIGHT / 2.0);
		}
		this.contentMinX = minX;
		this.contentMaxX = maxX;
		this.contentMinY = minY;
		this.contentMaxY = maxY;
	}

	private void updateScrollBarRanges() {
		this.horizontalScrollBar.setMin(this.contentMinX);
		this.horizontalScrollBar.setMax(this.contentMaxX);
		this.verticalScrollBar.setMin(this.contentMinY);
		this.verticalScrollBar.setMax(this.contentMaxY);
		syncScrollBarsFromCamera();
	}

	/** Mirrors the camera's current position/zoom onto the scrollbars; call after any camera change. */
	private void syncScrollBarsFromCamera() {
		if (null == view) return;
		Camera camera = view.getCamera();
		Point3 center = camera.getViewCenter();
		double visibleWidth = (this.contentMaxX - this.contentMinX) * camera.getViewPercent();
		double visibleHeight = (this.contentMaxY - this.contentMinY) * camera.getViewPercent();

		this.syncingScrollBars = true;
		try {
			this.horizontalScrollBar.setVisibleAmount(visibleWidth);
			this.horizontalScrollBar.setValue(clamp(center.x, this.contentMinX, this.contentMaxX));
			this.verticalScrollBar.setVisibleAmount(visibleHeight);
			this.verticalScrollBar.setValue(clamp(this.contentMinY + this.contentMaxY - center.y, this.contentMinY, this.contentMaxY));
		} finally {
			this.syncingScrollBars = false;
		}
	}

	private static double clamp(double value, double min, double max) {
		if (min > max) return min; // degenerate (e.g. a single node): nothing to clamp against
		return Math.max(min, Math.min(max, value));
	}

	/** "#2a78d6" - GraphStream's inline "ui.style" attributes accept CSS hex colors directly. */
	private static String toHexColor(Color color) {
		return String.format("#%02x%02x%02x",
				(int) Math.round(color.getRed() * 255),
				(int) Math.round(color.getGreen() * 255),
				(int) Math.round(color.getBlue() * 255));
	}


	// wider than the tree layout's old circle nodes, so X_SPACING grows with it to keep siblings
	// at the same depth from visually overlapping.
	private static final double X_SPACING = 130;
	private static final double Y_SPACING = 110;
	private static final int NODE_WIDTH = 90;
	private static final int NODE_HEIGHT = 32;
	private static final int MANDATORY_MARKER_SIZE = 10;

	private static final class FeatureModelSnapshot {
		final List<FeatureNodeSnapshot> nodes = new ArrayList<>();
		final List<FeatureEdgeSnapshot> edges = new ArrayList<>();
		final Set<String> mandatoryNodeIds = new HashSet<>();
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
		Map<String, String> nodeIdByFeatureName = new HashMap<>();
		for (FeatureModelTree.Placement placement : FeatureModelTree.compute(this.service.getRepository())) {
			double x = placement.x * X_SPACING;
			double y = -placement.depth * Y_SPACING;
			snapshot.nodes.add(new FeatureNodeSnapshot(placement.feature.getId(), placement.feature.getName(), x, y, placement.rootIndex));
			if (placement.parent != null) {
				snapshot.edges.add(new FeatureEdgeSnapshot(placement.parent.getId(), placement.feature.getId()));
			}
			nodeIdByFeatureName.put(placement.feature.getName(), placement.feature.getId());
		}

		// only MANDATORY is shown here (as a node decorator, below); accepted REQUIRES/EXCLUDES
		// already have a home in ConstraintSuggestionsView's "Accepted" list, so this tab doesn't
		// need a second, redundant place to show them.
		for (Constraint constraint : this.service.getRepository().getConstraints()) {
			if (constraint.getKind() != Constraint.Kind.MANDATORY) continue;
			String id = nodeIdByFeatureName.get(constraint.getFeatureA());
			if (id != null) snapshot.mandatoryNodeIds.add(id);
		}
		return snapshot;
	}

	/**
	 * Re-mines/re-lays-out and re-renders the graph right away, off the calling thread. Unlike
	 * {@link #statusChangedEvent}, this isn't triggered by a real {@link EccoService} event (no
	 * commit/checkout happened) -- it's called by {@link ConstraintSuggestionsView} whenever a
	 * suggestion is accepted/rejected/undone, since none of those go through a code path that fires
	 * an {@link at.jku.isse.ecco.service.listener.EccoListener} event.
	 */
	private void refreshNow() {
		if (!this.service.isInitialized() || !this.tabVisible) return;
		new Thread(() -> {
			FeatureModelSnapshot snapshot = this.buildSnapshot();
			Platform.runLater(() -> {
				if (this.viewer == null || this.view == null) {
					initView();
				}
				this.refreshGraph(snapshot);
			});
		}).start();
	}

	/**
	 * Called by the containing tab whenever it's selected or deselected - see
	 * {@link at.jku.isse.ecco.gui.view.graph.ArtifactGraphView#setTabVisible} for the full rationale
	 * (skip snapshot work while hidden, catch up with one fresh render when shown again).
	 */
	public void setTabVisible(boolean tabVisible) {
		boolean becameVisible = tabVisible && !this.tabVisible;
		this.tabVisible = tabVisible;
		this.suggestionsView.setTabVisible(tabVisible);
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
