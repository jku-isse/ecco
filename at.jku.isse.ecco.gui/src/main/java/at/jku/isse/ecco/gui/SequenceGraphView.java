package at.jku.isse.ecco.gui;

import at.jku.isse.ecco.sequenceGraph.SequenceGraph;
import at.jku.isse.ecco.sequenceGraph.SequenceGraphNode;
import at.jku.isse.ecco.tree.Node;
import javafx.embed.swing.SwingNode;
import javafx.scene.layout.BorderPane;
import org.graphstream.graph.Edge;
import org.graphstream.graph.Graph;
import org.graphstream.graph.implementations.SingleGraph;
import org.graphstream.ui.layout.Layout;
import org.graphstream.ui.layout.springbox.implementations.SpringBox;
import org.graphstream.ui.swingViewer.ViewPanel;
import org.graphstream.ui.view.Viewer;

import javax.swing.*;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class SequenceGraphView extends BorderPane {

	private Graph graph;

	public SequenceGraphView() {


		System.setProperty("gs.ui.renderer", "org.graphstream.ui.j2dviewer.J2DGraphRenderer");


		this.graph = new SingleGraph("SequenceGraph");

		Layout layout = new SpringBox(false);
		this.graph.addSink(layout);
		layout.addAttributeSink(this.graph);

		Viewer viewer = new Viewer(this.graph, Viewer.ThreadingModel.GRAPH_IN_ANOTHER_THREAD);
		viewer.enableAutoLayout(layout);
		ViewPanel view = viewer.addDefaultView(false); // false indicates "no JFrame"

		SwingNode swingNode = new SwingNode();

		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				swingNode.setContent(view);
			}
		});

		this.setCenter(swingNode);
	}

	public void showGraph(SequenceGraph sg) {
		this.graph.clear();

		this.graph.setStrict(false);

		this.graph.addAttribute("ui.quality");
		this.graph.addAttribute("ui.antialias");
		this.graph.addAttribute("ui.stylesheet",
				" ");


		org.graphstream.graph.Node root = this.graph.addNode("N");

		this.traverseSequenceGraph(sg.getRoot(), root, "", new HashSet<Node>());
	}


	private void traverseSequenceGraph(SequenceGraphNode sgn, org.graphstream.graph.Node parent, String currentPath, Set<Node> nodeSet) {
		for (Map.Entry<Node, SequenceGraphNode> entry : sgn.getChildren().entrySet()) {
			Set<Node> newNodeSet = new HashSet<Node>(nodeSet);
			newNodeSet.add(entry.getKey());

			org.graphstream.graph.Node child = this.graph.addNode("N" + this.getStringForPath(newNodeSet));

			String newPath = currentPath + "," + entry.getKey().getSequenceNumber();
			Edge edge = this.graph.addEdge("E" + newPath, parent, child, true);

			if (edge != null) {
				edge.setAttribute("label", entry.getKey() + " [" + entry.getKey().getSequenceNumber() + "]");

				this.traverseSequenceGraph(entry.getValue(), child, newPath, newNodeSet);
			}
		}


	}

	private String getStringForPath(Set<Node> path) {
		return path.stream()
				.sorted((n1, n2) -> Integer.compare(n1.getSequenceNumber(), n2.getSequenceNumber()))
				.map(v -> String.valueOf(v.getSequenceNumber()))
				.collect(Collectors.joining(","));
	}


}
