package at.jku.isse.ecco.gui.view.graph;

import at.jku.isse.ecco.pog.OrderRegions;
import at.jku.isse.ecco.pog.PartialOrderGraph;
import at.jku.isse.ecco.tree.ArtifactDiagnostics;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import org.graphstream.graph.Graph;
import org.graphstream.graph.implementations.SingleGraph;
import org.graphstream.ui.fx_viewer.FxViewPanel;
import org.graphstream.ui.fx_viewer.FxViewer;
import org.graphstream.ui.fx_viewer.util.FxMouseOverMouseManager;
import org.graphstream.ui.graphicGraph.GraphicElement;
import org.graphstream.ui.javafx.FxGraphRenderer;
import org.graphstream.ui.view.Viewer;
import org.graphstream.ui.view.util.InteractiveElement;

import java.util.*;

/**
 * The order graph of an ordered node - in which order its children (a file's lines, say) may come,
 * as the commits so far say - drawn top to bottom with its straight runs folded into blocks
 * ({@link OrderRegions#collapse}): what is worth seeing are the places where variants order things
 * differently, the blocks side by side. It was a node per child, labelled with its artifact and an
 * internal number ("null [-]" for the start and end), laid out by a spring layout that coiled the
 * runs and, for a file of thousands of lines, never settled.
 */
public class PartialOrderGraphView extends BorderPane {

	/** Distances between the layers and the columns of the blocks, in graph units. */
	static final double LAYER_GAP = 1, COLUMN_GAP = 24;
	/** Characters of a child's text in a block's label. */
	static final int LABEL_TEXT = 32;

	private final Graph graph;
	// the latest graph asked for (showGraph); an older request is not drawn
	private final java.util.concurrent.atomic.AtomicLong requests = new java.util.concurrent.atomic.AtomicLong();
	// read by the drawing thread, set and cleared on the FX thread
	private volatile FxViewer viewer;
	private volatile FxViewPanel view;
	/** What a block holds, by its graph node's id - shown below the graph while the mouse is on it. */
	private volatile Map<String, String> detailsById = Map.of();
	private final Label details = new Label(HOVER_HINT);

	static final String HOVER_HINT = "Point at a block to see what it holds.";
	static final String LEGEND = "Top to bottom, in order. Green: start, red: end, orange: where alternatives part or meet, "
			+ "grey: only in other variants. Numbers: lines (or places) in what is shown, (n): how many a block holds. "
			+ "Scroll to zoom, drag to move, double-click to see all.";
	/** Layers on the screen at most for the labels to show: a graph of thousands of blocks shows them zoomed in. */
	static final int LABELLED_LAYERS = 60;

	public PartialOrderGraphView() {
		this.graph = new SingleGraph("PartialOrderGraph");

		Label legend = new Label(LEGEND);
		legend.setWrapText(true);
		legend.setPadding(new Insets(4, 8, 4, 8));
		legend.setStyle("-fx-font-size: 11px; -fx-font-style: italic;");
		this.setTop(legend);
		this.details.setWrapText(true);
		this.details.setPadding(new Insets(4, 8, 4, 8));
		this.details.setStyle("-fx-font-size: 11px;");
		this.setBottom(this.details);

		// in steps of a fraction, deep enough for thousands of layers (it stopped at a tenth)
		this.setOnScroll(event -> {
			if (null != view && event.getDeltaY() != 0) {
				double factor = event.getDeltaY() > 0 ? 1 / 1.15 : 1.15;
				view.getCamera().setViewPercent(Math.max(0.0005, Math.min(1.0, view.getCamera().getViewPercent() * factor)));
			}
		});
		double[] dragFrom = new double[2];
		this.setOnMousePressed(event -> {
			dragFrom[0] = event.getX();
			dragFrom[1] = event.getY();
		});
		this.setOnMouseDragged(event -> {
			if (null == view)
				return;
			double ratio = view.getCamera().getMetrics().ratioPx2Gu;
			if (ratio <= 0)
				return;
			org.graphstream.ui.geom.Point3 center = view.getCamera().getViewCenter();
			view.getCamera().setViewCenter(center.x - (event.getX() - dragFrom[0]) / ratio, center.y + (event.getY() - dragFrom[1]) / ratio, center.z);
			dragFrom[0] = event.getX();
			dragFrom[1] = event.getY();
		});
		this.setOnMouseClicked(event -> {
			if (null != view && event.getClickCount() == 2)
				view.getCamera().resetView();
		});
	}

	/** As {@link #showGraph(PartialOrderGraph, at.jku.isse.ecco.tree.Node, Object)} without a node shown: every block is shown. */
	public void showGraph(PartialOrderGraph pog, Object lock) {
		this.showGraph(pog, null, lock);
	}

	/**
	 * Shows {@code pog}, the order graph of {@code shownNode} (whose children tell which blocks are
	 * shown, and their lines; null: all, without), read while holding {@code lock} - the service,
	 * whose commits and other writes change order graphs while holding it: read without it, a commit
	 * running at the same time changed a node's next nodes while they were walked
	 * (ConcurrentModificationException).
	 */
	public void showGraph(PartialOrderGraph pog, at.jku.isse.ecco.tree.Node shownNode, Object lock) {
		if (null == viewer) {
			initView();
		}

		setCursor(Cursor.WAIT);
		long request = this.requests.incrementAndGet();
		Thread th = new Thread(() -> {
			Snapshot snapshot;
			synchronized (lock) {
				snapshot = blocksOf(pog, shownNode);
			}
			// one rebuild at a time: selecting another node before the previous rebuild finished used
			// to start a second thread clearing and filling the same (not thread-safe) graph
			synchronized (this.graph) {
				// waiting for the lock (a whole commit) a newer graph may have been asked for - its
				// thread can get the lock first - or the view closed: then this one is not drawn
				if (request != this.requests.get() || this.viewer == null) {
					Platform.runLater(() -> setCursor(Cursor.DEFAULT));
					return;
				}
				this.rebuildGraph(snapshot);
			}
		});
		th.setDaemon(true);
		th.start();
	}

	/**
	 * The blocks of an order graph - their labels, styles, places and what they hold - and the edges
	 * between them, as indices into them.
	 */
	record Snapshot(List<String> labels, List<String> classes, List<double[]> places, List<String> details, List<int[]> edges) {
	}

	/** {@code pog} folded into blocks ({@link OrderRegions#collapse}), described by the children of {@code shownNode} (may be null). */
	static Snapshot blocksOf(PartialOrderGraph pog, at.jku.isse.ecco.tree.Node shownNode) {
		Map<PartialOrderGraph.Node, at.jku.isse.ecco.tree.Node> childOf = new IdentityHashMap<>();
		Map<at.jku.isse.ecco.tree.Node, Integer> placeOf = new IdentityHashMap<>();
		if (shownNode != null) {
			pog.matchChildren(shownNode).forEach((child, node) -> childOf.put(node, child));
			List<? extends at.jku.isse.ecco.tree.Node> children = shownNode.getChildren();
			for (int i = 0; i < children.size(); i++)
				placeOf.put(children.get(i), i);
		}
		Set<PartialOrderGraph.Node> shown = Collections.newSetFromMap(new IdentityHashMap<>());
		if (shownNode != null)
			shown.addAll(childOf.keySet());
		else
			shown.addAll(pog.collectNodes());

		OrderRegions.Collapsed collapsed = OrderRegions.collapse(pog, shown);
		List<String> labels = new ArrayList<>(), classes = new ArrayList<>(), details = new ArrayList<>();
		List<double[]> places = new ArrayList<>();
		for (OrderRegions.Block block : collapsed.blocks()) {
			labels.add(label(block, childOf, placeOf, shownNode != null));
			classes.add(switch (block.kind()) {
				case START -> "start";
				case END -> "end";
				case NODES -> !block.shown() ? "other" : block.branch() ? "branch" : null;
			});
			places.add(new double[]{block.column() * COLUMN_GAP, -block.layer() * LAYER_GAP});
			details.add(details(block, childOf, placeOf, shownNode != null));
		}
		List<int[]> edges = new ArrayList<>();
		for (OrderRegions.Edge edge : collapsed.edges())
			edges.add(new int[]{edge.from(), edge.to()});
		return new Snapshot(labels, classes, places, details, edges);
	}

	/** "12-340 (329)  first ... last", "other (3)  first ... last", "Start", "End". */
	static String label(OrderRegions.Block block, Map<PartialOrderGraph.Node, at.jku.isse.ecco.tree.Node> childOf,
						Map<at.jku.isse.ecco.tree.Node, Integer> placeOf, boolean placed) {
		if (block.kind() == OrderRegions.Kind.START)
			return "Start";
		if (block.kind() == OrderRegions.Kind.END)
			return "End";
		int size = block.nodes().size();
		String where = !block.shown() ? "other" : placed ? places(block, childOf, placeOf) : "";
		String count = size > 1 ? " (" + size + ")" : "";
		String text = size == 1 ? short_(firstText(block, childOf), 2 * LABEL_TEXT)
				: short_(firstText(block, childOf), LABEL_TEXT) + " … " + short_(lastText(block, childOf), LABEL_TEXT);
		return (where + count + "  " + text).trim();
	}

	/** What the details line says about a block. */
	static String details(OrderRegions.Block block, Map<PartialOrderGraph.Node, at.jku.isse.ecco.tree.Node> childOf,
						  Map<at.jku.isse.ecco.tree.Node, Integer> placeOf, boolean placed) {
		if (block.kind() == OrderRegions.Kind.START)
			return "Start: what comes first follows from here.";
		if (block.kind() == OrderRegions.Kind.END)
			return "End: what comes last leads here.";
		int size = block.nodes().size();
		StringBuilder text = new StringBuilder();
		text.append(size == 1 ? "1 element" : size + " elements in this order");
		if (!block.shown())
			text.append(", only in other variants");
		else if (placed)
			text.append(", at ").append(places(block, childOf, placeOf)).append(" of what is shown");
		if (block.branch())
			text.append("; alternatives part or meet here");
		text.append(".\nFirst: ").append(short_(firstText(block, childOf), 160));
		if (size > 1)
			text.append("\nLast: ").append(short_(lastText(block, childOf), 160));
		text.append("\nOrder numbers: ").append(block.first().getSequenceNumber());
		if (size > 1)
			text.append("-").append(block.last().getSequenceNumber());
		return text.toString();
	}

	/**
	 * The block's places among the children shown, "12-340" - a file's lines, for one of single
	 * lines. Not the lines the adapter recorded: those are of the variant a line was committed with.
	 */
	private static String places(OrderRegions.Block block, Map<PartialOrderGraph.Node, at.jku.isse.ecco.tree.Node> childOf,
								 Map<at.jku.isse.ecco.tree.Node, Integer> placeOf) {
		at.jku.isse.ecco.tree.Node first = childOf.get(block.first()), last = childOf.get(block.last());
		if (first == null || last == null)
			return "";
		int a = placeOf.getOrDefault(first, -1) + 1, b = placeOf.getOrDefault(last, -1) + 1;
		return a == b ? String.valueOf(a) : a + "-" + b;
	}

	/** The text of the block's first element that is not blank - a run of code often begins with an empty line. */
	private static String firstText(OrderRegions.Block block, Map<PartialOrderGraph.Node, at.jku.isse.ecco.tree.Node> childOf) {
		for (PartialOrderGraph.Node node : block.nodes()) {
			String text = text(node, childOf);
			if (!text.isEmpty())
				return text;
		}
		return "(blank)";
	}

	/** The text of the block's last element that is not blank. */
	private static String lastText(OrderRegions.Block block, Map<PartialOrderGraph.Node, at.jku.isse.ecco.tree.Node> childOf) {
		for (int i = block.nodes().size() - 1; i >= 0; i--) {
			String text = text(block.nodes().get(i), childOf);
			if (!text.isEmpty())
				return text;
		}
		return "(blank)";
	}

	private static String text(PartialOrderGraph.Node node, Map<PartialOrderGraph.Node, at.jku.isse.ecco.tree.Node> childOf) {
		at.jku.isse.ecco.tree.Node child = childOf.get(node);
		String text = child != null ? ArtifactDiagnostics.textOf(child) : String.valueOf(node.getArtifact());
		return text.replaceAll("\\s+", " ").trim();
	}

	private static String short_(String text, int max) {
		return text.length() <= max ? text : text.substring(0, max - 1) + "…";
	}

	private void rebuildGraph(Snapshot snapshot) {
		// the view may be closed while this draws (closeGraph clears both on the FX thread): drawing
		// goes on with the ones it started with, which are discarded anyway - read twice, the field
		// was null the second time
		FxViewer viewer = this.viewer;
		FxViewPanel view = this.view;
		if (viewer == null || view == null) {
			Platform.runLater(() -> setCursor(Cursor.DEFAULT));
			return;
		}
		try {
			this.graph.clear();
			this.graph.setStrict(false);
			this.graph.setAttribute("ui.quality");
			this.graph.setAttribute("ui.antialias");
			this.graph.setAttribute("ui.stylesheet",
					"node { size: 12px; fill-color: #1565c0; text-alignment: at-right; text-offset: 6px, 0px; "
							+ "text-background-mode: plain; text-background-color: #ffffffcc; text-size: 12; } "
							+ "node.start { fill-color: #2e7d32; size: 16px; } node.end { fill-color: #c62828; size: 16px; } "
							+ "node.branch { fill-color: #f9a825; size: 14px; } node.other { fill-color: #9e9e9e; } "
							+ "edge { fill-color: #9e9e9e; arrow-size: 6px, 4px; }"
							+ labelVisibility(snapshot));

			Map<String, String> newDetails = new HashMap<>();
			List<org.graphstream.graph.Node> gsNodes = new ArrayList<>();
			for (int i = 0; i < snapshot.labels().size(); i++) {
				org.graphstream.graph.Node gsNode = this.graph.addNode("N" + i);
				gsNode.setAttribute("label", snapshot.labels().get(i));
				double[] place = snapshot.places().get(i);
				gsNode.setAttribute("xyz", place[0], place[1], 0);
				if (snapshot.classes().get(i) != null)
					gsNode.setAttribute("ui.class", snapshot.classes().get(i));
				newDetails.put(gsNode.getId(), snapshot.details().get(i));
				gsNodes.add(gsNode);
			}
			this.detailsById = newDetails;
			for (int[] edge : snapshot.edges())
				this.graph.addEdge("E" + edge[0] + "-" + edge[1], gsNodes.get(edge[0]), gsNodes.get(edge[1]), true);

			// placed in layers: no layout runs (a spring layout coiled the runs and, for thousands of
			// nodes, never settled - redrawing on every step)
			view.getCamera().resetView();

		} finally {
			Platform.runLater(() -> {
				setCursor(Cursor.DEFAULT);
				this.details.setText(HOVER_HINT);
			});
		}
	}

	/** For a graph of more layers than {@link #LABELLED_LAYERS}: the labels only once zoomed in that far. */
	static String labelVisibility(Snapshot snapshot) {
		double layers = 1;
		for (double[] place : snapshot.places())
			layers = Math.max(layers, -place[1] / LAYER_GAP + 1);
		if (layers <= LABELLED_LAYERS)
			return "";
		return " node { text-visibility-mode: under-zoom; text-visibility: " + (LABELLED_LAYERS / layers) + "; }";
	}

	private void initView() {
		viewer = new FxViewer(graph, Viewer.ThreadingModel.GRAPH_IN_ANOTHER_THREAD);
		view = (FxViewPanel)  viewer.addDefaultView(false, new FxGraphRenderer());
		view.setMouseManager(new DetailsMouseManager());

		setCenter(view);
	}

	/** Shows what the block under the mouse holds, below the graph. */
	private class DetailsMouseManager extends FxMouseOverMouseManager {
		DetailsMouseManager() {
			super(EnumSet.of(InteractiveElement.NODE), 150);
		}

		@Override
		protected void mouseOverElement(GraphicElement element) {
			super.mouseOverElement(element);
			String text = PartialOrderGraphView.this.detailsById.get(element.getId());
			Platform.runLater(() -> PartialOrderGraphView.this.details.setText(text != null ? text : HOVER_HINT));
		}
	}

	/** The details line below the graph. Package-visible for testing. */
	String detailsText() {
		return this.details.getText();
	}

	/** What a drawn block holds, by its graph node's id. Package-visible for testing. */
	Map<String, String> drawnDetails() {
		return this.detailsById;
	}

	/**
	 * Stops a layout - none runs since the blocks are placed in layers; kept for callers that stop
	 * the graph when it is no longer shown.
	 */
	public void stopLayout() {
		FxViewer viewer = this.viewer;
		if (viewer != null)
			viewer.disableAutoLayout();
	}

	public void closeGraph() {
		setCenter(null);
		this.requests.incrementAndGet(); // a graph still being read is not drawn

		if (null != viewer) {
			try {
				viewer.close();
			} catch (Exception ignored) {
				// GraphStream's FxGraphRenderer can NPE while tearing down a view that was
				// created but never rendered yet - harmless here since the viewer/view are
				// discarded immediately after anyway.
			}
			view = null;
			viewer = null;
		}
	}

	/** The graph as drawn. Package-visible for testing. */
	Graph drawnGraph() {
		return this.graph;
	}

}
