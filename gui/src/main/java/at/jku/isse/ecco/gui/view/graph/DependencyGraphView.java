package at.jku.isse.ecco.gui.view.graph;

import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.core.DependencyGraph;
import at.jku.isse.ecco.gui.ExceptionAlert;
import at.jku.isse.ecco.gui.GraphCameraControls;
import at.jku.isse.ecco.gui.TabVisibilityAware;
import at.jku.isse.ecco.service.listener.EccoListener;
import at.jku.isse.ecco.module.Condition;
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

public class DependencyGraphView extends BorderPane implements EccoListener, TabVisibilityAware {

	private final EccoService service;

	private final Graph graph;
	private final Layout layout;
	private FxViewer viewer;
	private FxViewPanel view;

	/** Shared with {@link GraphCameraControls}'s Zoom In/Out buttons, so they clamp to the exact same range scroll-zoom already does. */
	private static final double MIN_VIEW_PERCENT = 0.1;
	private static final double MAX_VIEW_PERCENT = 1.0;

	private boolean showLabels = true;
	private boolean simplifyLabels = true;
	private boolean hideImpliedDependencies = true;
	private boolean hideTransitiveDependencies = true;

	/**
	 * See {@code ArtifactGraphView#tabVisible} for the full rationale: without this,
	 * {@link #statusChangedEvent} tore down and recreated the GraphStream {@link FxViewer} (each with
	 * its own {@code GRAPH_IN_ANOTHER_THREAD} background thread) on every single commit, even while
	 * this tab was never shown - a "Commit Multiple Versions" batch fires this once per folder in a
	 * tight loop with nothing throttling it, so back-to-back teardown/recreate cycles raced the
	 * previous viewer's still-running background thread against the new one, crashing deep inside
	 * GraphStream's {@code SourceBase}/{@code ThreadProxyPipe} (NoSuchElementException/
	 * NullPointerException from concurrent access to its internal event queue).
	 */
	private volatile boolean tabVisible = true;


	private DependencyGraph dg = null;


	public DependencyGraphView(EccoService service) {
		this.service = service;


		ToolBar toolBar = new ToolBar();
		this.setTop(toolBar);

		Button exportButton = new Button("Export");
		toolBar.getItems().add(exportButton);
		exportButton.setOnAction(ae -> {
			toolBar.setDisable(true);

			FileChooser fileChooser = new FileChooser();
			// FileSinkFactory.sinkFor() picks a writer purely off the file extension and returns
			// null (see the "Unknown file extension" branch below) for anything it doesn't
			// recognize - list the formats GraphStream actually supports so the save dialog
			// doesn't let that happen by default.
			fileChooser.getExtensionFilters().addAll(
					new FileChooser.ExtensionFilter("GraphML (*.graphml)", "*.graphml"),
					new FileChooser.ExtensionFilter("GEXF (*.gexf)", "*.gexf"),
					new FileChooser.ExtensionFilter("DOT (*.dot)", "*.dot"),
					new FileChooser.ExtensionFilter("DGS (*.dgs)", "*.dgs"));
			fileChooser.setInitialFileName("dependency-graph.graphml");
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

		toolBar.getItems().addAll(GraphCameraControls.build(() -> this.view, MIN_VIEW_PERCENT, MAX_VIEW_PERCENT, () -> {
		}));


		System.setProperty("org.graphstream.ui.renderer", "org.graphstream.ui.j2dviewer.J2DGraphRenderer");


		this.graph = new SingleGraph("DependencyGraph");

		this.layout = new SpringBox(false);
		this.graph.addSink(this.layout);
		this.layout.addAttributeSink(this.graph);

		this.setOnScroll(event -> {
			if (null != view) {
				view.getCamera().setViewPercent(Math.max(MIN_VIEW_PERCENT, Math.min(MAX_VIEW_PERCENT,
						view.getCamera().getViewPercent() - 0.05 * event.getDeltaY() / event.getMultiplierY())));
			}
		});

		showLabelsCheckbox.setSelected(this.showLabels);

		service.addListener(this);
		Platform.runLater(() -> statusChangedEvent(service));
	}


	private void updateGraphStylehseet() {
		String textMode = "text-mode: normal; ";
		if (!this.showLabels)
			textMode = "text-mode: hidden; ";

		this.graph.setAttribute("ui.stylesheet",
				"edge { " + textMode + " size: 1px; shape: blob; arrow-shape: none; arrow-size: 3px, 3px; } " +
						"node { " + textMode + " text-background-mode: plain;  shape: circle; size: 10px; stroke-mode: plain; stroke-color: #000000; stroke-width: 1px; } ");
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
		try {
			viewer.close();
		} catch (Exception ignored) {
			// GraphStream's FxGraphRenderer can NPE while tearing down a view that was
			// created but never rendered yet (e.g. closed again in quick succession by
			// back-to-back status-changed events during a batch commit) - harmless here
			// since the viewer/view are discarded immediately after anyway.
		}
		view = null;
		viewer = null;
	}

	private void updateGraph() {
		assert viewer != null && view != null;

		this.viewer.disableAutoLayout();

		this.graph.removeSink(this.layout);
		this.layout.removeAttributeSink(this.graph);
		this.layout.clear();
		this.graph.clear();

		this.view.getCamera().resetView();


		//this.graph.setStrict(false);

		this.graph.setAttribute("ui.quality");
		this.graph.setAttribute("ui.antialias");

		this.updateGraphStylehseet();


		// always rebuilt, not just when null: this is also what the automatic refresh path
		// (statusChangedEvent/setTabVisible, below) calls now, and a commit changing the
		// repository's associations should be reflected the same way the manual Refresh button
		// already does (which always rebuilds), not show whatever was there at the last refresh.
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


	/**
	 * Fires on open/close AND, notably, after every commit too - see {@code ArtifactGraphView#
	 * statusChangedEvent}. Skips the (expensive, GraphStream-viewer-recreating) refresh entirely
	 * while {@link #tabVisible} is false; {@link #setTabVisible} catches up with a single fresh
	 * render once the tab is shown again.
	 * <p>
	 * Used to only call {@link #initView()} here, leaving the graph itself empty until the user
	 * manually clicked Refresh - {@link #updateGraph()} (Swing-thread only, like every other
	 * GraphStream mutation in this class) now runs right after, so opening a repository, switching
	 * commits, or switching back to this tab all show current data without a manual click first.
	 */
	@Override
	public void statusChangedEvent(EccoService service) {
		if (service.isInitialized()) {
			if (!this.tabVisible) {
				Platform.runLater(() -> this.setDisable(false));
				return;
			}
			Platform.runLater(() -> {
				initView();
				this.setDisable(false);
				SwingUtilities.invokeLater(this::updateGraph);
			});
		} else {
			Platform.runLater(() -> {
				closeView();
				this.setDisable(true);
			});
		}
	}

	/**
	 * Called by the containing tab whenever it's selected or deselected - see
	 * {@code ArtifactGraphView#setTabVisible} for the full rationale (skip the expensive refresh
	 * while hidden, catch up with one fresh render when shown again).
	 */
	@Override
	public void setTabVisible(boolean tabVisible) {
		boolean becameVisible = tabVisible && !this.tabVisible;
		this.tabVisible = tabVisible;
		if (becameVisible && this.service.isInitialized()) {
			Platform.runLater(() -> {
				initView();
				this.setDisable(false);
				SwingUtilities.invokeLater(this::updateGraph);
			});
		}
	}

}
