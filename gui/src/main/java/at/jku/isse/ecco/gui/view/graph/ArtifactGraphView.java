package at.jku.isse.ecco.gui.view.graph;

import at.jku.isse.ecco.adapter.dispatch.DirectoryArtifactData;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.composition.LazyCompositionRootNode;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.gui.CategoricalColorPalette;
import at.jku.isse.ecco.gui.EditableSpinner;
import at.jku.isse.ecco.gui.ExceptionAlert;
import at.jku.isse.ecco.gui.TabVisibilityAware;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.service.listener.EccoListener;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import org.graphstream.graph.Edge;
import org.graphstream.graph.Graph;
import org.graphstream.graph.Node;
import org.graphstream.graph.implementations.SingleGraph;
import org.graphstream.stream.file.FileSink;
import org.graphstream.stream.file.FileSinkFactory;
import org.graphstream.ui.geom.Point3;
import org.graphstream.ui.graphicGraph.GraphicElement;
import org.graphstream.ui.javafx.FxGraphRenderer;
import org.graphstream.ui.layout.Layout;
import org.graphstream.ui.layout.springbox.implementations.SpringBox;
import org.graphstream.ui.fx_viewer.FxViewPanel;
import org.graphstream.ui.fx_viewer.FxViewer;
import org.graphstream.ui.fx_viewer.util.FxMouseOverMouseManager;
import org.graphstream.ui.view.Viewer;
import org.graphstream.ui.view.util.InteractiveElement;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ArtifactGraphView extends BorderPane implements EccoListener, TabVisibilityAware {

	private final EccoService service;

	private final Graph graph;
	private final Layout layout;
	private FxViewer viewer;
	private FxViewPanel view;

	private final ToolBar toolBar;

	/**
	 * Floating node-info panel shown by {@link HoverOverlayMouseManager} after the mouse has
	 * rested on a node for {@link #HOVER_DELAY_MS} - a plain JavaFX node stacked on top of {@link
	 * #view} (which is itself real JavaFX, not a Swing bridge, despite GraphStream's rendering
	 * running on its own thread per {@link Viewer.ThreadingModel#GRAPH_IN_ANOTHER_THREAD}).
	 * {@code mouseTransparent} so it never steals the hover/click GraphStream itself needs from
	 * the node underneath it.
	 */
	private final Label hoverInfoLabel = new Label();
	private final VBox hoverOverlay = createHoverOverlay(this.hoverInfoLabel);

	/**
	 * Node id -> hover info, rebuilt (as a whole new map, never mutated in place) alongside every
	 * {@link #applySnapshot}. Read from {@link #showHoverOverlay} on the FX thread while written
	 * from the Swing thread GraphStream's rendering runs on - safe via plain reference-swap
	 * publication under {@code volatile}, the same pattern {@link #tabVisible} already uses.
	 */
	private volatile Map<String, HoverInfo> hoverInfoById = Map.of();

	/** Everything {@link #showHoverOverlay} needs to display for one node, looked up by id rather than read off the GraphicElement itself - see {@link #hoverInfoById}. */
	private record HoverInfo(String text, Integer successorsCount) {
	}

	private static VBox createHoverOverlay(Label infoLabel) {
		VBox overlay = new VBox(infoLabel);
		overlay.setMouseTransparent(true);
		overlay.setVisible(false);
		// StackPane resizes children to fill its own bounds by default - without this, the
		// overlay stretched to the size of the whole graph view instead of sizing to its content
		overlay.setMaxSize(VBox.USE_PREF_SIZE, VBox.USE_PREF_SIZE);
		overlay.setStyle("-fx-background-color: rgba(20,20,20,0.85); -fx-padding: 6px 10px; " +
				"-fx-background-radius: 4px; -fx-border-color: rgba(255,255,255,0.3); -fx-border-radius: 4px;");
		infoLabel.setStyle("-fx-text-fill: white; -fx-font-size: 11px;");
		infoLabel.setWrapText(false);
		return overlay;
	}

	/**
	 * Whether this view's tab is the one currently showing, as reported by {@link #setTabVisible}.
	 * Defaults to true so a caller that doesn't wire tab-selection tracking gets the old
	 * always-render behavior rather than a silently inert view. Volatile since it's read from
	 * {@link #statusChangedEvent}, which - notably, for a commit - runs on the commit thread, not
	 * the FX thread that writes it.
	 */
	private volatile boolean tabVisible = true;

	private boolean showLabels = DEFAULT_SHOW_LABELS;

	private int childCountLimit = DEFAULT_CHILD_COUNT_LIMIT;
	private int depthLimit = DEFAULT_DEPTH_LIMIT;

	public ArtifactGraphView(EccoService service) {
		this.service = service;

		this.toolBar = new ToolBar();
		this.setTop(toolBar);

		CheckBox showLabelsCheckbox = this.buildToolBar();


		System.setProperty("org.graphstream.ui.renderer", "org.graphstream.ui.j2dviewer.J2DGraphRenderer");


		this.graph = new SingleGraph("ArtifactsGraph");

		this.layout = new SpringBox(false);
		this.graph.addSink(this.layout);
		this.layout.addAttributeSink(this.graph);

		this.setOnScroll(event -> {
			if (null != view) {
				view.getCamera().setViewPercent(Math.max(0.1, Math.min(1.0,
						view.getCamera().getViewPercent() - 0.05 * event.getDeltaY() / event.getMultiplierY())));
			}
		});

		// must run after `graph` is constructed above: setSelected(true) (DEFAULT_SHOW_LABELS)
		// changes the checkbox away from its own JavaFX default of false, which synchronously fires
		// its listener (wired in buildToolBar()), and that listener calls updateGraphStylehseet(),
		// which touches this.graph.
		showLabelsCheckbox.setSelected(this.showLabels);

		service.addListener(this);
		Platform.runLater(() -> statusChangedEvent(service));
	}

	/**
	 * Builds the toolbar: child-count/depth-limit spinners, Export/Reset buttons, and the Show Labels
	 * checkbox, all wired to their handlers. Split out of the constructor purely for readability -- no
	 * behavior change from the previous single-constructor version. Returns the Show Labels checkbox
	 * so the constructor can set its initial value once {@link #graph} exists (see the comment there
	 * for why that ordering matters).
	 */
	private CheckBox buildToolBar() {
		Spinner<Integer> childCountLimitSpinner = new EditableSpinner(1, CHILD_COUNT_LIMIT_MAX, childCountLimit);
		childCountLimitSpinner.setEditable(true);
		Label childCountLimitLabel = new Label("Child Count Limit: ");
		childCountLimitSpinner.valueProperty().addListener((obs, oldValue, newValue) -> {
			ArtifactGraphView.this.childCountLimit = newValue;
			ArtifactGraphView.this.refreshGraph();
		});

		Spinner<Integer> depthLimitSpinner = new EditableSpinner(1, DEPTH_LIMIT_MAX, depthLimit);
		depthLimitSpinner.setEditable(true);
		Label depthLimitLabel = new Label("Depth Limit: ");
		depthLimitSpinner.valueProperty().addListener((obs, oldValue, newValue) -> {
			ArtifactGraphView.this.depthLimit = newValue;
			ArtifactGraphView.this.refreshGraph();
		});

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
			fileChooser.setInitialFileName("artifact-graph.graphml");
			File selectedFile = fileChooser.showSaveDialog(ArtifactGraphView.this.getScene().getWindow());

			if (selectedFile != null) {
				FileSink out = FileSinkFactory.sinkFor(selectedFile.toString());
				if (out != null) {
					try {
						out.writeAll(ArtifactGraphView.this.graph, selectedFile.toString());
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
		showLabelsCheckbox.selectedProperty().addListener((ov, old_val, new_val) -> {
			ArtifactGraphView.this.showLabels = new_val;
			ArtifactGraphView.this.updateGraphStylehseet(new_val);
		});

		Button resetButton = new Button("Reset");
		resetButton.setOnAction(e -> {
			// each control's own listener (already wired above) applies the new value and
			// triggers a redraw, so nothing else needs to happen here
			childCountLimitSpinner.getValueFactory().setValue(DEFAULT_CHILD_COUNT_LIMIT);
			depthLimitSpinner.getValueFactory().setValue(DEFAULT_DEPTH_LIMIT);
			showLabelsCheckbox.setSelected(DEFAULT_SHOW_LABELS);
		});


		toolBar.getItems().setAll(exportButton, resetButton, new Separator(), showLabelsCheckbox, new Separator(), childCountLimitLabel, childCountLimitSpinner, new Separator(), depthLimitLabel, depthLimitSpinner, new Separator());

		return showLabelsCheckbox;
	}


	private void updateNodesAndEdgesStyles() {
		Map<String, Integer> idColorMap = new HashMap<>();
		int nextColor = 0;

		for (Node node : this.graph.nodes().collect(Collectors.toSet())) {
			int size = DEFAULT_SIZE;
			if (node.hasAttribute(SUCCESSOR_COUNT_ATTRIBUTE)) {
				int successorsCount = node.getAttribute(SUCCESSOR_COUNT_ATTRIBUTE, Integer.class);
				// Logarithmic, not square-root: a tree's subtree counts span a *much* wider dynamic
				// range than "area vs. count" alone accounts for - depth alone means a root/near-root
				// node's count can be 1000x a leaf's, since it's a sum over every level beneath it.
				// Against a max that large, sqrt(1) vs. sqrt(7) still barely move the needle (both
				// round to the same on-screen pixel size once divided by sqrt(1000s)) - log1p keeps
				// low counts spread apart even against a huge max, which is exactly what "a 1-artifact
				// subnode should look smaller than a 7-artifact summary node" needs.
				double t = this.maxSuccessorsCount > 0 ? Math.log1p(Math.max(0, successorsCount)) / Math.log1p(this.maxSuccessorsCount) : 0;
				size = (int) (MIN_SIZE + t * (MAX_SIZE - MIN_SIZE));
			}

			// nodes with no association (e.g. a "dropped children" summary node spanning several
			// associations, see addSummaryNode) get the palette's neutral fallback color rather than
			// no fill-color at all - GraphStream defaults an unset fill-color to black, which is the
			// exact bug fixed earlier for the general case
			Color fillColor = CategoricalColorPalette.OTHER;
			if (node.hasAttribute(ASSOC_ID_ATTRIBUTE)) {
				String id = node.getAttribute(ASSOC_ID_ATTRIBUTE, String.class);
				if (!idColorMap.containsKey(id)) {
					idColorMap.put(id, nextColor++);
				}
				fillColor = CategoricalColorPalette.colorForIndex(idColorMap.get(id));
			}
			node.setAttribute("ui.style", "size: " + size + "px; fill-color: " + toHexColor(fillColor) + ";");
		}

		for (Edge edge : this.graph.edges().collect(Collectors.toSet())) {
			Color fillColor = CategoricalColorPalette.OTHER;
			if (edge.getSourceNode().hasAttribute(ASSOC_ID_ATTRIBUTE)) {
				String id = edge.getSourceNode().getAttribute(ASSOC_ID_ATTRIBUTE, String.class);
				if (!idColorMap.containsKey(id)) {
					idColorMap.put(id, nextColor++);
				}
				fillColor = CategoricalColorPalette.colorForIndex(idColorMap.get(id));
			}
			edge.setAttribute("ui.style", "fill-color: " + toHexColorWithAlpha(fillColor, 0x88) + ";");
		}
	}

	/** "#2a78d6" - GraphStream's inline "ui.style" attributes accept CSS hex colors directly. */
	private static String toHexColor(Color color) {
		return String.format("#%02x%02x%02x",
				(int) Math.round(color.getRed() * 255),
				(int) Math.round(color.getGreen() * 255),
				(int) Math.round(color.getBlue() * 255));
	}

	/** As {@link #toHexColor}, with an appended 8-bit alpha channel, e.g. "#2a78d688". */
	private static String toHexColorWithAlpha(Color color, int alpha) {
		return toHexColor(color) + String.format("%02x", alpha);
	}

	private void updateGraphStylehseet(boolean showLabels) {
		String textMode = "text-mode: normal; ";
		if (!showLabels)
			textMode = "text-mode: hidden; ";

		this.graph.setAttribute("ui.stylesheet",
				"edge { size: 1px; shape: blob; arrow-shape: none; arrow-size: 3px, 3px; } " +
						"node { " + textMode + " text-background-mode: plain;  shape: circle; size: " + DEFAULT_SIZE + "px; stroke-mode: plain; stroke-color: #000000; stroke-width: 1px; } ");
	}

	private void initView() {
		closeView();
		viewer = new FxViewer(graph, Viewer.ThreadingModel.GRAPH_IN_ANOTHER_THREAD);
		view = (FxViewPanel)  viewer.addDefaultView(false, new FxGraphRenderer());
		view.setMouseManager(new HoverOverlayMouseManager());

		StackPane stackPane = new StackPane(view, this.hoverOverlay);
		StackPane.setAlignment(this.hoverOverlay, Pos.TOP_LEFT);
		setCenter(stackPane);
	}

	private void closeView() {
		this.hoverOverlay.setVisible(false);

		if (null == viewer) {
			return;
		}

		setCenter(null);
		try {
			viewer.close();
		} catch (Exception ignored) {
			// GraphStream's FxGraphRenderer can NPE while tearing down a view that was
			// created but never rendered yet (e.g. closed again in quick succession by
			// back-to-back status-changed events during a batch commit) - harmless here
			// since the viewer/view are discarded immediately after anyway.
		}
		view = null;
		viewer = null;
	}

	/**
	 * Positions and fills in {@link #hoverOverlay} for the given node, then makes it visible.
	 * Called from {@link HoverOverlayMouseManager}, which - per {@link FxMouseOverMouseManager}'s
	 * own implementation - runs its hover-delay timer on a background {@code java.util.Timer}
	 * thread, not the FX Application Thread, so every caller goes through {@code Platform.runLater}.
	 */
	private void showHoverOverlay(GraphicElement element) {
		if (this.view == null) {
			return;
		}

		HoverInfo info = this.hoverInfoById.get(element.getId());
		StringBuilder text = new StringBuilder(info != null && info.text() != null && !info.text().isBlank() ? info.text() : "(unnamed)");
		if (info != null && info.successorsCount() != null) {
			text.append("\nArtifacts: ").append(info.successorsCount());
		}
		this.hoverInfoLabel.setText(text.toString());

		Point3 pixelPos = this.view.getCamera().transformGuToPx(element.getX(), element.getY(), element.getZ());
		this.hoverOverlay.setTranslateX(pixelPos.x + HOVER_OVERLAY_OFFSET);
		this.hoverOverlay.setTranslateY(pixelPos.y + HOVER_OVERLAY_OFFSET);
		this.hoverOverlay.setVisible(true);
	}

	private void hideHoverOverlay() {
		this.hoverOverlay.setVisible(false);
	}

	/**
	 * GraphStream's own FX hover-with-delay mouse manager, scoped to nodes only (not edges) -
	 * {@link #mouseOverElement}/{@link #mouseLeftElement} are its extension points, called once the
	 * mouse has rested on (or left) a node for {@link #HOVER_DELAY_MS}.
	 */
	private class HoverOverlayMouseManager extends FxMouseOverMouseManager {

		HoverOverlayMouseManager() {
			super(EnumSet.of(InteractiveElement.NODE), HOVER_DELAY_MS);
		}

		@Override
		protected void mouseOverElement(GraphicElement element) {
			super.mouseOverElement(element);
			Platform.runLater(() -> ArtifactGraphView.this.showHoverOverlay(element));
		}

		@Override
		protected void mouseLeftElement(GraphicElement element) {
			super.mouseLeftElement(element);
			Platform.runLater(ArtifactGraphView.this::hideHoverOverlay);
		}
	}

	/**
	 * Builds a fresh snapshot of the repository's current associations and queues it for rendering.
	 * Used by the toolbar's child-count/depth-limit spinners, which trigger directly on the FX
	 * thread in response to a user edit - only the current values matter there, unlike the
	 * point-in-time correctness {@link #statusChangedEvent} needs (see {@link #buildSnapshot()}).
	 */
	private void refreshGraph() {
		this.refreshGraph(this.buildSnapshot());
	}

	/**
	 * Queues an already-built snapshot for rendering on GraphStream's background Swing thread.
	 * Deliberately does NOT skip/coalesce a render just because a newer snapshot has since been
	 * queued (e.g. committing several folders back to back): every snapshot is rendered in order,
	 * so a viewer left open during a multi-folder commit visibly grows one commit at a time instead
	 * of jumping straight to the final state. The only guard is against the repository having been
	 * closed (viewer/view torn down) while this was queued, checked both when scheduling and again
	 * right before use since that happens on a different thread.
	 */
	private void refreshGraph(GraphSnapshot snapshot) {
		if (this.viewer == null || this.view == null) {
			return;
		}

		this.toolBar.setDisable(true);
		SwingUtilities.invokeLater(() -> {
			if (this.viewer == null || this.view == null) {
				return;
			}
			this.applySnapshot(snapshot, this.showLabels);
			Platform.runLater(() -> this.toolBar.setDisable(false));
		});
	}

	/**
	 * Walks the repository's current associations into a plain-data {@link GraphSnapshot}. Touches
	 * only the ecco data model (no GraphStream/Swing/JavaFX), so it's safe to call synchronously
	 * from any thread - notably the commit thread itself, from {@link #statusChangedEvent}, so each
	 * commit's exact state gets captured before the next commit can run.
	 */
	private GraphSnapshot buildSnapshot() {
		GraphSnapshot snapshot = new GraphSnapshot();
		LazyCompositionRootNode compRootNode = new LazyCompositionRootNode();
		for (Association association : this.service.getRepository().getAssociations()) {
			compRootNode.addOrigNode(association.getRootNode());
		}
		this.traverseSnapshot(compRootNode, 0, snapshot);
		return snapshot;
	}

	/**
	 * Renders a previously-built snapshot into the live GraphStream graph. Swing-thread only.
	 * <p>
	 * Node/edge identity isn't stable across snapshots (ids are just a sequential counter assigned
	 * during traversal - the underlying ecco model doesn't expose a persistent per-artifact id this
	 * view could otherwise key off of), so an in-place diff against the previous render isn't
	 * possible; every call still does a full {@code graph.clear()} and rebuild. Two things are done
	 * to keep that from looking like a flicker on every commit: the view is hidden for the duration
	 * of the rebuild, since GraphStream renders on its own thread (the {@code GRAPH_IN_ANOTHER_THREAD}
	 * model) independently of this one and could otherwise catch and paint the graph mid-rebuild -
	 * empty right after {@code clear()}, or half-populated partway through the node/edge loops below
	 * - as a visible blank/broken frame; and the camera is only reset on the very first render, not
	 * on every subsequent one, so an open viewer keeps its current zoom/pan across commits instead of
	 * snapping back to the default view each time.
	 */
	private void applySnapshot(GraphSnapshot snapshot, boolean showLabels) {
		assert viewer != null && view != null;

		boolean firstRender = this.graph.getNodeCount() == 0;

		this.view.setVisible(false);
		try {
			this.viewer.disableAutoLayout();

			this.graph.removeSink(this.layout);
			this.layout.removeAttributeSink(this.graph);
			this.layout.clear();
			this.graph.clear();

			if (firstRender) {
				this.view.getCamera().resetView();
			}

			this.graph.setAttribute("ui.quality");
			this.graph.setAttribute("ui.antialias");

			this.maxSuccessorsCount = snapshot.maxSuccessorsCount;

			// built alongside the graph and swapped into the volatile field in one shot below,
			// rather than relying on GraphStream to carry these as custom attributes across into
			// the GraphicElements the hover mouse manager sees - those live in a separate graph
			// mirrored across a thread boundary (GRAPH_IN_ANOTHER_THREAD), and there's no
			// guarantee a non-"ui."-namespaced attribute actually survives that mirror
			Map<String, HoverInfo> newHoverInfoById = new HashMap<>();

			for (NodeSnapshot nodeSnapshot : snapshot.nodes) {
				Node graphNode = this.graph.addNode(nodeSnapshot.id);
				if (nodeSnapshot.assocId != null) {
					graphNode.setAttribute(ASSOC_ID_ATTRIBUTE, nodeSnapshot.assocId);
				}
				if (nodeSnapshot.label != null) {
					graphNode.setAttribute("label", nodeSnapshot.label);
				}
				if (nodeSnapshot.successorsCount != null) {
					graphNode.setAttribute(SUCCESSOR_COUNT_ATTRIBUTE, nodeSnapshot.successorsCount);
				}
				newHoverInfoById.put(nodeSnapshot.id,
						new HoverInfo(nodeSnapshot.hoverText, nodeSnapshot.successorsCount));
			}
			this.hoverInfoById = newHoverInfoById;

			for (EdgeSnapshot edgeSnapshot : snapshot.edges) {
				this.graph.addEdge(edgeSnapshot.id, edgeSnapshot.sourceId, edgeSnapshot.targetId, true);
			}

			this.updateNodesAndEdgesStyles();
			this.updateGraphStylehseet(showLabels);


			this.graph.addSink(this.layout);
			this.layout.addAttributeSink(this.graph);

			this.viewer.enableAutoLayout(this.layout);
		} finally {
			this.view.setVisible(true);
		}
	}


	private static final int CHILD_COUNT_LIMIT_MAX = 1000;
	private static final int DEPTH_LIMIT_MAX = 50;
	private static final int DEFAULT_CHILD_COUNT_LIMIT = 20;
	private static final int DEFAULT_DEPTH_LIMIT = 10;
	private static final boolean DEFAULT_SHOW_LABELS = true;
	private static final int MAX_SIZE = 100;
	private static final int MIN_SIZE = 30;
	private static final int DEFAULT_SIZE = 20;
	private static final String SUCCESSOR_COUNT_ATTRIBUTE = "artifactsCount";
	private static final String ASSOC_ID_ATTRIBUTE = "assocId";
	private static final long HOVER_DELAY_MS = 200;
	private static final double HOVER_OVERLAY_OFFSET = 12;

	private int maxSuccessorsCount = 0;

	/** Plain-data node/edge lists built by {@link #buildSnapshot()} - safe to construct off the FX/Swing threads. */
	private static final class GraphSnapshot {
		final List<NodeSnapshot> nodes = new ArrayList<>();
		final List<EdgeSnapshot> edges = new ArrayList<>();
		int maxSuccessorsCount = 0;
		private int nextArtifactId = 0;
	}

	private static final class NodeSnapshot {
		final String id;
		String assocId;
		String label;
		/** Fuller text for the hover overlay - unlike {@link #label}, set for every artifact node, not just files/directories. */
		String hoverText;
		Integer successorsCount;

		NodeSnapshot(String id) {
			this.id = id;
		}
	}

	private static final class EdgeSnapshot {
		final String id;
		final String sourceId;
		final String targetId;

		EdgeSnapshot(String sourceId, String targetId) {
			this.id = sourceId + "-" + targetId;
			this.sourceId = sourceId;
			this.targetId = targetId;
		}
	}

	private void groupArtifactsByAssocRec(at.jku.isse.ecco.tree.Node eccoNode, Map<Association, Integer> groupMap) {
		for (at.jku.isse.ecco.tree.Node eccoChildNode : eccoNode.getChildren()) {
			// isUnique() check matches Trees.countArtifacts() exactly (used both by regular nodes,
			// above, and the dropped-children summary path below) - without it, a shared/non-unique
			// artifact got counted here but not there, inflating this specific kind of summary node's
			// size relative to every other node in the same view.
			if (eccoChildNode.getArtifact() != null && eccoChildNode.isUnique()) {
				Association childContainingAssociation = eccoChildNode.getArtifact().getContainingNode().getContainingAssociation();
				if (childContainingAssociation != null) {
					if (groupMap.containsKey(childContainingAssociation)) {
						int groupCount = groupMap.get(childContainingAssociation);
						groupCount++;
						groupMap.put(childContainingAssociation, groupCount);
					} else {
						groupMap.put(childContainingAssociation, 1);
					}
				}
			}
			this.groupArtifactsByAssocRec(eccoChildNode, groupMap);
		}
	}

	private String traverseSnapshot(at.jku.isse.ecco.tree.Node eccoNode, int depth, GraphSnapshot snapshot) {
		String nodeId = null;
		if (eccoNode.getArtifact() != null) {
			nodeId = String.valueOf(++snapshot.nextArtifactId);
			NodeSnapshot nodeSnapshot = new NodeSnapshot(nodeId);
			nodeSnapshot.assocId = eccoNode.getArtifact().getContainingNode().getContainingAssociation().getId();
			snapshot.nodes.add(nodeSnapshot);

			if (eccoNode.getArtifact().getData() instanceof PluginArtifactData) {
				nodeSnapshot.label = ((PluginArtifactData) eccoNode.getArtifact().getData()).getPath().toString();
			} else if (eccoNode.getArtifact().getData() instanceof DirectoryArtifactData) {
				nodeSnapshot.label = ((DirectoryArtifactData) eccoNode.getArtifact().getData()).getPath().toString();
			}
			// the on-canvas label above is deliberately limited to files/directories to avoid
			// cluttering the graph, but the hover overlay has room to show the real content of
			// any node - every ArtifactData implementation (line, token, function, ...) has a
			// meaningful toString(), e.g. a source line's actual text
			nodeSnapshot.hoverText = nodeSnapshot.label != null ? nodeSnapshot.label : eccoNode.getArtifact().getData().toString();

			// same subtree-size sizing {@link #addSummaryNode} already gives its collapsed nodes,
			// extended to every regular node too - a file/directory with a large subtree renders
			// bigger than a single leaf line/token, same "count -> size" idea as the summary nodes
			// (updateNodesAndEdgesStyles() picks this up automatically via SUCCESSOR_COUNT_ATTRIBUTE).
			nodeSnapshot.successorsCount = eccoNode.countArtifacts();
			if (snapshot.maxSuccessorsCount < nodeSnapshot.successorsCount) {
				snapshot.maxSuccessorsCount = nodeSnapshot.successorsCount;
			}
		}

		List<? extends at.jku.isse.ecco.tree.Node> children = eccoNode.getChildren();

		if (depth >= this.depthLimit) {
			// gone too deep to keep expanding - collapse the entire remaining subtree into one
			// summary node per association instead of continuing to recurse
			if (nodeId != null && !children.isEmpty()) {
				Map<Association, Integer> groupMap = new HashMap<>();
				this.groupArtifactsByAssocRec(eccoNode, groupMap);
				for (Map.Entry<Association, Integer> entry : groupMap.entrySet()) {
					this.addSummaryNode(nodeId, entry.getKey().getId(), "[" + entry.getValue() + "]", entry.getValue(), snapshot);
				}
			}
		} else if (children.size() > this.childCountLimit) {
			// too many children to show individually - keep the childCountLimit children with the
			// most content (by subtree artifact count) and expand those normally, folding the
			// smaller remainder into a single combined summary node rather than hiding them
			// without a trace
			List<at.jku.isse.ecco.tree.Node> sortedChildren = new ArrayList<>(children);
			sortedChildren.sort(Comparator.comparingInt(at.jku.isse.ecco.tree.Node::countArtifacts).reversed());

			List<at.jku.isse.ecco.tree.Node> kept = sortedChildren.subList(0, this.childCountLimit);
			List<at.jku.isse.ecco.tree.Node> dropped = sortedChildren.subList(this.childCountLimit, sortedChildren.size());

			for (at.jku.isse.ecco.tree.Node eccoChildNode : kept) {
				String childNodeId = this.traverseSnapshot(eccoChildNode, depth + 1, snapshot);
				if (childNodeId != null && nodeId != null) {
					snapshot.edges.add(new EdgeSnapshot(nodeId, childNodeId));
				}
			}

			if (nodeId != null && !dropped.isEmpty()) {
				int droppedArtifactCount = dropped.stream().mapToInt(at.jku.isse.ecco.tree.Node::countArtifacts).sum();
				this.addSummaryNode(nodeId, null, "[+" + dropped.size() + " smaller, " + droppedArtifactCount + "]", droppedArtifactCount, snapshot);
			}
		} else {
			for (at.jku.isse.ecco.tree.Node eccoChildNode : children) {
				String childNodeId = this.traverseSnapshot(eccoChildNode, depth + 1, snapshot);
				if (childNodeId != null && nodeId != null) {
					snapshot.edges.add(new EdgeSnapshot(nodeId, childNodeId));
				}
			}
		}

		return nodeId;
	}

	/** Adds a synthetic "collapsed" node (grouped-by-association summary, or dropped-children summary) as a child of {@code parentId}. */
	private void addSummaryNode(String parentId, String assocId, String label, int successorsCount, GraphSnapshot snapshot) {
		String summaryId = String.valueOf(++snapshot.nextArtifactId);
		NodeSnapshot summaryNode = new NodeSnapshot(summaryId);
		summaryNode.label = label;
		summaryNode.hoverText = label;
		summaryNode.successorsCount = successorsCount;
		summaryNode.assocId = assocId;
		snapshot.nodes.add(summaryNode);

		if (snapshot.maxSuccessorsCount < successorsCount)
			snapshot.maxSuccessorsCount = successorsCount;

		snapshot.edges.add(new EdgeSnapshot(parentId, summaryId));
	}

	/**
	 * Called by the containing tab whenever it's selected or deselected, so this view can skip its
	 * (comparatively expensive, done once per commit - see {@link #statusChangedEvent}) snapshot
	 * work while nobody's looking at it. Catches up with a single fresh render when the tab becomes
	 * visible again, since commits that happened while hidden were never snapshotted.
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
	 * Fires on open/close AND, notably, after every commit too (unlike {@code commitsChangedEvent},
	 * which {@link EccoService} defines but never actually fires - so this is the only hook that
	 * makes "refresh automatically when a new commit happens" possible). Only (re)creates the native
	 * GraphStream viewer the first time the repository becomes initialized; a later call (e.g. from
	 * a commit) just queues a re-render into the already-open viewer instead of tearing it down and
	 * recreating it every time. Skips the snapshot and render entirely while {@link #tabVisible} is
	 * false - {@link #setTabVisible} catches up with a single fresh render once the tab is shown
	 * again, rather than this method doing the (wasted, invisible) work for every commit in between.
	 * <p>
	 * When visible, the snapshot is built synchronously, right here, on whatever thread fired this
	 * event - e.g. the commit thread itself, still inside that specific {@code commit()} call -
	 * rather than deferred like the rest of this method. Committing several folders in one session
	 * fires this once per folder in a tight loop with nothing throttling it; if the snapshot were
	 * built lazily (e.g. inside the {@code Platform.runLater} below, or worse, inside the render task
	 * on the Swing thread), by the time any of that code actually ran the loop would typically have
	 * already raced ahead through every remaining commit, so EVERY queued render would end up reading
	 * the same final repository state and the view would visibly jump straight to it instead of
	 * growing one commit at a time. Capturing the snapshot here - before returning control to the
	 * caller, i.e. before the loop can advance to the next commit - is what makes each queued render
	 * distinct.
	 */
	@Override
	public void statusChangedEvent(EccoService service) {
		if (service.isInitialized()) {
			if (!this.tabVisible) {
				Platform.runLater(() -> this.setDisable(false));
				return;
			}
			GraphSnapshot snapshot = this.buildSnapshot();
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
