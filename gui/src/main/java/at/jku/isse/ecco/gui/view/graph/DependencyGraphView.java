package at.jku.isse.ecco.gui.view.graph;

import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.core.DependencyGraph;
import at.jku.isse.ecco.gui.ExceptionAlert;
import at.jku.isse.ecco.service.listener.EccoListener;
import at.jku.isse.ecco.module.Condition;
import javafx.application.Platform;
import javafx.embed.swing.SwingNode;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;
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

public class DependencyGraphView extends BorderPane implements EccoListener {

	private EccoService service;

	private Graph graph;
	private Layout layout;
	private Viewer viewer;
	private ViewPanel view;

	private boolean showLabels = true;
	private boolean simplifyLabels = true;
	private boolean hideImpliedDependencies = true;
	private boolean hideTransitiveDependencies = true;


	private DependencyGraph dg = null;


	public DependencyGraphView(EccoService service) {
		this.service = service;


		ToolBar toolBar = new ToolBar();
		this.setTop(toolBar);

		Button refreshButton = new Button("Refresh");
		toolBar.getItems().add(refreshButton);
		refreshButton.setOnAction(e -> {
			toolBar.setDisable(true);
			SwingUtilities.invokeLater(() -> {
				dg = new DependencyGraph(DependencyGraphView.this.service.getRepository().getAssociations());
				DependencyGraphView.this.updateGraph();
				Platform.runLater(() -> toolBar.setDisable(false));
			});
		});
		toolBar.getItems().add(new Separator());


		Button exportButton = new Button("Export");
		toolBar.getItems().add(exportButton);
		exportButton.setOnAction(ae -> {
			toolBar.setDisable(true);

			FileChooser fileChooser = new FileChooser();
			File selectedFile = fileChooser.showSaveDialog(DependencyGraphView.this.getScene().getWindow());

			if (selectedFile != null) {
				FileSink out = FileSinkFactory.sinkFor(selectedFile.toString());
				if (out != null) {
					try {
						out.writeAll(DependencyGraphView.this.graph, selectedFile.toString());
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
		toolBar.getItems().add(new Separator());


		CheckBox showLabelsCheckbox = new CheckBox("Show Labels");
		showLabelsCheckbox.setSelected(this.showLabels);
		toolBar.getItems().add(showLabelsCheckbox);
		showLabelsCheckbox.selectedProperty().addListener((ov, old_val, new_val) -> {
			DependencyGraphView.this.showLabels = new_val;
			DependencyGraphView.this.updateGraphStylehseet();
		});
		toolBar.getItems().add(new Separator());


		CheckBox simplifyLabelsCheckbox = new CheckBox("Simplified Labels");
		simplifyLabelsCheckbox.setSelected(this.simplifyLabels);
		toolBar.getItems().add(simplifyLabelsCheckbox);
		simplifyLabelsCheckbox.selectedProperty().addListener((ov, old_val, new_val) -> {
			DependencyGraphView.this.simplifyLabels = new_val;
			DependencyGraphView.this.updateGraph();
		});
		toolBar.getItems().add(new Separator());


		CheckBox hideImpliedDependenciesCheckBox = new CheckBox("Hide Implied Dependencies");
		hideImpliedDependenciesCheckBox.setSelected(this.hideImpliedDependencies);
		toolBar.getItems().add(hideImpliedDependenciesCheckBox);
		hideImpliedDependenciesCheckBox.selectedProperty().addListener((ov, old_val, new_val) -> {
			DependencyGraphView.this.hideImpliedDependencies = new_val;
			DependencyGraphView.this.updateGraph();
		});
		toolBar.getItems().add(new Separator());


		CheckBox hideTransitiveDependenciesCheckBox = new CheckBox("Hide Transitive Dependencies");
		hideTransitiveDependenciesCheckBox.setSelected(this.hideTransitiveDependencies);
		toolBar.getItems().add(hideTransitiveDependenciesCheckBox);
		hideTransitiveDependenciesCheckBox.selectedProperty().addListener((ov, old_val, new_val) -> {
			DependencyGraphView.this.hideTransitiveDependencies = new_val;
			DependencyGraphView.this.updateGraph();
		});
		toolBar.getItems().add(new Separator());


		System.setProperty("org.graphstream.ui.renderer", "org.graphstream.ui.j2dviewer.J2DGraphRenderer");


		this.graph = new SingleGraph("DependencyGraph");

		this.layout = new SpringBox(false);
		this.graph.addSink(this.layout);
		this.layout.addAttributeSink(this.graph);

		this.viewer = new Viewer(this.graph, Viewer.ThreadingModel.GRAPH_IN_ANOTHER_THREAD);
		//this.viewer.enableAutoLayout(this.layout);
		this.view = this.viewer.addDefaultView(false); // false indicates "no JFrame"

		SwingNode swingNode = new SwingNode();

		SwingUtilities.invokeLater(() -> swingNode.setContent(view));


		this.setOnScroll(event -> view.getCamera().setViewPercent(Math.max(0.1, Math.min(1.0, view.getCamera().getViewPercent() - 0.05 * event.getDeltaY() / event.getMultiplierY()))));


		this.setCenter(swingNode);


		showLabelsCheckbox.setSelected(this.showLabels);


		service.addListener(this);

		if (!service.isInitialized())
			this.setDisable(true);
	}


	private void updateGraphStylehseet() {
		String textMode = "text-mode: normal; ";
		if (!this.showLabels)
			textMode = "text-mode: hidden; ";

		this.graph.addAttribute("ui.stylesheet",
				"edge { " + textMode + " size: 1px; shape: blob; arrow-shape: none; arrow-size: 3px, 3px; } " +
						"node { " + textMode + " text-background-mode: plain;  shape: circle; size: 10px; stroke-mode: plain; stroke-color: #000000; stroke-width: 1px; } ");
	}

	private void updateGraph() {
		this.viewer.disableAutoLayout();

		this.graph.removeSink(this.layout);
		this.layout.removeAttributeSink(this.graph);
		this.layout.clear();
		this.graph.clear();

		this.view.getCamera().resetView();


		//this.graph.setStrict(false);

		this.graph.addAttribute("ui.quality");
		this.graph.addAttribute("ui.antialias");

		this.updateGraphStylehseet();


		if (dg == null)
			dg = new DependencyGraph(this.service.getRepository().getAssociations());

		for (DependencyGraph.Dependency dep : dg.getDependencies()) {
			Condition depFromCondition = dep.getFrom().computeCondition();
			Condition depToCondition = dep.getTo().computeCondition();
			if (!hideImpliedDependencies || !depFromCondition.implies(depToCondition)) {
//			boolean implied = Condition.implies(dep.getFrom().getPresenceCondition(), dep.getTo().getPresenceCondition());
				Node from = this.graph.getNode(String.valueOf(dep.getFrom().getId()));
				if (from == null) {
					from = this.graph.addNode(String.valueOf(dep.getFrom().getId()));
					if (simplifyLabels)
						from.setAttribute("label", "[" + depFromCondition.getSimpleModuleRevisionConditionString() + "]");
					else
						from.setAttribute("label", "[" + depFromCondition.getModuleRevisionConditionString() + "]");
//				from.setAttribute("implied", implied);
//				if (implied)
//					from.setAttribute("hide");
				}
//			if ((boolean) from.getAttribute("implied") && !implied) {
//				from.setAttribute("implied", false);
//				from.removeAttribute("hide");
//			}
				Node to = this.graph.getNode(String.valueOf(dep.getTo().getId()));
				if (to == null) {
					to = this.graph.addNode(String.valueOf(dep.getTo().getId()));
					if (simplifyLabels)
						to.setAttribute("label", "[" + depToCondition.getSimpleModuleRevisionConditionString() + "]");
					else
						to.setAttribute("label", "[" + depToCondition.getModuleRevisionConditionString() + "]");
//				to.setAttribute("implied", implied);
//				if (implied)
//					to.setAttribute("hide");
				}
//			if ((boolean) to.getAttribute("implied") && !implied) {
//				to.setAttribute("implied", false);
//				to.removeAttribute("hide");
//			}
				Edge edge = this.graph.addEdge(dep.getFrom().getId() + "-" + dep.getTo().getId(), from, to, true);
				edge.setAttribute("label", String.valueOf(dep.getWeight()));
//			if (implied)
//				edge.setAttribute("hide");
			}
		}


		this.graph.addSink(this.layout);
		this.layout.addAttributeSink(this.graph);

		this.viewer.enableAutoLayout(this.layout);
	}


	@Override
	public void statusChangedEvent(EccoService service) {
		if (service.isInitialized()) {
			Platform.runLater(() -> {
				//this.updateGraph();
				this.setDisable(false);
			});
		} else {
			Platform.runLater(() -> this.setDisable(true));
		}
	}

}
