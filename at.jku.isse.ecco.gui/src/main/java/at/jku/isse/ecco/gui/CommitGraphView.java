package at.jku.isse.ecco.gui;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.EccoService;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.listener.EccoListener;
import at.jku.isse.ecco.plugin.artifact.ArtifactReader;
import at.jku.isse.ecco.plugin.artifact.ArtifactWriter;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingNode;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Button;
import javafx.scene.control.ToolBar;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import org.graphstream.graph.Edge;
import org.graphstream.graph.Graph;
import org.graphstream.graph.Node;
import org.graphstream.graph.implementations.SingleGraph;
import org.graphstream.ui.layout.Layout;
import org.graphstream.ui.layout.springbox.implementations.SpringBox;
import org.graphstream.ui.swingViewer.ViewPanel;
import org.graphstream.ui.view.Viewer;

import javax.swing.*;
import java.nio.file.Path;

public class CommitGraphView extends BorderPane implements EccoListener {

	private EccoService service;

	private Graph graph;
	private Layout layout;
	private ViewPanel view;

	public CommitGraphView(EccoService service) {
		this.service = service;


		ToolBar toolBar = new ToolBar();
		this.setTop(toolBar);

		Button refreshButton = new Button("Refresh");

		refreshButton.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent e) {
				toolBar.setDisable(true);
				//SwingUtilities.invokeLater(() -> {
				Platform.runLater(() -> {
					CommitGraphView.this.updateGraph();
				});
				Task refreshTask = new Task<Void>() {
					@Override
					public Void call() throws EccoException {
						//CommitGraphView.this.updateGraph();
						Platform.runLater(() -> {
							toolBar.setDisable(false);
						});
						return null;
					}
				};

				new Thread(refreshTask).start();
			}
		});


		toolBar.getItems().add(refreshButton);


		Button exportButton = new Button("Export");
		toolBar.getItems().add(exportButton);


		System.setProperty("gs.ui.renderer", "org.graphstream.ui.j2dviewer.J2DGraphRenderer");


		this.graph = new SingleGraph("CommitGraph");

		this.layout = new SpringBox(false);
		this.graph.addSink(this.layout);
		this.layout.addAttributeSink(this.graph);

		Viewer viewer = new Viewer(this.graph, Viewer.ThreadingModel.GRAPH_IN_ANOTHER_THREAD);
		viewer.enableAutoLayout(this.layout);
		this.view = viewer.addDefaultView(false); // false indicates "no JFrame"

		SwingNode swingNode = new SwingNode();

		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				swingNode.setContent(view);
			}
		});

		this.setCenter(swingNode);


		swingNode.setOnScroll(new EventHandler<ScrollEvent>() {
			@Override
			public void handle(ScrollEvent event) {
				view.getCamera().setViewPercent(Math.max(0.1, Math.min(1.0, view.getCamera().getViewPercent() - 0.05 * event.getDeltaY() / event.getMultiplierY())));
			}
		});


		service.addListener(this);

		if (!service.isInitialized())
			this.setDisable(true);
	}

	private void updateGraph() {

		this.graph.clear();
		this.view.getCamera().resetView();

		this.graph.addAttribute("ui.quality");
		this.graph.addAttribute("ui.antialias");
		this.graph.addAttribute("ui.stylesheet",
				"edge { size: 2px; shape: blob; } " +
						"edge.commit { fill-color: #aaaaff; } " +
						"edge.assoc { fill-color: #ffddaa; } " +
						"node { shape: circle; size: 24px; stroke-mode: plain; stroke-color: #000000; stroke-width: 3px; } " +
						"node.commit { fill-color: #aaaaff; } " +
						"node.association { fill-color: #ffddaa; } ");

		for (Commit commit : this.service.getCommits()) {
			Node commitNode = this.graph.addNode("C" + commit.getId());
			commitNode.addAttribute("ui.class", "commit");
			commitNode.addAttribute("label", commitNode.getId());

			for (Association association : commit.getAssociations()) {
				Node associationNode = this.graph.getNode("A" + association.getId());
				if (associationNode == null) {
					associationNode = this.graph.addNode("A" + association.getId());
					associationNode.addAttribute("ui.class", "association");
					associationNode.addAttribute("label", associationNode.getId());
				}

				Edge commitEdge = this.graph.addEdge(commitNode.getId() + associationNode.getId(), commitNode, associationNode, true);
				commitEdge.setAttribute("ui.class", "commit");
			}
		}

		for (Association association : this.service.getAssociations()) {
			Node associationNode = this.graph.getNode("A" + association.getId());
			if (associationNode == null) {
				associationNode = this.graph.addNode("A" + association.getId());
				associationNode.addAttribute("ui.class", "association");
				associationNode.addAttribute("label", associationNode.getId());
			}

			for (Association parent : association.getParents()) {
				Node parentNode = this.graph.getNode("A" + parent.getId());
				if (parentNode == null) {
					parentNode = this.graph.addNode("A" + parent.getId());
					parentNode.addAttribute("ui.class", "association");
					parentNode.addAttribute("label", associationNode.getId());
				}

				Edge associationEdge = this.graph.addEdge(associationNode.getId() + parentNode.getId(), parentNode, associationNode, true);
				associationEdge.setAttribute("ui.class", "assoc");
			}
		}
	}

	@Override
	public void statusChangedEvent(EccoService service) {
		if (service.isInitialized()) {
			Platform.runLater(() -> {
				//this.updateGraph();
				this.setDisable(false);
			});
		} else {
			Platform.runLater(() -> {
				this.setDisable(true);
			});
		}
	}

	@Override
	public void commitsChangedEvent(EccoService service, Commit commit) {

	}

	@Override
	public void fileReadEvent(Path file, ArtifactReader reader) {

	}

	@Override
	public void fileWriteEvent(Path file, ArtifactWriter writer) {

	}

}
