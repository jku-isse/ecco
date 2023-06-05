package at.jku.isse.ecco.gui.view.graph;

import at.jku.isse.ecco.adapter.dispatch.DirectoryArtifactData;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.composition.LazyCompositionRootNode;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.gui.EditableSpinner;
import at.jku.isse.ecco.gui.ExceptionAlert;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.service.listener.EccoListener;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;
import org.graphstream.graph.Edge;
import org.graphstream.graph.Graph;
import org.graphstream.graph.Node;
import org.graphstream.graph.implementations.SingleGraph;
import org.graphstream.stream.file.FileSink;
import org.graphstream.stream.file.FileSinkFactory;
import org.graphstream.ui.javafx.FxGraphRenderer;
import org.graphstream.ui.layout.Layout;
import org.graphstream.ui.layout.springbox.implementations.SpringBox;
import org.graphstream.ui.fx_viewer.FxViewPanel;
import org.graphstream.ui.fx_viewer.FxViewer;
import org.graphstream.ui.view.Viewer;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class ArtifactGraphView extends BorderPane implements EccoListener {

	private final EccoService service;

	private final Graph graph;
	private final Layout layout;
	private FxViewer viewer;
	private FxViewPanel view;

	private boolean depthFade = false;
	private boolean showLabels = true;

	private int childCountLimit = 20;
	private int depthLimit = 10;

	public ArtifactGraphView(EccoService service) {
		this.service = service;

		ToolBar toolBar = new ToolBar();
		this.setTop(toolBar);

		Spinner<Integer> childCountLimitSpinner = new EditableSpinner(1, CHILD_COUNT_LIMIT, childCountLimit);
		childCountLimitSpinner.setEditable(true);
		Label childCountLimitLabel = new Label("Child Count Limit: ");

		Spinner<Integer> depthLimitSpinner = new EditableSpinner(1, DEPTH_LIMIT, depthLimit);
		depthLimitSpinner.setEditable(true);
		Label depthLimitLabel = new Label("Depth Limit: ");

		Button refreshButton = new Button("Refresh");
		refreshButton.setOnAction(e -> {
			toolBar.setDisable(true);
			childCountLimit = childCountLimitSpinner.getValue();
			depthLimit = depthLimitSpinner.getValue();
			SwingUtilities.invokeLater(() -> {
				ArtifactGraphView.this.updateGraph(ArtifactGraphView.this.depthFade, ArtifactGraphView.this.showLabels);
				Platform.runLater(() -> toolBar.setDisable(false));
			});
		});

		Button exportButton = new Button("Export");
		exportButton.setOnAction(ae -> {
			toolBar.setDisable(true);

			FileChooser fileChooser = new FileChooser();
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

		CheckBox depthFadeCheckBox = new CheckBox("Depth Fade");
		depthFadeCheckBox.selectedProperty().addListener((ov, old_val, new_val) -> {
			ArtifactGraphView.this.depthFade = new_val;
			ArtifactGraphView.this.updateNodesAndEdgesStyles(new_val);
		});

		CheckBox showLabelsCheckbox = new CheckBox("Show Labels");
		showLabelsCheckbox.selectedProperty().addListener((ov, old_val, new_val) -> {
			ArtifactGraphView.this.showLabels = new_val;
			ArtifactGraphView.this.updateGraphStylehseet(new_val);
		});


		toolBar.getItems().setAll(refreshButton, new Separator(), exportButton, new Separator(), depthFadeCheckBox, new Separator(), showLabelsCheckbox, new Separator(), childCountLimitLabel, childCountLimitSpinner, new Separator(), depthLimitLabel, depthLimitSpinner, new Separator());


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

		depthFadeCheckBox.setSelected(this.depthFade);
		showLabelsCheckbox.setSelected(this.showLabels);

		service.addListener(this);
		Platform.runLater(() -> statusChangedEvent(service));
	}


	private void updateNodesAndEdgesStyles(boolean depthFade) {
		Map<String, Integer> idColorMap = new HashMap<>();
		int nextColor = 1;

		for (Node node : this.graph.nodes().collect(Collectors.toSet())) {
			int depth = node.getAttribute(DEPTH_ATTRIBUTE, Integer.class);

			int size = DEFAULT_SIZE;
			if (node.hasAttribute(SUCCESSOR_COUNT_ATTRIBUTE)) {
				int successorsCount = node.getAttribute(SUCCESSOR_COUNT_ATTRIBUTE, Integer.class);
				size = (int) ((double) MIN_SIZE + ((double) successorsCount) / (double) (this.maxSuccessorsCount) * (double) (MAX_SIZE - MIN_SIZE));
			}

			if (depthFade) {
				int colorValue = (int) (((double) depth) * 200.0 / ((double) this.maxDepth));
				node.setAttribute("ui.style", "size: " + size + "px; fill-color: rgb(" + colorValue + ", " + colorValue + ", " + colorValue + ");");
				node.removeAttribute("ui.class");
			} else {
				node.setAttribute("ui.style", "size: " + size + "px;");
				if (node.hasAttribute(ASSOC_ID_ATTRIBUTE)) {
					String id = node.getAttribute(ASSOC_ID_ATTRIBUTE, String.class);
					if (!idColorMap.containsKey(id)) {
						idColorMap.put(id, nextColor++);
					}
					node.setAttribute("ui.class", "A" + idColorMap.get(id));
					//node.setAttribute("ui.class", "A" + ((node.<Integer>getAttribute(ASSOC_ID_ATTRIBUTE) % 7) + 1));
				}
			}
		}

		for (Edge edge : this.graph.edges().collect(Collectors.toSet())) {
			//int depth = edge.getAttribute(DEPTH_ATTRIBUTE);
			int depth = edge.getSourceNode().getAttribute(DEPTH_ATTRIBUTE, Integer.class);
			int colorValue = (int) (((double) depth) * 200.0 / ((double) this.maxDepth));
			if (depthFade) {
				edge.setAttribute("ui.style", "fill-color: rgb(" + colorValue + ", " + colorValue + ", " + colorValue + ");");
				edge.removeAttribute("ui.class");
			} else {
				if (edge.getSourceNode().hasAttribute(ASSOC_ID_ATTRIBUTE)) {
					String id = edge.getSourceNode().getAttribute(ASSOC_ID_ATTRIBUTE, String.class);
					if (!idColorMap.containsKey(id)) {
						idColorMap.put(id, nextColor++);
					}
					edge.setAttribute("ui.class", "A" + idColorMap.get(id));
					//edge.setAttribute("ui.class", "A" + ((edge.getSourceNode().getAttribute(ASSOC_ID_ATTRIBUTE, Integer.class) % 7) + 1));
				}
				edge.removeAttribute("ui.style");
			}
		}
	}

	private void updateGraphStylehseet(boolean showLabels) {
		String textMode = "text-mode: normal; ";
		if (!showLabels)
			textMode = "text-mode: hidden; ";

		this.graph.setAttribute("ui.stylesheet",
				"edge { size: 1px; shape: blob; arrow-shape: none; arrow-size: 3px, 3px; } " +
						"node { " + textMode + " text-background-mode: plain;  shape: circle; size: " + DEFAULT_SIZE + "px; stroke-mode: plain; stroke-color: #000000; stroke-width: 1px; } " +
						"edge.A1 { fill-color: #ffaaaa88; } " +
						"edge.A2 { fill-color: #aaffaa88; } " +
						"edge.A3 { fill-color: #aaaaff88; } " +
						"edge.A4 { fill-color: #ffffaa88; } " +
						"edge.A5 { fill-color: #ffaaff88; } " +
						"edge.A6 { fill-color: #aaffff88; } " +
						"edge.A7 { fill-color: #aaaaaa88; } " +
						"node.A1 { fill-color: #ffaaaa88; } " +
						"node.A2 { fill-color: #aaffaa88; } " +
						"node.A3 { fill-color: #aaaaff88; } " +
						"node.A4 { fill-color: #ffffaa88; } " +
						"node.A5 { fill-color: #ffaaff88; } " +
						"node.A6 { fill-color: #aaffff88; } " +
						"node.A7 { fill-color: #aaaaaa88; } ");
	}

	private void initView() {
		closeView();
		viewer = new FxViewer(graph, Viewer.ThreadingModel.GRAPH_IN_ANOTHER_THREAD);
		view = (FxViewPanel)  viewer.addDefaultView(false, new FxGraphRenderer());

		setCenter(view);
	}

	private void closeView() {
		if (null == viewer) {
			return;
		}

		setCenter(null);
		viewer.close();
		view = null;
		viewer = null;
	}

	private void updateGraph(boolean depthFade, boolean showLabels) {
		assert viewer != null && view != null;

		this.viewer.disableAutoLayout();

		this.graph.removeSink(this.layout);
		this.layout.removeAttributeSink(this.graph);
		this.layout.clear();
		this.graph.clear();

		this.view.getCamera().resetView();


		this.graph.setAttribute("ui.quality");
		this.graph.setAttribute("ui.antialias");

		this.artifactCount = 0;
		this.maxSuccessorsCount = 0;
		this.maxDepth = 0;

		// traverse trees and add nodes
//		for (Association association : this.service.getAssociations()) {
//			this.traverseTree(association.getRootNode(), 0, association.getId(), depthFade);
//		}
		LazyCompositionRootNode compRootNode = new LazyCompositionRootNode();
		for (Association association : this.service.getRepository().getAssociations()) {
			compRootNode.addOrigNode(association.getRootNode());
		}
		this.traverseTree(compRootNode, 0);

		this.updateNodesAndEdgesStyles(depthFade);
		this.updateGraphStylehseet(showLabels);


		this.graph.addSink(this.layout);
		this.layout.addAttributeSink(this.graph);

		this.viewer.enableAutoLayout(this.layout);
	}


	private static final int CHILD_COUNT_LIMIT = 1000;
	private static final int DEPTH_LIMIT = 50;
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
		String assocId;

		Node graphNode = null;
		if (eccoNode.getArtifact() != null) {
			this.artifactCount++;

			graphNode = this.graph.addNode(String.valueOf(this.artifactCount));
			graphNode.setAttribute(DEPTH_ATTRIBUTE, depth);
			assocId = eccoNode.getArtifact().getContainingNode().getContainingAssociation().getId();
			graphNode.setAttribute(ASSOC_ID_ATTRIBUTE, assocId);
			if (this.maxDepth < depth)
				this.maxDepth = depth;

//			if (depthFade) {
//				graphNode.addAttribute("ui.style", "fill-color: rgb(" + colorValue + ", " + colorValue + ", " + colorValue + ");");
//			} else {
//				graphNode.addAttribute("ui.class", "A" + ((eccoNode.getArtifact().getContainingNode().getContainingAssociation().getId() & 7) + 1));
//			}

			if (eccoNode.getChildren().size() >= this.childCountLimit || depth >= this.depthLimit) {
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
					graphChildNode.setAttribute(SUCCESSOR_COUNT_ATTRIBUTE, entry.getValue());
					graphChildNode.setAttribute(DEPTH_ATTRIBUTE, depth + 1);
					graphChildNode.setAttribute(ASSOC_ID_ATTRIBUTE, entry.getKey().getId());

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


		if (eccoNode.getChildren().size() < this.childCountLimit && depth < this.depthLimit) {
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
				initView();
				this.setDisable(false);
			});
		} else {
			Platform.runLater(() -> {
				closeView();
				this.setDisable(true);
			});
		}
	}

}
