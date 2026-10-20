package at.jku.isse.ecco.gui.view.graph;

import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.DependencyGraph;
import at.jku.isse.ecco.gui.EditableSpinner;
import at.jku.isse.ecco.gui.ExceptionAlert;
import at.jku.isse.ecco.gui.GraphCameraControls;
import at.jku.isse.ecco.gui.TabVisibilityAware;
import at.jku.isse.ecco.service.listener.EccoListener;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.module.Condition;
import at.jku.isse.ecco.module.ConditionText;
import at.jku.isse.ecco.module.ModuleRevision;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

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
	 * One node per feature instead of per association: for zip4j's Git import the associations made
	 * 331 nodes and 804 edges, labelled with whole conditions - unreadable; its 49 features are not.
	 */
	private volatile boolean byFeature = true;
	/** Edges lighter than this are not shown: by feature, zip4j had 1728 edges, 61 of weight 5 or more. */
	private volatile int minWeight = DEFAULT_MIN_WEIGHT;
	static final int DEFAULT_MIN_WEIGHT = 5;
	private final Label shownLabel = new Label();

	/**
	 * The absorbed terms of the associations' conditions, kept from one refresh to the next until a
	 * commit or another repository: computed anew, they took 5 s for zip4j's Git import on every
	 * change of the minimum weight or another option.
	 */
	private volatile Map<Association, List<ModuleRevision>> termsCache = newTermsCache();

	private static Map<Association, List<ModuleRevision>> newTermsCache() {
		return java.util.Collections.synchronizedMap(new IdentityHashMap<>());
	}

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


		CheckBox byFeatureCheckbox = new CheckBox("Group by Feature");
		byFeatureCheckbox.setTooltip(new Tooltip("One node per feature, the associations' dependencies added up between the features of their conditions"));
		byFeatureCheckbox.setSelected(this.byFeature);
		toolBar.getItems().add(byFeatureCheckbox);
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
		// a feature's name has no revisions to hide
		simplifyLabelsCheckbox.setDisable(this.byFeature);
		byFeatureCheckbox.selectedProperty().addListener((ov, old_val, new_val) -> {
			DependencyGraphView.this.byFeature = new_val;
			simplifyLabelsCheckbox.setDisable(new_val);
			DependencyGraphView.this.updateGraph();
		});


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

		Spinner<Integer> minWeightSpinner = new EditableSpinner(1, 1_000_000, this.minWeight);
		minWeightSpinner.setPrefWidth(90);
		minWeightSpinner.setTooltip(new Tooltip("Hide the dependencies lighter than this"));
		minWeightSpinner.valueProperty().addListener((obs, oldValue, newValue) -> {
			DependencyGraphView.this.minWeight = newValue;
			DependencyGraphView.this.updateGraph();
		});
		toolBar.getItems().addAll(new Label("Min. Weight: "), minWeightSpinner, this.shownLabel);
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

	/**
	 * What the graph shows: its nodes - an association's or feature's id and label - and edges, read
	 * in the background; [all] edges there were before the ones lighter than the minimum weight were left out.
	 */
	record Dependencies(Map<String, String> labels, List<String[]> edges, int all) {
	}

	/**
	 * The dependencies and their conditions, in the background holding the service's lock - the
	 * latest only, not while hidden (see {@link at.jku.isse.ecco.gui.LatestRefresh}): computed on the
	 * Swing thread, they took 2.5 s there for tinycc, for each commit, and read the counters while
	 * the next commit changed them.
	 */
	private Dependencies computeDependencies(EccoService service) {
		boolean hideImplied = this.hideImpliedDependencies, simplify = this.simplifyLabels, byFeature = this.byFeature;
		int minWeight = this.minWeight;
		DependencyGraph graph = new DependencyGraph(service.getRepository().getAssociations());
		this.dg = graph;
		Map<String, String> labels = new LinkedHashMap<>();
		List<String[]> edges = new java.util.ArrayList<>();
		int all = 0;
		// once per association, not per dependency: an association is in many (2.5 s for tinycc);
		// absorbed: zip4j's Git import had 8.1M module revisions in its conditions, 34k absorbed
		Map<Association, List<ModuleRevision>> terms = this.termsCache;
		Map<Association, Set<String>> features = new IdentityHashMap<>();
		Map<List<String>, Double> featureWeights = new LinkedHashMap<>();
		for (DependencyGraph.Dependency dep : graph.getDependencies()) {
			List<ModuleRevision> fromTerms = terms.computeIfAbsent(dep.getFrom(), a -> a.computeCondition().getAbsorbedModuleRevisions());
			List<ModuleRevision> toTerms = terms.computeIfAbsent(dep.getTo(), a -> a.computeCondition().getAbsorbedModuleRevisions());
			if (hideImplied && isImplied(fromTerms, toTerms))
				continue;
			if (byFeature) {
				addFeatureDependency(featureWeights, features.computeIfAbsent(dep.getFrom(), a -> features(fromTerms)),
						features.computeIfAbsent(dep.getTo(), a -> features(toTerms)), dep.getWeight());
				continue;
			}
			all++;
			if (dep.getWeight() < minWeight)
				continue;
			labels.computeIfAbsent(String.valueOf(dep.getFrom().getId()), id -> "[" + displayString(fromTerms, !simplify) + "]");
			labels.computeIfAbsent(String.valueOf(dep.getTo().getId()), id -> "[" + displayString(toTerms, !simplify) + "]");
			edges.add(new String[]{String.valueOf(dep.getFrom().getId()), String.valueOf(dep.getTo().getId()), String.valueOf(dep.getWeight())});
		}
		if (byFeature) {
			all = featureWeights.size();
			featureWeights.forEach((pair, weight) -> {
				if (weight < minWeight)
					return;
				// prefixed: a feature's name is not an association's id
				String fromId = "feature:" + pair.get(0), toId = "feature:" + pair.get(1);
				labels.putIfAbsent(fromId, pair.get(0));
				labels.putIfAbsent(toId, pair.get(1));
				edges.add(new String[]{fromId, toId, formatWeight(weight)});
			});
		}
		return new Dependencies(labels, edges, all);
	}

	/**
	 * The names of the features in a condition's absorbed [terms] - present in one of them; negated
	 * ones do not count - or {@link Condition#NOT_TRACED} for an association traced to no feature.
	 */
	static Set<String> features(List<ModuleRevision> terms) {
		Set<String> names = new TreeSet<>();
		for (ModuleRevision term : terms)
			for (FeatureRevision featureRevision : term.getPos())
				names.add(featureRevision.getFeature().getName());
		if (names.isEmpty())
			names.add(Condition.NOT_TRACED);
		return names;
	}

	/**
	 * Adds a dependency of weight [weight] between associations of features [from] and [to] to
	 * [weights], keyed by (from feature, to feature): shared out over the pairs, not given to each in
	 * full - with the whole weight, an association of ten features made a hundred heavy edges, and
	 * zip4j's 49 features 1728 edges, most of them of weight 5 or more. A pair of the same feature is
	 * a dependency within it, not drawn; its share is left out.
	 */
	static void addFeatureDependency(Map<List<String>, Double> weights, Set<String> from, Set<String> to, int weight) {
		double share = (double) weight / (from.size() * to.size());
		for (String fromFeature : from)
			for (String toFeature : to)
				if (!fromFeature.equals(toFeature))
					weights.merge(List.of(fromFeature, toFeature), share, Double::sum);
	}

	/** {@link Condition#toDisplayString(boolean)} from the absorbed [terms], not absorbing them again. */
	static String displayString(List<ModuleRevision> terms, boolean withRevisions) {
		List<java.util.SortedSet<String>> literals = new java.util.ArrayList<>();
		for (ModuleRevision term : terms)
			literals.add(ConditionText.literals(term, withRevisions));
		return literals.isEmpty() ? Condition.NOT_TRACED : ConditionText.join(literals);
	}

	/** A shared-out weight for an edge's label: whole from 10 on, one decimal below. */
	static String formatWeight(double weight) {
		return weight >= 10 ? String.valueOf(Math.round(weight)) : String.format(Locale.ROOT, "%.1f", weight);
	}

	/** Shows [dependencies], on the FX thread: the viewer made if there is none - kept, not made anew for each commit. */
	private void showDependencies(Dependencies dependencies) {
		if (this.viewer == null || this.view == null)
			initView();
		this.setDisable(false);
		this.shownLabel.setText(" " + dependencies.edges().size() + " of " + dependencies.all() + " shown");
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
		return isImplied(from.getAbsorbedModuleRevisions(), to.getAbsorbedModuleRevisions());
	}

	/**
	 * {@link Condition#isImpliedBy} on the absorbed terms of the two conditions - each term of from
	 * has a term of to within it - with the same answer: a term absorbed is within one kept, on
	 * either side. On all of them, zip4j's Git import (up to 261k terms in one condition, compared
	 * pairwise for each dependency) kept the graph from showing for many minutes.
	 */
	static boolean isImplied(List<ModuleRevision> fromTerms, List<ModuleRevision> toTerms) {
		for (ModuleRevision fromTerm : fromTerms) {
			boolean within = false;
			for (ModuleRevision toTerm : toTerms) {
				if (toTerm.isImpliedBy(fromTerm)) {
					within = true;
					break;
				}
			}
			if (!within)
				return false;
		}
		return true;
	}

	@Override
	public void statusChangedEvent(EccoService service) {
		this.termsCache = newTermsCache(); // after a commit, or for another repository
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
