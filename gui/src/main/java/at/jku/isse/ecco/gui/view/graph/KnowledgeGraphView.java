package at.jku.isse.ecco.gui.view.graph;

import at.jku.isse.ecco.gui.CategoricalColorPalette;
import at.jku.isse.ecco.gui.EditableSpinner;
import at.jku.isse.ecco.gui.ExceptionAlert;
import at.jku.isse.ecco.gui.view.KnowledgeGraphLayout;
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
import javafx.scene.control.Spinner;
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
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Cross-entity view of the repository: Features, Constraints, Commits, Associations, and Variants,
 * one horizontal lane per entity type, plus the direct (object-reference) relationships between
 * them - see {@link KnowledgeGraphLayout} for the actual algorithm (kept separate from this class
 * so it has no JavaFX/GraphStream dependency and can be tested on its own, same split as
 * {@code FeaturesView}/{@code FeatureModelTree}).
 * <p>
 * Deliberately not a physics graph: five structurally different entity kinds laid out by
 * {@link org.graphstream.ui.layout.springbox.implementations.SpringBox} would be an unreadable
 * hairball (unlike {@code ArtifactGraphView}'s homogeneous artifact tree) - node positions come
 * straight from {@link KnowledgeGraphLayout#compute}, exactly like {@code FeaturesView}.
 */
public class KnowledgeGraphView extends BorderPane implements EccoListener {

	private final EccoService service;

	private final Graph graph;
	private FxViewer viewer;
	private FxViewPanel view;

	private final BorderPane graphContainer;
	private final ScrollBar horizontalScrollBar = new ScrollBar();
	private final ScrollBar verticalScrollBar = new ScrollBar();
	private boolean syncingScrollBars = false;
	private double contentMinX, contentMaxX, contentMinY, contentMaxY;

	private final ToolBar toolBar;

	/** See {@link ArtifactGraphView#tabVisible} for the full rationale. */
	private volatile boolean tabVisible = true;

	private boolean showLabels = DEFAULT_SHOW_LABELS;
	private final Set<KnowledgeGraphLayout.EntityKind> enabledKinds = EnumSet.allOf(KnowledgeGraphLayout.EntityKind.class);
	private int commitLimit = DEFAULT_COMMIT_LIMIT;
	private boolean includeComputedVariantAssociationEdges = false;

	public KnowledgeGraphView(EccoService service) {
		this.service = service;

		this.toolBar = new ToolBar();
		this.setTop(toolBar);

		CheckBox showLabelsCheckbox = this.buildToolBar();


		System.setProperty("org.graphstream.ui.renderer", "org.graphstream.ui.j2dviewer.J2DGraphRenderer");


		// MultiGraph, not SingleGraph: mixing several relation kinds between entities risks more
		// than one edge landing on the same source/target pair, which SingleGraph rejects (same
		// reasoning as FeaturesView).
		this.graph = new MultiGraph("KnowledgeGraph");
		// deliberately no layout sink/enableAutoLayout(): node positions come straight from
		// KnowledgeGraphLayout.compute(), same as FeaturesView.

		this.setOnScroll(event -> {
			if (null != view) {
				view.getCamera().setViewPercent(Math.max(0.05, Math.min(1.0,
						view.getCamera().getViewPercent() - 0.05 * event.getDeltaY() / event.getMultiplierY())));
				this.syncScrollBarsFromCamera();
			}
		});
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
			view.getCamera().setViewCenter(center.x, this.contentMinY + this.contentMaxY - newV.doubleValue(), center.z);
		});

		Label legend = new Label("Nodes (top to bottom): Feature | Constraint | Commit | Association | Variant.  "
				+ "Edges: touches (commit->association) | selects (commit/variant->feature) | requires | excludes | "
				+ "mandatory | produces (commit->variant) | touches, computed (variant->association, only shown when enabled above).");
		legend.setWrapText(true);
		legend.setPadding(new Insets(4, 8, 4, 8));
		legend.setStyle("-fx-font-size: 11px; -fx-font-style: italic;");

		this.graphContainer = new BorderPane();
		this.graphContainer.setTop(legend);
		this.graphContainer.setBottom(horizontalScrollBar);
		this.graphContainer.setRight(verticalScrollBar);
		this.setCenter(this.graphContainer);

		service.addListener(this);
		Platform.runLater(() -> statusChangedEvent(service));
	}

	/**
	 * Builds the toolbar: one checkbox per entity-type lane, the commit-window spinner, the
	 * opt-in computed-edges checkbox, Show Labels, and Export - all wired to their handlers. Split
	 * out of the constructor purely for readability, same as {@code ArtifactGraphView.buildToolBar()}.
	 * Returns the Show Labels checkbox so the constructor can set its initial value once
	 * {@link #graph} exists (see the comment there for why that ordering matters).
	 */
	private CheckBox buildToolBar() {
		Button exportButton = new Button("Export");
		exportButton.setOnAction(ae -> {
			toolBar.setDisable(true);

			FileChooser fileChooser = new FileChooser();
			File selectedFile = fileChooser.showSaveDialog(KnowledgeGraphView.this.getScene().getWindow());

			if (selectedFile != null) {
				FileSink out = FileSinkFactory.sinkFor(selectedFile.toString());
				if (out != null) {
					try {
						out.writeAll(KnowledgeGraphView.this.graph, selectedFile.toString());
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
			KnowledgeGraphView.this.showLabels = newVal;
			KnowledgeGraphView.this.updateGraphStylesheet();
		});

		toolBar.getItems().setAll(exportButton, new Separator(), showLabelsCheckbox, new Separator());

		for (KnowledgeGraphLayout.EntityKind kind : KnowledgeGraphLayout.EntityKind.values()) {
			CheckBox kindCheckbox = new CheckBox(displayName(kind));
			kindCheckbox.setSelected(true);
			kindCheckbox.selectedProperty().addListener((ov, oldVal, newVal) -> {
				if (newVal) {
					KnowledgeGraphView.this.enabledKinds.add(kind);
				} else {
					KnowledgeGraphView.this.enabledKinds.remove(kind);
				}
				KnowledgeGraphView.this.refreshGraph();
			});
			toolBar.getItems().add(kindCheckbox);
		}
		toolBar.getItems().add(new Separator());

		Label commitLimitLabel = new Label("Commits shown: ");
		Spinner<Integer> commitLimitSpinner = new EditableSpinner(1, COMMIT_LIMIT_MAX, commitLimit);
		commitLimitSpinner.valueProperty().addListener((obs, oldValue, newValue) -> {
			KnowledgeGraphView.this.commitLimit = newValue;
			KnowledgeGraphView.this.refreshGraph();
		});
		toolBar.getItems().addAll(commitLimitLabel, commitLimitSpinner, new Separator());

		CheckBox computedEdgesCheckbox = new CheckBox("Show variant -> association edges (computed)");
		computedEdgesCheckbox.setSelected(false);
		computedEdgesCheckbox.selectedProperty().addListener((ov, oldVal, newVal) -> {
			KnowledgeGraphView.this.includeComputedVariantAssociationEdges = newVal;
			KnowledgeGraphView.this.refreshGraph();
		});
		toolBar.getItems().add(computedEdgesCheckbox);

		return showLabelsCheckbox;
	}

	private static String displayName(KnowledgeGraphLayout.EntityKind kind) {
		String name = kind.name();
		return Character.toUpperCase(name.charAt(0)) + name.substring(1).toLowerCase() + "s";
	}

	private void updateGraphStylesheet() {
		String textMode = this.showLabels ? "text-mode: normal; " : "text-mode: hidden; ";
		StringBuilder css = new StringBuilder();
		css.append("node { ").append(textMode)
				.append("text-background-mode: plain; shape: box; size: ").append(NODE_WIDTH).append("gu, ").append(NODE_HEIGHT)
				.append("gu; stroke-mode: plain; stroke-color: #000000; stroke-width: 1px; } ");
		for (KnowledgeGraphLayout.EntityKind kind : KnowledgeGraphLayout.EntityKind.values()) {
			css.append("node.").append(kind.name().toLowerCase()).append(" { fill-color: ")
					.append(toHexColor(CategoricalColorPalette.colorForIndex(kind.ordinal()))).append("; } ");
		}
		css.append("edge { ").append(textMode).append(" size: 1px; arrow-size: 6px, 4px; } ");
		for (Map.Entry<KnowledgeGraphLayout.EdgeKind, String> entry : EDGE_COLORS.entrySet()) {
			css.append("edge.").append(entry.getKey().name().toLowerCase()).append(" { fill-color: ").append(entry.getValue()).append("; } ");
		}
		this.graph.setAttribute("ui.stylesheet", css.toString());
	}

	private void initView() {
		closeView();
		viewer = new FxViewer(graph, Viewer.ThreadingModel.GRAPH_IN_ANOTHER_THREAD);
		view = (FxViewPanel) viewer.addDefaultView(false, new FxGraphRenderer());
		// replaces the default manager's node-drag behavior with plain click-drag panning, same
		// reasoning as FeaturesView.PanMouseManager - node positions come straight from
		// KnowledgeGraphLayout and dragging one would visually detach it from its meaning.
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

	/** Click-drag panning; see {@code FeaturesView.PanMouseManager} for the full rationale (identical here). */
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
			KnowledgeGraphView.this.syncScrollBarsFromCamera();
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

	/** Builds a fresh snapshot and queues it for rendering; see {@code ArtifactGraphView#refreshGraph()}. */
	private void refreshGraph() {
		this.refreshGraph(this.buildSnapshot());
	}

	private void refreshGraph(KnowledgeGraphLayout.Snapshot snapshot) {
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

	/**
	 * Walks the repository via {@link KnowledgeGraphLayout#compute}. Touches only the ecco data
	 * model (no GraphStream/Swing/JavaFX), so it's safe to call synchronously from any thread -
	 * notably the commit thread itself, from {@link #statusChangedEvent} - see
	 * {@code ArtifactGraphView#buildSnapshot()} for the full rationale.
	 */
	private KnowledgeGraphLayout.Snapshot buildSnapshot() {
		return KnowledgeGraphLayout.compute(this.service.getRepository(), Set.copyOf(this.enabledKinds),
				this.commitLimit, this.includeComputedVariantAssociationEdges);
	}

	/**
	 * Renders a previously-built snapshot into the live GraphStream graph. Swing-thread only. Same
	 * flicker-avoidance (view hidden during rebuild) and camera-reset-only-on-first-render as
	 * {@code ArtifactGraphView}/{@code FeaturesView}.
	 */
	private void applySnapshot(KnowledgeGraphLayout.Snapshot snapshot) {
		assert viewer != null && view != null;

		boolean firstRender = this.graph.getNodeCount() == 0;

		this.view.setVisible(false);
		try {
			this.graph.clear();

			this.graph.setAttribute("ui.quality");
			this.graph.setAttribute("ui.antialias");

			for (KnowledgeGraphLayout.Placement placement : snapshot.nodes) {
				Node graphNode = this.graph.addNode(placement.id);
				String label = placement.kind == KnowledgeGraphLayout.EntityKind.ASSOCIATION && placement.artifactCount >= 0
						? placement.label + " (" + placement.artifactCount + ")"
						: placement.label;
				graphNode.setAttribute("label", label);
				graphNode.setAttribute("xyz", placement.x, placement.y, 0.0);
				graphNode.setAttribute("ui.class", placement.kind.name().toLowerCase());
			}
			for (KnowledgeGraphLayout.Edge edge : snapshot.edges) {
				this.graph.addEdge(edge.id, edge.sourceId, edge.targetId, true)
						.setAttribute("ui.class", edge.kind.name().toLowerCase());
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

	/** See {@code FeaturesView.computeContentBounds} - same reasoning (camera metrics aren't reliable synchronously after a mutation). */
	private void computeContentBounds(KnowledgeGraphLayout.Snapshot snapshot) {
		if (snapshot.nodes.isEmpty()) {
			this.contentMinX = this.contentMaxX = this.contentMinY = this.contentMaxY = 0;
			return;
		}
		double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
		double minY = Double.POSITIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
		for (KnowledgeGraphLayout.Placement node : snapshot.nodes) {
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
		if (min > max) return min;
		return Math.max(min, Math.min(max, value));
	}

	/** "#2a78d6" - GraphStream's inline "ui.style"/stylesheet attributes accept CSS hex colors directly. */
	private static String toHexColor(Color color) {
		return String.format("#%02x%02x%02x",
				(int) Math.round(color.getRed() * 255),
				(int) Math.round(color.getGreen() * 255),
				(int) Math.round(color.getBlue() * 255));
	}


	private static final int NODE_WIDTH = 110;
	private static final int NODE_HEIGHT = 32;
	private static final boolean DEFAULT_SHOW_LABELS = true;
	private static final int DEFAULT_COMMIT_LIMIT = 50;
	private static final int COMMIT_LIMIT_MAX = 1000;

	/**
	 * Fixed colors per relation kind, reused from {@link CategoricalColorPalette}'s own hues for
	 * visual consistency rather than inventing a second palette - kept distinct from node fill
	 * color (which encodes entity-type lane instead, via {@link CategoricalColorPalette#colorForIndex}),
	 * so an edge's color and a node's fill never carry the same dimension of meaning.
	 */
	private static final Map<KnowledgeGraphLayout.EdgeKind, String> EDGE_COLORS = new EnumMap<>(KnowledgeGraphLayout.EdgeKind.class);

	static {
		EDGE_COLORS.put(KnowledgeGraphLayout.EdgeKind.TOUCHES, "#4a3aa788");
		EDGE_COLORS.put(KnowledgeGraphLayout.EdgeKind.SELECTS, "#89878188");
		EDGE_COLORS.put(KnowledgeGraphLayout.EdgeKind.REQUIRES, "#1baf7a");
		EDGE_COLORS.put(KnowledgeGraphLayout.EdgeKind.EXCLUDES, "#e34948");
		EDGE_COLORS.put(KnowledgeGraphLayout.EdgeKind.MANDATORY, "#000000");
		EDGE_COLORS.put(KnowledgeGraphLayout.EdgeKind.PRODUCES_VARIANT, "#2a78d6");
		EDGE_COLORS.put(KnowledgeGraphLayout.EdgeKind.TOUCHES_COMPUTED, "#eda10099");
	}

	/**
	 * Called by the containing tab whenever it's selected or deselected - see
	 * {@code ArtifactGraphView#setTabVisible} for the full rationale (skip snapshot work while
	 * hidden, catch up with one fresh render when shown again).
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
	 * See {@code ArtifactGraphView#statusChangedEvent} - the same rationale applies here: this is
	 * what actually fires after every commit, the snapshot is built synchronously on the calling
	 * thread for point-in-time correctness across a multi-commit burst, and it's skipped entirely
	 * while the tab is hidden.
	 */
	@Override
	public void statusChangedEvent(EccoService service) {
		if (service.isInitialized()) {
			if (!this.tabVisible) {
				Platform.runLater(() -> this.setDisable(false));
				return;
			}
			KnowledgeGraphLayout.Snapshot snapshot = this.buildSnapshot();
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
