package at.jku.isse.ecco.gui.view.graph;

import at.jku.isse.ecco.pog.PartialOrderGraph;
import javafx.application.Platform;
import javafx.scene.Cursor;
import javafx.scene.layout.BorderPane;
import org.graphstream.graph.Graph;
import org.graphstream.graph.implementations.SingleGraph;
import org.graphstream.ui.javafx.FxGraphRenderer;
import org.graphstream.ui.layout.Layout;
import org.graphstream.ui.layout.springbox.implementations.SpringBox;
import org.graphstream.ui.fx_viewer.FxViewPanel;
import org.graphstream.ui.fx_viewer.FxViewer;
import org.graphstream.ui.view.Viewer;
import java.util.*;

public class PartialOrderGraphView extends BorderPane {

	private final Graph graph;
	private final Layout layout;
	private FxViewer viewer;
	private FxViewPanel view;

	public PartialOrderGraphView() {
		System.setProperty("org.graphstream.ui.renderer", "org.graphstream.ui.j2dviewer.J2DGraphRenderer");

		this.graph = new SingleGraph("PartialOrderGraph");

		this.layout = new SpringBox(false);
		this.graph.addSink(layout);
		layout.addAttributeSink(this.graph);

		this.setOnScroll(event -> {
			if (null != view) {
				view.getCamera().setViewPercent(Math.max(0.1, Math.min(1.0,
						view.getCamera().getViewPercent() - 0.05 * event.getDeltaY() / event.getMultiplierY())));
			}
		});
	}

	/**
	 * Shows {@code pog}, read while holding {@code lock} - the service, whose commits and other
	 * writes change order graphs while holding it: read without it, a commit running at the same
	 * time changed a node's next nodes while they were walked (ConcurrentModificationException).
	 */
	public void showGraph(PartialOrderGraph pog, Object lock) {
		if (null == viewer) {
			initView();
		}

		setCursor(Cursor.WAIT);
		Thread th = new Thread(() -> {
			Snapshot snapshot;
			synchronized (lock) {
				snapshot = snapshot(pog);
			}
			// one rebuild at a time: selecting another node before the previous rebuild finished used
			// to start a second thread clearing and filling the same (not thread-safe) graph
			synchronized (this.graph) {
				this.rebuildGraph(snapshot);
			}
		});
		th.start();
	}

	/** The nodes of an order graph - their labels and styles - and its edges, as indices into them. */
	record Snapshot(List<String> labels, List<String> classes, List<int[]> edges) {
	}

	/**
	 * Every node of {@code pog} once, from its head, and every edge once. The walk used to recurse
	 * into a node's next nodes each time it came to the node, from each of its previous nodes -
	 * exponential in the graph's open branches - and once per line deep.
	 */
	static Snapshot snapshot(PartialOrderGraph pog) {
		Map<PartialOrderGraph.Node, Integer> index = new IdentityHashMap<>();
		List<PartialOrderGraph.Node> nodes = new ArrayList<>();
		List<String> labels = new ArrayList<>();
		List<String> classes = new ArrayList<>();
		List<int[]> edges = new ArrayList<>();
		Deque<PartialOrderGraph.Node> pending = new ArrayDeque<>();
		index.put(pog.getHead(), 0);
		nodes.add(pog.getHead());
		pending.push(pog.getHead());
		while (!pending.isEmpty()) {
			PartialOrderGraph.Node node = pending.pop();
			int from = index.get(node);
			for (PartialOrderGraph.Node next : List.copyOf(node.getNext())) {
				Integer to = index.get(next);
				if (to == null) {
					to = nodes.size();
					index.put(next, to);
					nodes.add(next);
					pending.push(next);
				}
				edges.add(new int[]{from, to});
			}
		}
		for (PartialOrderGraph.Node node : nodes) {
			labels.add(node.getArtifact() + " [" + (node.getArtifact() != null ? node.getArtifact().getSequenceNumber() : "-") + "]");
			classes.add(node.getNext().isEmpty() ? "end" : node.getPrevious().isEmpty() ? "start" : null);
		}
		return new Snapshot(labels, classes, edges);
	}

	private void rebuildGraph(Snapshot snapshot) {
		try {
			this.viewer.disableAutoLayout();
			this.graph.removeSink(this.layout);
			this.layout.removeAttributeSink(this.graph);
			this.layout.clear();

			this.graph.clear();
			this.graph.setStrict(false);
			this.graph.setAttribute("ui.quality");
			this.graph.setAttribute("ui.antialias");
			this.graph.setAttribute("ui.stylesheet",
					" node.start { fill-color: green; size: 20px; } node.end { fill-color: red; size: 20px; } node {text-alignment:above;text-background-mode:plain;}");

			List<org.graphstream.graph.Node> gsNodes = new ArrayList<>();
			for (int i = 0; i < snapshot.labels().size(); i++) {
				org.graphstream.graph.Node gsNode = this.graph.addNode("N" + i);
				gsNode.setAttribute("label", snapshot.labels().get(i));
				if (snapshot.classes().get(i) != null)
					gsNode.setAttribute("ui.class", snapshot.classes().get(i));
				gsNodes.add(gsNode);
			}
			for (int[] edge : snapshot.edges())
				this.graph.addEdge("E" + edge[0] + "-" + edge[1], gsNodes.get(edge[0]), gsNodes.get(edge[1]), true);

			this.graph.addSink(this.layout);
			this.layout.addAttributeSink(this.graph);
			this.viewer.enableAutoLayout(this.layout);

			this.view.getCamera().resetView();

		} finally {
			Platform.runLater(() -> setCursor(Cursor.DEFAULT));
		}
	}

	private void initView() {
		viewer = new FxViewer(graph, Viewer.ThreadingModel.GRAPH_IN_ANOTHER_THREAD);
		view = (FxViewPanel)  viewer.addDefaultView(false, new FxGraphRenderer());

		setCenter(view);
	}

	public void closeGraph() {
		setCenter(null);

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

}
