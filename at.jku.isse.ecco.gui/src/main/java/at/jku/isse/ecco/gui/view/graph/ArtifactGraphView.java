package at.jku.isse.ecco.gui.view.graph;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.EccoService;
import at.jku.isse.ecco.composition.LazyCompositionRootNode;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.listener.EccoListener;
import at.jku.isse.ecco.plugin.artifact.ArtifactReader;
import at.jku.isse.ecco.plugin.artifact.ArtifactWriter;
import at.jku.isse.ecco.plugin.artifact.DirectoryArtifactData;
import at.jku.isse.ecco.plugin.artifact.PluginArtifactData;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingNode;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
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

public class ArtifactGraphView extends BorderPane implements EccoListener {

	private EccoService service;

	private Graph graph;
	private Layout layout;
	private Viewer viewer;
	private ViewPanel view;

	private boolean depthFade = false;
	private boolean showLabels = true;

	public ArtifactGraphView(EccoService service) {
		this.service = service;


		ToolBar toolBar = new ToolBar();
		this.setTop(toolBar);

		Button refreshButton = new Button("Refresh");

		refreshButton.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent e) {
				toolBar.setDisable(true);
				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						ArtifactGraphView.this.updateGraph(ArtifactGraphView.this.depthFade, ArtifactGraphView.this.showLabels);
					}
				});
				Task refreshTask = new Task<Void>() {
					@Override
					public Void call() throws EccoException {
						//ArtifactsGraphView.this.updateGraph();
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


		CheckBox depthFadeCheckBox = new CheckBox("Depth Fade");
		toolBar.getItems().add(depthFadeCheckBox);
		depthFadeCheckBox.selectedProperty().addListener(new ChangeListener<Boolean>() {
			public void changed(ObservableValue<? extends Boolean> ov, Boolean old_val, Boolean new_val) {
				ArtifactGraphView.this.depthFade = new_val;
			}
		});

		CheckBox showLabelsCheckbox = new CheckBox("Show Labels");
		toolBar.getItems().add(showLabelsCheckbox);
		showLabelsCheckbox.selectedProperty().addListener(new ChangeListener<Boolean>() {
			public void changed(ObservableValue<? extends Boolean> ov, Boolean old_val, Boolean new_val) {
				ArtifactGraphView.this.showLabels = new_val;
				ArtifactGraphView.this.updateGraphStylehseet(new_val);
			}
		});


		//System.clearProperty("gs.ui.renderer");
		System.setProperty("gs.ui.renderer", "org.graphstream.ui.j2dviewer.J2DGraphRenderer");


		this.graph = new SingleGraph("ArtifactsGraph");

		this.layout = new SpringBox(false);
		this.graph.addSink(this.layout);
		this.layout.addAttributeSink(this.graph);

		this.viewer = new Viewer(this.graph, Viewer.ThreadingModel.GRAPH_IN_ANOTHER_THREAD);
		//this.viewer.enableAutoLayout(this.layout);
		this.view = this.viewer.addDefaultView(false); // false indicates "no JFrame"

		SwingNode swingNode = new SwingNode();

		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				swingNode.setContent(view);
			}
		});


		this.setOnScroll(new EventHandler<ScrollEvent>() {
			@Override
			public void handle(ScrollEvent event) {
				view.getCamera().setViewPercent(Math.max(0.1, Math.min(1.0, view.getCamera().getViewPercent() - 0.05 * event.getDeltaY() / event.getMultiplierY())));
			}
		});


		this.setCenter(swingNode);


		depthFadeCheckBox.setSelected(this.depthFade);
		showLabelsCheckbox.setSelected(this.showLabels);


		service.addListener(this);

		if (!service.isInitialized())
			this.setDisable(true);
	}


	private void updateGraphStylehseet(boolean showLabels) {
		String textMode = "text-mode: normal; ";
		if (!showLabels)
			textMode = "text-mode: hidden; ";

		this.graph.addAttribute("ui.stylesheet",
				"edge { size: 1px; shape: blob; arrow-shape: none; arrow-size: 3px, 3px; } " +
						"node { " + textMode + " text-background-mode: plain;  shape: circle; size: 10px; stroke-mode: plain; stroke-color: #000000; stroke-width: 1px; } " +
						"edge.A1 { fill-color: #ffaaaa; } " +
						"edge.A2 { fill-color: #aaffaa; } " +
						"edge.A3 { fill-color: #aaaaff; } " +
						"edge.A4 { fill-color: #ffffaa; } " +
						"edge.A5 { fill-color: #ffaaff; } " +
						"edge.A6 { fill-color: #aaffff; } " +
						"edge.A7 { fill-color: #aaaaaa; } " +
						"node.A1 { fill-color: #ffaaaa; } " +
						"node.A2 { fill-color: #aaffaa; } " +
						"node.A3 { fill-color: #aaaaff; } " +
						"node.A4 { fill-color: #ffffaa; } " +
						"node.A5 { fill-color: #ffaaff; } " +
						"node.A6 { fill-color: #aaffff; } " +
						"node.A7 { fill-color: #aaaaaa; } ");
	}

	private void updateGraph(boolean depthFade, boolean showLabels) {
		this.viewer.disableAutoLayout();

		this.graph.removeSink(this.layout);
		this.layout.removeAttributeSink(this.graph);
		this.layout.clear();
		this.graph.clear();

		this.view.getCamera().resetView();


		this.graph.addAttribute("ui.quality");
		this.graph.addAttribute("ui.antialias");

		this.updateGraphStylehseet(showLabels);

		this.artifactCount = 0;

		// traverse trees and add nodes
//		for (Association association : this.service.getAssociations()) {
//			this.traverseTree(association.getRootNode(), 0, association.getId(), depthFade);
//		}
		LazyCompositionRootNode compRootNode = new LazyCompositionRootNode();
		for (Association association : this.service.getAssociations()) {
			compRootNode.addOrigNode(association.getRootNode());
		}
		this.traverseTree(compRootNode, 0, depthFade, showLabels);

		while (this.layout.getStabilization() < 0.9) {
			System.out.println(this.layout.getStabilization());
			this.layout.compute();
		}
		System.out.println(this.layout.getStabilization());


		this.graph.addSink(this.layout);
		this.layout.addAttributeSink(this.graph);

		this.viewer.enableAutoLayout(this.layout);
	}


	private static final int CHILD_COUNT_LIMIT = 100;

	private int artifactCount = 0;

	private Node traverseTree(at.jku.isse.ecco.tree.Node eccoNode, int depth, boolean depthFade, boolean showLabels) {
		int colorValue = (int) (Math.min(1.0, depth / 8.0) * 200.0);

		Node graphNode = null;
		if (eccoNode.getArtifact() != null) {
			this.artifactCount++;

			graphNode = this.graph.addNode(String.valueOf(artifactCount));
			if (depthFade) {
				if (eccoNode.getChildren().size() < CHILD_COUNT_LIMIT)
					graphNode.addAttribute("ui.style", "fill-color: rgb(" + colorValue + ", " + colorValue + ", " + colorValue + ");");
				else
					graphNode.addAttribute("ui.style", "size: " + Math.min(200, eccoNode.getChildren().size()) + "px; fill-color: rgb(" + colorValue + ", " + colorValue + ", " + colorValue + ");");
			} else {
				if (eccoNode.getChildren().size() >= CHILD_COUNT_LIMIT)
					graphNode.addAttribute("ui.style", "size: " + Math.min(200, eccoNode.getChildren().size()) + "px;");
				graphNode.addAttribute("ui.class", "A" + ((eccoNode.getArtifact().getContainingNode().getContainingAssociation().getId() & 7) + 1));
			}

			if (eccoNode.getArtifact().getData() instanceof PluginArtifactData) {
				graphNode.setAttribute("label", ((PluginArtifactData) eccoNode.getArtifact().getData()).getPath().toString());
			} else if (eccoNode.getArtifact().getData() instanceof DirectoryArtifactData) {
				graphNode.setAttribute("label", ((DirectoryArtifactData) eccoNode.getArtifact().getData()).getPath().toString());
			}
		}


		if (eccoNode.getChildren().size() < CHILD_COUNT_LIMIT) {
			for (at.jku.isse.ecco.tree.Node eccoChildNode : eccoNode.getChildren()) {
				Node graphChildNode = this.traverseTree(eccoChildNode, depth + 1, depthFade, showLabels);

				if (graphChildNode != null && graphNode != null) {
					Edge edge = this.graph.addEdge(graphNode.getId() + "-" + graphChildNode.getId(), graphNode, graphChildNode, true);
					if (depthFade)
						edge.addAttribute("ui.style", "fill-color: rgb(" + colorValue + ", " + colorValue + ", " + colorValue + ");");
					else
						//edge.addAttribute("ui.class", "A" + assocId);
						edge.addAttribute("ui.class", "A" + ((eccoNode.getArtifact().getContainingNode().getContainingAssociation().getId() & 7) + 1));
				}
			}
		}

		return graphNode;
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
