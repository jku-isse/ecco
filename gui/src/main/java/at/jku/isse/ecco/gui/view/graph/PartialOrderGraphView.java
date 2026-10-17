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
	/** The first element of each drawn block in what is shown (none for other variants), by its graph node's id. */
	private volatile Map<String, at.jku.isse.ecco.tree.Node> firstById = Map.of();
	/** The ids of the drawn blocks where variants differ, top to bottom - see {@link #nextBranch}. */
	private volatile List<String> branchIds = List.of();
	private volatile Map<String, double[]> placeById = Map.of();
	private volatile double layers = 1;
	private int branch = -1;
	/** Whether the blocks only other variants have are shown as such (else folded, an outline); null: by itself, see {@link #blocksOf}. */
	private volatile Boolean showOtherVariants;
	private final javafx.scene.control.CheckBox otherVariants = new javafx.scene.control.CheckBox("Other variants");
	/** Draws the graph shown last again - with other variants or not. */
	private volatile Runnable redraw;
	/** Told the element a chosen block begins with - the detail view shows it in the code. */
	private java.util.function.Consumer<at.jku.isse.ecco.tree.Node> onBlockChosen = node -> { };

	static final String HOVER_HINT = "Point at a block to see what it holds.";
	static final String LEGEND = "Top to bottom, in order. Green: start, red: end, orange: where alternatives part or meet, "
			+ "grey: only in other variants. Numbers: lines (or places) in what is shown, (n): how many a block holds. "
			+ "Click a block to see it in the code. Scroll to zoom, drag to move, double-click to see all.";
	/** Layers on the screen at most for the labels to show: a graph of thousands of blocks shows them zoomed in. */
	static final int LABELLED_LAYERS = 60;
	/** Layers a graph too tall to read fitted shows at first: its top. */
	static final int START_LAYERS = 40;

	public PartialOrderGraphView() {
		this.graph = new SingleGraph("PartialOrderGraph");

		Label legend = new Label(LEGEND);
		legend.setWrapText(true);
		legend.setPadding(new Insets(4, 8, 4, 8));
		legend.setStyle("-fx-font-size: 11px; -fx-font-style: italic;");
		// to the places where variants differ, one after another - in thousands of lines they were to be searched for
		javafx.scene.control.Button next = new javafx.scene.control.Button("Next branch");
		next.setTooltip(new javafx.scene.control.Tooltip("Go to the next place where variants order things differently"));
		next.setOnAction(e -> this.nextBranch());
		javafx.scene.control.Button fit = new javafx.scene.control.Button("Fit");
		fit.setTooltip(new javafx.scene.control.Tooltip("Show the whole graph"));
		fit.setOnAction(e -> {
			if (view != null)
				view.getCamera().resetView();
		});
		// a Git import's file holds every line it ever had: the lines shown, an outline, with the rest folded
		this.otherVariants.setTooltip(new javafx.scene.control.Tooltip("Show what only other variants have as blocks of their own; "
				+ "else folded between the blocks shown. Off by itself when other variants have more blocks than what is shown."));
		this.otherVariants.setOnAction(e -> {
			this.showOtherVariants = this.otherVariants.isSelected();
			Runnable redraw = this.redraw;
			if (redraw != null)
				redraw.run();
		});
		javafx.scene.layout.HBox bar = new javafx.scene.layout.HBox(8, next, fit, this.otherVariants);
		bar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
		bar.setPadding(new Insets(0, 8, 4, 8));
		this.setTop(new javafx.scene.layout.VBox(legend, bar));
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
		this.showGraph(pog, shownNode, (java.util.function.Supplier<at.jku.isse.ecco.repository.Repository>) null, lock);
	}

	/**
	 * As above, with the [repository] whose commits tell where the alternatives' lines come from
	 * when no feature does (may be null: then the conditions only).
	 */
	public void showGraph(PartialOrderGraph pog, at.jku.isse.ecco.tree.Node shownNode, at.jku.isse.ecco.repository.Repository repository, Object lock) {
		this.showGraph(pog, shownNode, repository == null ? null : (java.util.function.Supplier<at.jku.isse.ecco.repository.Repository>) () -> repository, lock);
	}

	/**
	 * As above, the repository got by [repository] in the background, holding [lock] - got on the FX
	 * thread, it waited there for a running commit, which holds the lock (may be null; it may give null).
	 */
	public void showGraph(PartialOrderGraph pog, at.jku.isse.ecco.tree.Node shownNode, java.util.function.Supplier<at.jku.isse.ecco.repository.Repository> repository, Object lock) {
		if (null == viewer) {
			initView();
		}

		this.redraw = () -> this.showGraph(pog, shownNode, repository, lock);
		setCursor(Cursor.WAIT);
		long request = this.requests.incrementAndGet();
		Boolean others = this.showOtherVariants;
		Thread th = new Thread(() -> {
			Snapshot snapshot;
			synchronized (lock) {
				at.jku.isse.ecco.repository.Repository origins = repository == null ? null : repository.get();
				snapshot = blocksOf(pog, shownNode, origins == null ? PartialOrderGraphView::conditionOrNotTraced : new ArtifactOrigins(origins)::describe, others);
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
	record Snapshot(List<String> labels, List<String> classes, List<double[]> places, List<String> details, List<int[]> edges,
					List<at.jku.isse.ecco.tree.Node> firsts, boolean withOtherVariants) {
	}

	/** {@code pog} folded into blocks ({@link OrderRegions#collapse}), described by the children of {@code shownNode} (may be null). */
	static Snapshot blocksOf(PartialOrderGraph pog, at.jku.isse.ecco.tree.Node shownNode) {
		return blocksOf(pog, shownNode, PartialOrderGraphView::conditionOrNotTraced);
	}

	private static String conditionOrNotTraced(at.jku.isse.ecco.core.Association association) {
		String condition = ArtifactOrigins.condition(association);
		return condition != null ? condition : at.jku.isse.ecco.module.Condition.NOT_TRACED;
	}

	/** As above, [describe] telling where an association's elements come from - {@link ArtifactOrigins}. */
	static Snapshot blocksOf(PartialOrderGraph pog, at.jku.isse.ecco.tree.Node shownNode,
							 java.util.function.Function<at.jku.isse.ecco.core.Association, String> describe) {
		return blocksOf(pog, shownNode, describe, true);
	}

	/**
	 * As above; with [withOtherVariants] false the blocks only other variants have are folded between
	 * those shown ({@link OrderRegions#outline}), with null so when they are more than those shown -
	 * a Git import's file, which holds every line it ever had (tinycc's tcc.c: 9,036 of 9,082 blocks).
	 */
	static Snapshot blocksOf(PartialOrderGraph pog, at.jku.isse.ecco.tree.Node shownNode,
							 java.util.function.Function<at.jku.isse.ecco.core.Association, String> describe, Boolean withOtherVariants) {
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
		long otherBlocks = collapsed.blocks().stream().filter(b -> b.kind() == OrderRegions.Kind.NODES && !b.shown()).count();
		boolean others = withOtherVariants != null ? withOtherVariants : otherBlocks <= collapsed.blocks().size() - otherBlocks;
		if (!others)
			collapsed = OrderRegions.outline(collapsed);
		// the alternatives - what only other variants have, and what lies beside them in a layer - say
		// which variants they come from; each association's condition once
		Map<Integer, Integer> perLayer = new HashMap<>();
		for (OrderRegions.Block block : collapsed.blocks())
			perLayer.merge(block.layer(), 1, Integer::sum);
		Map<at.jku.isse.ecco.core.Association, String> conditions = new IdentityHashMap<>();
		List<String> labels = new ArrayList<>(), classes = new ArrayList<>(), details = new ArrayList<>();
		List<double[]> places = new ArrayList<>();
		List<at.jku.isse.ecco.tree.Node> firsts = new ArrayList<>();
		for (OrderRegions.Block block : collapsed.blocks()) {
			firsts.add(block.kind() == OrderRegions.Kind.NODES ? childOf.get(block.first()) : null);
			List<String> origins = block.kind() == OrderRegions.Kind.OTHERS
					|| block.kind() == OrderRegions.Kind.NODES && (!block.shown() || perLayer.get(block.layer()) > 1)
					? origins(block, conditions, describe) : List.of();
			String label = label(block, childOf, placeOf, shownNode != null);
			if (!origins.isEmpty()) {
				String condition = origins.get(0).substring(0, origins.get(0).lastIndexOf(" (association "));
				// the commits that had it are in the details: in the label it was cut off
				if (condition.startsWith(at.jku.isse.ecco.module.Condition.NOT_TRACED))
					condition = at.jku.isse.ecco.module.Condition.NOT_TRACED;
				label += "  [" + (condition.length() <= ORIGIN_TEXT || condition.equals(at.jku.isse.ecco.module.Condition.NOT_TRACED) ? condition : short_(condition, ORIGIN_TEXT)) + (origins.size() > 1 ? "; +" + (origins.size() - 1) : "") + "]";
			}
			labels.add(label);
			classes.add(switch (block.kind()) {
				case START -> "start";
				case END -> "end";
				case NODES -> !block.shown() ? "other" : block.branch() ? "branch" : null;
				case OTHERS -> "other";
			});
			places.add(new double[]{block.column() * COLUMN_GAP, -block.layer() * LAYER_GAP});
			String text = details(block, childOf, placeOf, shownNode != null);
			for (int o = 0; o < origins.size(); o++)
				text += (o == 0 ? "\nFrom: " : "\n      ") + origins.get(o);
			details.add(text);
		}
		List<int[]> edges = new ArrayList<>();
		for (OrderRegions.Edge edge : collapsed.edges())
			edges.add(new int[]{edge.from(), edge.to()});
		return new Snapshot(labels, classes, places, details, edges, firsts, others);
	}

	/** Characters of an alternative's condition in its label. */
	static final int ORIGIN_TEXT = 30;
	/** Associations an alternative's details name at most. */
	static final int ORIGINS = 3;

	/**
	 * The variants [block]'s elements come from: for each association they are in, what [describe]
	 * says - its condition, or the commits with it ({@link ArtifactOrigins}) - "B (association
	 * 3f2a1c0d)", at most {@link #ORIGINS} and how many more.
	 */
	static List<String> origins(OrderRegions.Block block, Map<at.jku.isse.ecco.core.Association, String> conditions,
								java.util.function.Function<at.jku.isse.ecco.core.Association, String> describe) {
		Set<at.jku.isse.ecco.core.Association> associations = Collections.newSetFromMap(new IdentityHashMap<>());
		List<at.jku.isse.ecco.core.Association> ordered = new ArrayList<>();
		for (PartialOrderGraph.Node node : block.nodes()) {
			at.jku.isse.ecco.artifact.Artifact<?> artifact = node.getArtifact();
			at.jku.isse.ecco.core.Association association = artifact == null || artifact.getContainingNode() == null ? null
					: artifact.getContainingNode().getContainingAssociation();
			if (association != null && associations.add(association))
				ordered.add(association);
		}
		List<String> origins = new ArrayList<>();
		for (at.jku.isse.ecco.core.Association association : ordered.subList(0, Math.min(ORIGINS, ordered.size())))
			origins.add(conditions.computeIfAbsent(association, describe)
					+ " (association " + at.jku.isse.ecco.gui.TableColumns.shortIds(association.getId()) + ")");
		if (ordered.size() > ORIGINS)
			origins.add("and " + (ordered.size() - ORIGINS) + " more associations");
		return origins;
	}

	/** "12-340 (329)  first ... last", "other (3)  first ... last", "Start", "End". */
	static String label(OrderRegions.Block block, Map<PartialOrderGraph.Node, at.jku.isse.ecco.tree.Node> childOf,
						Map<at.jku.isse.ecco.tree.Node, Integer> placeOf, boolean placed) {
		if (block.kind() == OrderRegions.Kind.START)
			return "Start";
		if (block.kind() == OrderRegions.Kind.END)
			return "End";
		int size = block.nodes().size();
		if (block.kind() == OrderRegions.Kind.OTHERS)
			return "other variants (" + size + ")";
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
		if (block.kind() == OrderRegions.Kind.OTHERS) {
			text.append(size == 1 ? "1 element" : size + " elements").append(" only other variants have here, folded (Other variants shows them).")
					.append("\nFirst: ").append(short_(firstText(block, childOf), 160));
			if (size > 1)
				text.append("\nLast: ").append(short_(lastText(block, childOf), 160));
			return text.toString();
		}
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
			Map<String, at.jku.isse.ecco.tree.Node> newFirsts = new HashMap<>();
			Map<String, double[]> newPlaces = new HashMap<>();
			List<Integer> branches = new ArrayList<>();
			List<org.graphstream.graph.Node> gsNodes = new ArrayList<>();
			for (int i = 0; i < snapshot.labels().size(); i++) {
				org.graphstream.graph.Node gsNode = this.graph.addNode("N" + i);
				gsNode.setAttribute("label", snapshot.labels().get(i));
				double[] place = snapshot.places().get(i);
				gsNode.setAttribute("xyz", place[0], place[1], 0);
				if (snapshot.classes().get(i) != null)
					gsNode.setAttribute("ui.class", snapshot.classes().get(i));
				newDetails.put(gsNode.getId(), snapshot.details().get(i));
				if (snapshot.firsts().get(i) != null)
					newFirsts.put(gsNode.getId(), snapshot.firsts().get(i));
				newPlaces.put(gsNode.getId(), place);
				// where alternatives part or meet, and the alternatives themselves - side by side in a layer
				if ("branch".equals(snapshot.classes().get(i)) || "other".equals(snapshot.classes().get(i)) || besideOthers(snapshot, i))
					branches.add(i);
				gsNodes.add(gsNode);
			}
			// top to bottom, left to right
			branches.sort(Comparator.<Integer>comparingDouble(i -> -snapshot.places().get(i)[1]).thenComparingDouble(i -> snapshot.places().get(i)[0]));
			this.detailsById = newDetails;
			this.firstById = newFirsts;
			this.placeById = newPlaces;
			this.branchIds = branches.stream().map(i -> "N" + i).toList();
			double maxLayers = 1;
			for (double[] place : snapshot.places())
				maxLayers = Math.max(maxLayers, -place[1] / LAYER_GAP + 1);
			this.layers = maxLayers;
			for (int[] edge : snapshot.edges())
				this.graph.addEdge("E" + edge[0] + "-" + edge[1], gsNodes.get(edge[0]), gsNodes.get(edge[1]), true);

			// placed in layers: no layout runs (a spring layout coiled the runs and, for thousands of
			// nodes, never settled - redrawing on every step)
			view.getCamera().resetView();
			// too tall to read fitted - thousands of layers were a grey smear: its top, readable
			if (maxLayers > LABELLED_LAYERS) {
				view.getCamera().setViewPercent(START_LAYERS / maxLayers);
				// the start just inside the top, the column to the left of the middle: its labels go right
				view.getCamera().setViewCenter(START_LAYERS / 4.0 * LAYER_GAP, -(START_LAYERS / 2.0 - 1) * LAYER_GAP, 0);
			}
			boolean withOthers = snapshot.withOtherVariants();
			Platform.runLater(() -> this.otherVariants.setSelected(withOthers));

		} finally {
			Platform.runLater(() -> {
				setCursor(Cursor.DEFAULT);
				this.details.setText(HOVER_HINT);
				this.branch = -1;
			});
		}
	}

	/** Whether another block shares the layer of block [i]: they are alternatives. */
	private static boolean besideOthers(Snapshot snapshot, int i) {
		double y = snapshot.places().get(i)[1];
		for (int j = 0; j < snapshot.places().size(); j++)
			if (j != i && snapshot.places().get(j)[1] == y)
				return true;
		return false;
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
		// a click - not the end of a drag - on a block chooses it
		double[] pressed = new double[2];
		view.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e -> {
			pressed[0] = e.getX();
			pressed[1] = e.getY();
		});
		view.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_CLICKED, e -> {
			if (e.getClickCount() != 1 || Math.abs(e.getX() - pressed[0]) + Math.abs(e.getY() - pressed[1]) > 4)
				return;
			GraphicElement element = view.findGraphicElementAt(EnumSet.of(InteractiveElement.NODE), e.getX(), e.getY());
			if (element != null)
				this.choose(element.getId());
		});

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

	/** Called with the element a chosen block begins with, in what is shown. */
	public void setOnBlockChosen(java.util.function.Consumer<at.jku.isse.ecco.tree.Node> onBlockChosen) {
		this.onBlockChosen = onBlockChosen;
	}

	/**
	 * Chooses the drawn block [id]: hands its first element on, or - for one only other variants
	 * have, or the start or end - says why there is none. Package-visible for testing.
	 */
	void choose(String id) {
		at.jku.isse.ecco.tree.Node first = this.firstById.get(id);
		String text = this.detailsById.get(id);
		if (first != null) {
			this.details.setText(text);
			this.onBlockChosen.accept(first);
		} else if (text != null) {
			this.details.setText(text + (text.contains("only in other variants") ? "\nNot in the code shown: only other variants have it." : ""));
		}
	}

	/**
	 * Goes to the next place where variants differ - top to bottom, and round - and shows what it
	 * holds; returns its id, or null if there is none. Package-visible for testing.
	 */
	String nextBranch() {
		List<String> ids = this.branchIds;
		if (ids.isEmpty())
			return null;
		this.branch = (this.branch + 1) % ids.size();
		String id = ids.get(this.branch);
		this.details.setText(this.detailsById.get(id));
		double[] place = this.placeById.get(id);
		FxViewPanel view = this.view;
		if (view != null && place != null) {
			// some 30 layers around it
			view.getCamera().setViewPercent(Math.min(1.0, 30 / this.layers));
			view.getCamera().setViewCenter(place[0], place[1], 0);
		}
		return id;
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
			// the closed viewer's pipe stays registered with the graph and queues every later change
			// of it - a viewer made for each commit piled them up (GraphLayoutStopsTest)
			this.graph.clearSinks();
			view = null;
			viewer = null;
		}
	}

	/** The graph as drawn. Package-visible for testing. */
	Graph drawnGraph() {
		return this.graph;
	}

}
