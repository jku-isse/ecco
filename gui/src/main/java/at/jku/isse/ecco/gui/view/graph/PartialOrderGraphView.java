package at.jku.isse.ecco.gui.view.graph;

import at.jku.isse.ecco.pog.PartialOrderGraph;
import javafx.embed.swing.SwingNode;
import javafx.scene.layout.BorderPane;
import org.graphstream.graph.Graph;
import org.graphstream.graph.implementations.SingleGraph;
import org.graphstream.ui.layout.Layout;
import org.graphstream.ui.layout.springbox.implementations.SpringBox;
import org.graphstream.ui.swingViewer.ViewPanel;
import org.graphstream.ui.view.Viewer;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;

public class PartialOrderGraphView extends BorderPane {

	private Graph graph;
	private Layout layout;
	private Viewer viewer;
	private ViewPanel view;

	public PartialOrderGraphView() {
		System.setProperty("org.graphstream.ui.renderer", "org.graphstream.ui.j2dviewer.J2DGraphRenderer");


		this.graph = new SingleGraph("PartialOrderGraph");

		this.layout = new SpringBox(false);
		this.graph.addSink(layout);
		layout.addAttributeSink(this.graph);

		this.viewer = new Viewer(this.graph, Viewer.ThreadingModel.GRAPH_IN_ANOTHER_THREAD);
		//viewer.enableAutoLayout(layout);
		this.view = viewer.addDefaultView(false); // false indicates "no JFrame"

		SwingNode swingNode = new SwingNode();

		SwingUtilities.invokeLater(() -> swingNode.setContent(view));


		this.setOnScroll(event -> view.getCamera().setViewPercent(Math.max(0.1, Math.min(1.0, view.getCamera().getViewPercent() - 0.05 * event.getDeltaY() / event.getMultiplierY()))));


		this.setCenter(swingNode);
	}

	public void showGraph(PartialOrderGraph pog) {
		this.viewer.disableAutoLayout();

		this.graph.removeSink(this.layout);
		this.layout.removeAttributeSink(this.graph);
		this.layout.clear();
		this.graph.clear();

		this.view.getCamera().resetView();


		this.graph.setStrict(false);

		this.graph.addAttribute("ui.quality");
		this.graph.addAttribute("ui.antialias");
		this.graph.addAttribute("ui.stylesheet",
				" node.start { fill-color: green; size: 20px; } node.end { fill-color: red; size: 20px; } node {text-alignment:above;text-background-mode:plain;}");

		this.view.getCamera().resetView();


		this.traversePartialOrderGraph(pog.getHead(), null, new HashMap<>());


		this.graph.addSink(this.layout);
		this.layout.addAttributeSink(this.graph);

		this.viewer.enableAutoLayout(this.layout);
	}

	private void traversePartialOrderGraph(PartialOrderGraph.Node pogNode, org.graphstream.graph.Node gsParent, Map<PartialOrderGraph.Node, org.graphstream.graph.Node> nodeMap) {
		org.graphstream.graph.Node gsNode = nodeMap.get(pogNode);
		if (gsNode == null) {
			gsNode = this.graph.addNode("N" + nodeMap.size() + 1);
			gsNode.setAttribute("label", pogNode.getArtifact() + " [" + (pogNode.getArtifact() != null ? pogNode.getArtifact().getSequenceNumber() : "-") + "]");

			if (pogNode.getPrevious().isEmpty()) {
				gsNode.setAttribute("ui.class", "start");
			}

			if (pogNode.getNext().isEmpty()) {
				gsNode.setAttribute("ui.class", "end");
			}

			nodeMap.put(pogNode, gsNode);
		}
		if (gsParent != null) {
			this.graph.addEdge("E" + gsParent.getId() + "-" + gsNode.getId(), gsParent, gsNode, true);
		}
		for (PartialOrderGraph.Node pogChild : pogNode.getNext()) {
			this.traversePartialOrderGraph(pogChild, gsNode, nodeMap);
		}
	}

}
