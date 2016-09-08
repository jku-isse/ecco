package at.jku.isse.ecco.gui.view.graph;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.EccoService;
import at.jku.isse.ecco.composition.LazyCompositionRootNode;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.gui.ExceptionAlert;
import at.jku.isse.ecco.listener.RepositoryListener;
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
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.graphstream.graph.Edge;
import org.graphstream.graph.Graph;
import org.graphstream.graph.Node;
import org.graphstream.graph.implementations.SingleGraph;
import org.graphstream.stream.file.FileSink;
import org.graphstream.stream.file.FileSinkFactory;
import org.graphstream.ui.layout.Layout;
import org.graphstream.ui.layout.springbox.implementations.SpringBox;
import org.graphstream.ui.swingViewer.ViewPanel;
import org.graphstream.ui.view.Viewer;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class ArtifactGraphView extends BorderPane implements RepositoryListener {

	private EccoService service;

	private Graph graph;
	private Layout layout;
	private Viewer viewer;
	private ViewPanel view;

	private boolean depthFade = false;
	private boolean showLabels = true;

	public ArtifactGraphView(EccoService service, Stage stage) {
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
			}
		});

		toolBar.getItems().add(refreshButton);


		Button exportButton = new Button("Export");

		exportButton.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent ae) {
				toolBar.setDisable(true);

				FileChooser fileChooser = new FileChooser();
				File selectedFile = fileChooser.showSaveDialog(stage);

				if (selectedFile != null) {
					FileSink out = FileSinkFactory.sinkFor(selectedFile.toString());
					try {
						out.writeAll(ArtifactGraphView.this.graph, selectedFile.toString());
						out.flush();
					} catch (IOException e) {
						new ExceptionAlert(e).show();
					}
				}

				toolBar.setDisable(false);
			}
		});

		toolBar.getItems().add(exportButton);


		CheckBox depthFadeCheckBox = new CheckBox("Depth Fade");
		toolBar.getItems().add(depthFadeCheckBox);
		depthFadeCheckBox.selectedProperty().addListener(new ChangeListener<Boolean>() {
			public void changed(ObservableValue<? extends Boolean> ov, Boolean old_val, Boolean new_val) {
				ArtifactGraphView.this.depthFade = new_val;
				ArtifactGraphView.this.updateNodesAndEdgesStyles(new_val);
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


	private void updateNodesAndEdgesStyles(boolean depthFade) {
		for (Node node : this.graph.getNodeSet()) {
			int depth = node.getAttribute(DEPTH_ATTRIBUTE);

			int size = DEFAULT_SIZE;
			if (node.hasAttribute(SUCCESSOR_COUNT_ATTRIBUTE)) {
				int successorsCount = node.getAttribute(SUCCESSOR_COUNT_ATTRIBUTE);
				size = (int) ((double) MIN_SIZE + ((double) successorsCount) / (double) (this.maxSuccessorsCount) * (double) (MAX_SIZE - MIN_SIZE));
			}

			if (depthFade) {
				int colorValue = (int) (((double) depth) * 200.0 / ((double) this.maxDepth));
				node.setAttribute("ui.style", "size: " + size + "px; fill-color: rgb(" + colorValue + ", " + colorValue + ", " + colorValue + ");");
				node.removeAttribute("ui.class");
			} else {
				node.setAttribute("ui.style", "size: " + size + "px;");
				if (node.hasAttribute(ASSOC_ID_ATTRIBUTE))
					node.setAttribute("ui.class", "A" + ((node.<Integer>getAttribute(ASSOC_ID_ATTRIBUTE) % 7) + 1));
			}
		}

		for (Edge edge : this.graph.getEdgeSet()) {
			//int depth = edge.getAttribute(DEPTH_ATTRIBUTE);
			int depth = edge.getSourceNode().getAttribute(DEPTH_ATTRIBUTE);
			int colorValue = (int) (((double) depth) * 200.0 / ((double) this.maxDepth));
			if (depthFade) {
				edge.addAttribute("ui.style", "fill-color: rgb(" + colorValue + ", " + colorValue + ", " + colorValue + ");");
				edge.removeAttribute("ui.class");
			} else {
				if (edge.getSourceNode().hasAttribute(ASSOC_ID_ATTRIBUTE))
					edge.addAttribute("ui.class", "A" + ((edge.getSourceNode().<Integer>getAttribute(ASSOC_ID_ATTRIBUTE) % 7) + 1));
				edge.removeAttribute("ui.style");
			}
		}
	}

	private void updateGraphStylehseet(boolean showLabels) {
		String textMode = "text-mode: normal; ";
		if (!showLabels)
			textMode = "text-mode: hidden; ";

		this.graph.addAttribute("ui.stylesheet",
				"edge { size: 1px; shape: blob; arrow-shape: none; arrow-size: 3px, 3px; } " +
						"node { " + textMode + " text-background-mode: plain;  shape: circle; size: " + DEFAULT_SIZE + "px; stroke-mode: plain; stroke-color: #000000; stroke-width: 1px; } " +
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

		this.artifactCount = 0;
		this.maxSuccessorsCount = 0;
		this.maxDepth = 0;

		// traverse trees and add nodes
//		for (Association association : this.service.getAssociations()) {
//			this.traverseTree(association.getRootNode(), 0, association.getId(), depthFade);
//		}
		LazyCompositionRootNode compRootNode = new LazyCompositionRootNode();
		for (Association association : this.service.getAssociations()) {
			compRootNode.addOrigNode(association.getRootNode());
		}
		this.traverseTree(compRootNode, 0);

		this.updateNodesAndEdgesStyles(depthFade);
		this.updateGraphStylehseet(showLabels);

//		while (this.layout.getStabilization() < 0.9) {
//			this.layout.compute();
//		}


		this.graph.addSink(this.layout);
		this.layout.addAttributeSink(this.graph);

		this.viewer.enableAutoLayout(this.layout);
	}


	private static final int CHILD_COUNT_LIMIT = 100;
	private static final int MAX_SIZE = 100;
	private static final int MIN_SIZE = 30;
	private static final int DEFAULT_SIZE = 20;
	private static final String SUCCESSOR_COUNT_ATTRIBUTE = "artifactsCount";
	private static final String DEPTH_ATTRIBUTE = "depth";
	private static final String ASSOC_ID_ATTRIBUTE = "assocId";

	private int artifactCount = 0;
	private int maxSuccessorsCount = 0;
	private int maxDepth = 0;

	private void groupArtifactsByAssocRec(at.jku.isse.ecco.tree.Node eccoNode, Map<Association, Integer> groupMap) {
		for (at.jku.isse.ecco.tree.Node eccoChildNode : eccoNode.getChildren()) {
			if (eccoChildNode.getArtifact() != null) {
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

	private Node traverseTree(at.jku.isse.ecco.tree.Node eccoNode, int depth) {
		int assocId = 0;

		Node graphNode = null;
		if (eccoNode.getArtifact() != null) {
			this.artifactCount++;

			graphNode = this.graph.addNode(String.valueOf(this.artifactCount));
			graphNode.addAttribute(DEPTH_ATTRIBUTE, depth);
			assocId = eccoNode.getArtifact().getContainingNode().getContainingAssociation().getId();
			graphNode.addAttribute(ASSOC_ID_ATTRIBUTE, assocId);
			if (this.maxDepth < depth)
				this.maxDepth = depth;

//			if (depthFade) {
//				graphNode.addAttribute("ui.style", "fill-color: rgb(" + colorValue + ", " + colorValue + ", " + colorValue + ");");
//			} else {
//				graphNode.addAttribute("ui.class", "A" + ((eccoNode.getArtifact().getContainingNode().getContainingAssociation().getId() & 7) + 1));
//			}

			if (eccoNode.getChildren().size() >= CHILD_COUNT_LIMIT) {
				// group children by association
				Map<Association, Integer> groupMap = new HashMap<>();
				this.groupArtifactsByAssocRec(eccoNode, groupMap);
//				for (at.jku.isse.ecco.tree.Node eccoChildNode : eccoNode.getChildren()) {
//					if (eccoChildNode.getArtifact() != null) {
//						Association childContainingAssociation = eccoChildNode.getArtifact().getContainingNode().getContainingAssociation();
//						if (childContainingAssociation != null) {
//							int childContainedArtifactsCount = eccoChildNode.getArtifact().getContainingNode().countArtifacts();
//							if (groupMap.containsKey(childContainingAssociation)) {
//								int groupCount = groupMap.get(childContainingAssociation);
//								groupCount += childContainedArtifactsCount;
//								groupMap.put(childContainingAssociation, groupCount);
//							} else {
//								groupMap.put(childContainingAssociation, childContainedArtifactsCount);
//							}
//						}
//					}
//				}
				// add one child node per group
				for (Map.Entry<Association, Integer> entry : groupMap.entrySet()) {
					this.artifactCount++;
					Node graphChildNode = this.graph.addNode(String.valueOf(this.artifactCount));
					graphChildNode.setAttribute("label", "[" + entry.getValue() + "]");
//					if (depthFade) {
//						int childColorValue = (int) (Math.min(1.0, (depth + 1) / 8.0) * 200.0);
//						graphChildNode.addAttribute("ui.style", "size: " + Math.min(MAX_SIZE, entry.getValue()) + "px; fill-color: rgb(" + childColorValue + ", " + childColorValue + ", " + childColorValue + ");");
//					} else {
//						graphChildNode.addAttribute("ui.style", "size: " + Math.min(MAX_SIZE, entry.getValue()) + "px;");
//						graphChildNode.addAttribute("ui.class", "A" + ((entry.getKey().getId() % 7) + 1));
//					}
//					if (!depthFade) {
//						graphChildNode.addAttribute("ui.class", "A" + ((entry.getKey().getId() % 7) + 1));
//					}
					graphChildNode.addAttribute(SUCCESSOR_COUNT_ATTRIBUTE, entry.getValue());
					graphChildNode.addAttribute(DEPTH_ATTRIBUTE, depth + 1);
					graphChildNode.addAttribute(ASSOC_ID_ATTRIBUTE, entry.getKey().getId());

					if (this.maxSuccessorsCount < entry.getValue())
						this.maxSuccessorsCount = entry.getValue();

					Edge edge = this.graph.addEdge(graphNode.getId() + "-" + graphChildNode.getId(), graphNode, graphChildNode, true);
//					edge.addAttribute(DEPTH_ATTRIBUTE, depth);
//					edge.addAttribute(ASSOC_ID_ATTRIBUTE, assocId);
				}
			}

			if (eccoNode.getArtifact().getData() instanceof PluginArtifactData) {
				graphNode.setAttribute("label", ((PluginArtifactData) eccoNode.getArtifact().getData()).getPath().toString());
			} else if (eccoNode.getArtifact().getData() instanceof DirectoryArtifactData) {
				graphNode.setAttribute("label", ((DirectoryArtifactData) eccoNode.getArtifact().getData()).getPath().toString());
			}
		}


		if (eccoNode.getChildren().size() < CHILD_COUNT_LIMIT) {
			for (at.jku.isse.ecco.tree.Node eccoChildNode : eccoNode.getChildren()) {
				Node graphChildNode = this.traverseTree(eccoChildNode, depth + 1);

				if (graphChildNode != null && graphNode != null) {
					Edge edge = this.graph.addEdge(graphNode.getId() + "-" + graphChildNode.getId(), graphNode, graphChildNode, true);
//					edge.addAttribute(DEPTH_ATTRIBUTE, depth);
//					edge.addAttribute(ASSOC_ID_ATTRIBUTE, assocId);

//					if (depthFade)
//						edge.addAttribute("ui.style", "fill-color: rgb(" + colorValue + ", " + colorValue + ", " + colorValue + ");");
//					else
//						edge.addAttribute("ui.class", "A" + ((eccoNode.getArtifact().getContainingNode().getContainingAssociation().getId() % 7) + 1));
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
