package at.jku.isse.ecco.gui.view.graph;

import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.DependencyGraph;
import at.jku.isse.ecco.gui.ExceptionAlert;
import at.jku.isse.ecco.gui.GraphCameraControls;
import at.jku.isse.ecco.gui.TabVisibilityAware;
import at.jku.isse.ecco.service.listener.EccoListener;
import at.jku.isse.ecco.module.Condition;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import org.graphstream.graph.Graph;
import org.graphstream.graph.implementations.SingleGraph;
import org.graphstream.ui.javafx.FxGraphRenderer;
import org.graphstream.ui.layout.Layout;
import org.graphstream.ui.layout.springbox.implementations.SpringBox;
import org.graphstream.ui.fx_viewer.FxViewPanel;
import org.graphstream.ui.fx_viewer.FxViewer;
import org.graphstream.ui.view.Viewer;

import javax.swing.*;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

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
	private volatile boolean simplifyLabels = true;
	private volatile boolean hideImpliedDependencies = true;
	private final at.jku.isse.ecco.gui.LatestRefresh<Dependencies> refresher;
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


	private volatile DependencyGraph dg = null;


	public DependencyGraphView(EccoService service) {
		this.service = service;
		this.refresher = new at.jku.isse.ecco.gui.LatestRefresh<>("Dependency Graph", service, this::computeDependencies, this::showDependencies,
				failure -> new ExceptionAlert(failure).show());


		ToolBar toolBar = new ToolBar();
		this.setTop(toolBar);

		Button exportButton = at.jku.isse.ecco.gui.GraphViews.exportButton(() -> DependencyGraphView.this.graph, "dependency-graph.graphml", toolBar::setDisable);
		toolBar.getItems().add(exportButton);
		toolBar.getItems().add(new Separator());


		CheckBox showLabelsCheckbox = new CheckBox("Show Labels");
		showLabelsCheckbox.setSelected(this.showLabels);
		toolBar.getItems().add(showLabelsCheckbox);
		showLabelsCheckbox.selectedProperty().addListener((ov, old_val, new_val) -> {
			DependencyGraphView.this.showLabels = new_val;
			DependencyGraphView.this.updateGraphStylehseet();
		});
		toolBar.getItems().add(new Separator());


		CheckBox simplifyLabelsCheckbox = new CheckBox("Hide Revisions");
		// what it does: "A" for "A.1" - it said "Simplified Labels", the name of another option elsewhere
		simplifyLabelsCheckbox.setTooltip(new Tooltip("Show the conditions without feature revisions: A instead of A.1"));
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

		at.jku.isse.ecco.gui.GraphViews.zoomOnScroll(this, () -> this.view, MIN_VIEW_PERCENT, MAX_VIEW_PERCENT, null);

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
		at.jku.isse.ecco.gui.GraphViews.closeViewer(viewer, this.graph);
		view = null;
		viewer = null;
	}

	private void updateGraph() {
		this.refresher.request();
	}

	/** What the graph shows: its nodes - an association's id and label - and edges, read in the background. */
	record Dependencies(Map<String, String> labels, List<String[]> edges) {
	}

	/**
	 * The dependencies and their conditions, in the background holding the service's lock - the
	 * latest only, not while hidden (see {@link at.jku.isse.ecco.gui.LatestRefresh}): computed on the
	 * Swing thread, they took 2.5 s there for tinycc, for each commit, and read the counters while
	 * the next commit changed them.
	 */
	private Dependencies computeDependencies(EccoService service) {
		boolean hideImplied = this.hideImpliedDependencies, simplify = this.simplifyLabels;
		DependencyGraph graph = new DependencyGraph(service.getRepository().getAssociations());
		this.dg = graph;
		Map<String, String> labels = new java.util.LinkedHashMap<>();
		List<String[]> edges = new java.util.ArrayList<>();
		// once per association, not per dependency: an association is in many (2.5 s for tinycc)
		Map<Association, Condition> conditions = new IdentityHashMap<>();
		for (DependencyGraph.Dependency dep : graph.getDependencies()) {
			Condition from = conditions.computeIfAbsent(dep.getFrom(), Association::computeCondition);
			Condition to = conditions.computeIfAbsent(dep.getTo(), Association::computeCondition);
			if (hideImplied && isImplied(from, to))
				continue;
			labels.computeIfAbsent(String.valueOf(dep.getFrom().getId()), id -> "[" + (simplify ? from.toDisplayString(false) : from.toDisplayString()) + "]");
			labels.computeIfAbsent(String.valueOf(dep.getTo().getId()), id -> "[" + (simplify ? to.toDisplayString(false) : to.toDisplayString()) + "]");
			edges.add(new String[]{String.valueOf(dep.getFrom().getId()), String.valueOf(dep.getTo().getId()), String.valueOf(dep.getWeight())});
		}
		return new Dependencies(labels, edges);
	}

	/** Shows [dependencies], on the FX thread: the viewer made if there is none - kept, not made anew for each commit. */
	private void showDependencies(Dependencies dependencies) {
		if (this.viewer == null || this.view == null)
			initView();
		this.setDisable(false);
		SwingUtilities.invokeLater(() -> this.drawDependencies(dependencies));
	}

	/** Draws [dependencies] - Swing thread only, like every GraphStream change here. */
	private void drawDependencies(Dependencies dependencies) {
		// queued on the AWT thread: the repository may have been closed (and the viewer torn down) meanwhile
		if (this.viewer == null || this.view == null || !this.service.isInitialized())
			return;

		this.viewer.disableAutoLayout();
		this.layout.clear();
		this.graph.clear();
		this.view.getCamera().resetView();
		this.graph.setAttribute("ui.quality");
		this.graph.setAttribute("ui.antialias");
		this.updateGraphStylehseet();

		dependencies.labels().forEach((id, label) -> this.graph.addNode(id).setAttribute("label", label));
		for (String[] edge : dependencies.edges())
			this.graph.addEdge(edge[0] + "-" + edge[1], edge[0], edge[1], true).setAttribute("label", edge[2]);

		this.viewer.enableAutoLayout(this.layout);
		LayoutFreeze.schedule(this.viewer);
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
	/**
	 * Whether a dependency of [from] on [to] says nothing new - "Hide Implied Dependencies": wherever
	 * from is there, to is too (from implies to), as for a line in a file every variant with the
	 * line has. Package-visible for testing; it checked the other way round, hiding the
	 * dependencies that do say something and showing the self-evident ones.
	 */
	static boolean isImplied(Condition from, Condition to) {
		return to.isImpliedBy(from);
	}

	@Override
	public void statusChangedEvent(EccoService service) {
		if (service.isInitialized()) {
			Platform.runLater(() -> this.setDisable(false));
			this.refresher.request(); // noted while hidden, shown when shown
		} else {
			this.refresher.clear();
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
		this.tabVisible = tabVisible;
		// hidden: no more layout steps - its viewer redrew the graph on every step into a canvas no
		// longer in the window, whose drawing commands then piled up until the heap ran out
		// (OutOfMemoryError in GraphicsContext.writePaint); shown again, the view lays it out anew
		if (!tabVisible)
			Platform.runLater(() -> {
				if (this.viewer != null)
					this.viewer.disableAutoLayout();
			});
		this.refresher.setVisible(tabVisible);
	}

}
