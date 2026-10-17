package at.jku.isse.ecco.gui.view;

import at.jku.isse.ecco.gui.TabVisibilityAware;
import at.jku.isse.ecco.gui.TableColumns;
import at.jku.isse.ecco.gui.TaskFailures;
import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.adapter.ArtifactWriter;
import at.jku.isse.ecco.composition.LazyCompositionRootNode;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.mining.ModuleConditionBridge;
import at.jku.isse.ecco.mining.PresenceConditionMinimizer;
import at.jku.isse.ecco.service.listener.EccoListener;
import at.jku.isse.ecco.module.Condition;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.repository.Repository;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.VPos;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Loads its charts when shown and again after every change of the repository while shown - there
 * was a Refresh button to press, and the charts were empty until then.
 */
public class ChartsView extends BorderPane implements EccoListener, TabVisibilityAware {

	/** More bars than this were too thin to read or label. */
	static final int MAX_ASSOCIATION_BARS = 20;
	private static final double BAR_HEIGHT = 22;
	private static final double BAR_CHART_PADDING = 90; // the value axis and its label
	private static final double MIN_BAR_CHART_HEIGHT = 200;

	private EccoService service;

	/** "Advanced: Modules per Order" - computed only while it is open. */
	private TitledPane modulesPerOrderPane;

	/** See {@link at.jku.isse.ecco.gui.view.graph.ArtifactGraphView#tabVisible}: nothing is computed while hidden. */
	private volatile boolean tabVisible = true;

	/** A later load supersedes one still running; see {@link #refresh()}. */
	private final AtomicLong refreshGeneration = new AtomicLong();

	private XYChart.Series<Number, String> artifactsPerAssociationSeries;
	private CategoryAxis associationAxis;
	private BarChart<Number, String> artifactsPerAssociationChart;
	private XYChart.Series<Number, String> revisionsPerFeatureSeries;
	private BarChart<Number, String> revisionsPerFeatureChart;
	private XYChart.Series<Number, Number> artifactsPerDepthSeries;
	private XYChart.Series<String, Number> modulesPerOrderSeries;
	private XYChart.Series<String, Number> modulesPerOrderAbsorbedSeries;

	public ChartsView(EccoService service) {
		this.service = service;


		ScrollPane scrollPane = new ScrollPane();
		this.setCenter(scrollPane);

		FlowPane centerBox = new FlowPane(Orientation.VERTICAL);
		centerBox.setHgap(10);
		centerBox.setVgap(10);
		centerBox.setPadding(new Insets(10, 10, 10, 10));
		centerBox.setRowValignment(VPos.TOP);
		centerBox.prefWidthProperty().bind(scrollPane.widthProperty().subtract(20));
		centerBox.prefHeightProperty().bind(scrollPane.heightProperty().subtract(20));
		scrollPane.setContent(centerBox);


		{ // artifacts per association: the largest first, as bars - a pie of dozens of slices was unreadable
			TitledPane artifactsPerAssociationPane = new TitledPane();
			artifactsPerAssociationPane.setAnimated(false);
			artifactsPerAssociationPane.setText("Artifacts per Association");

			this.associationAxis = new CategoryAxis();
			this.artifactsPerAssociationChart = horizontalBarChart(this.associationAxis, "Number of Artifacts");
			this.artifactsPerAssociationSeries = new XYChart.Series<>();
			this.artifactsPerAssociationChart.getData().add(this.artifactsPerAssociationSeries);

			artifactsPerAssociationPane.setContent(this.artifactsPerAssociationChart);

			centerBox.getChildren().add(artifactsPerAssociationPane);
		}

		{ // revisions per feature: the most first, as bars
			TitledPane titledPane = new TitledPane();
			titledPane.setAnimated(false);
			titledPane.setText("Revisions per Feature");

			this.revisionsPerFeatureChart = horizontalBarChart(new CategoryAxis(), "Number of Revisions");
			this.revisionsPerFeatureSeries = new XYChart.Series<>();
			this.revisionsPerFeatureChart.getData().add(this.revisionsPerFeatureSeries);

			titledPane.setContent(this.revisionsPerFeatureChart);

			centerBox.getChildren().add(titledPane);
		}


		{ // artifacts per depth
			TitledPane artifactsPerDepthPane = new TitledPane();
			artifactsPerDepthPane.setAnimated(false);
			artifactsPerDepthPane.setText("Artifacts per Depth");

			final NumberAxis xAxis = new NumberAxis();
			final NumberAxis yAxis = new NumberAxis();
			xAxis.setLabel("Depth");
			xAxis.setTickUnit(1.0);
			xAxis.setMinorTickVisible(false);
			yAxis.setLabel("Number of Artifacts");
			yAxis.setTickUnit(1.0);
			yAxis.setMinorTickVisible(false);
			xAxis.setTickLabelFormatter(WHOLE_NUMBERS);
			yAxis.setTickLabelFormatter(WHOLE_NUMBERS);
			final LineChart<Number, Number> artifactsPerDepthChart = new LineChart<>(xAxis, yAxis);
			// one series, named like its axis
			artifactsPerDepthChart.setLegendVisible(false);

			this.artifactsPerDepthSeries = new XYChart.Series<>();
			this.artifactsPerDepthSeries.setName("Number of Artifacts at Depth");

			artifactsPerDepthChart.getData().setAll(this.artifactsPerDepthSeries);

			artifactsPerDepthPane.setContent(artifactsPerDepthChart);

			centerBox.getChildren().add(artifactsPerDepthPane);
		}


		{ // modules per order: raw vs. absorbed (X+XY=X redundant terms collapsed), side by side, so
			// the gap between the two series is a direct visualization of how much of an association's
			// stored module lattice is accumulated redundancy rather than real structural complexity -
			// see CONSTRAINT_MINING_DESIGN.md's "Surplus-module suppression" section
			TitledPane modulesPerOrderPane = new TitledPane();
			this.modulesPerOrderPane = modulesPerOrderPane;
			// computed only while open: it took most of a refresh (9.5 of 11 s for tinycc), on every change
			modulesPerOrderPane.expandedProperty().addListener((o, wasOpen, open) -> {
				if (open && this.service.isInitialized() && this.tabVisible)
					this.refresh();
			});
			modulesPerOrderPane.setAnimated(false);
			modulesPerOrderPane.setText("Advanced: Modules per Order (raw vs. absorbed)");
			// how the conditions are stored, not what the repository holds: folded away unless asked for
			modulesPerOrderPane.setExpanded(false);

			final CategoryAxis xAxis = new CategoryAxis();
			final NumberAxis yAxis = new NumberAxis();
			final BarChart<String, Number> bc = new BarChart<>(xAxis, yAxis);
			xAxis.setLabel("Order");
			yAxis.setLabel("Number of Modules");
			yAxis.setTickUnit(1.0);
			yAxis.setMinorTickVisible(false);

			this.modulesPerOrderSeries = new XYChart.Series<>();
			this.modulesPerOrderSeries.setName("Raw");

			this.modulesPerOrderAbsorbedSeries = new XYChart.Series<>();
			this.modulesPerOrderAbsorbedSeries.setName("Absorbed");

			bc.getData().setAll(this.modulesPerOrderSeries, this.modulesPerOrderAbsorbedSeries);

			modulesPerOrderPane.setContent(bc);

			centerBox.getChildren().add(modulesPerOrderPane);
		}


		Platform.runLater(() -> statusChangedEvent(service));

		service.addListener(this);
	}

	private void refresh() {
		long generation = this.refreshGeneration.incrementAndGet();
		boolean advanced = this.modulesPerOrderPane.isExpanded();

		Task<Void> refreshTask = new Task<>() {
			@Override
			public Void call() throws EccoException {
				Repository repository = ChartsView.this.service.getRepository();

				// artifacts per depth
				LazyCompositionRootNode compRootNode = new LazyCompositionRootNode();
				for (Association association : repository.getAssociations()) {
					compRootNode.addOrigNode(association.getRootNode());
				}
				Map<Integer, Integer> artifactsPerDepth = compRootNode.countArtifactsPerDepth();

				// modules per order: raw (every module-lattice entry an association has ever
				// accumulated, including redundant restatements - see CONSTRAINT_MINING_DESIGN.md's
				// "Surplus-module suppression" section) and absorbed (X+XY=X collapsed, i.e. the
				// minimal set of terms actually needed) side by side, so the chart itself shows how
				// much of the raw count is accumulation noise rather than real structural complexity
				final Map<Integer, Integer> modulesPerOrderMap = new TreeMap<>();
				final Map<Integer, Integer> modulesPerOrderAbsorbedMap = new TreeMap<>();
				for (Association association : advanced ? repository.getAssociations() : List.<Association>of()) {
					Condition condition = association.computeCondition();
					for (Module module : condition.getModules().keySet()) {
						modulesPerOrderMap.merge(module.getOrder(), 1, Integer::sum);
					}
					List<PresenceConditionMinimizer.Term> absorbedTerms =
							PresenceConditionMinimizer.absorb(ModuleConditionBridge.toRevisionTerms(condition));
					for (PresenceConditionMinimizer.Term term : absorbedTerms) {
						int order = term.positive.size() + term.negative.size() - 1;
						modulesPerOrderAbsorbedMap.merge(order, 1, Integer::sum);
					}
				}

				List<Bar> associationBars = new ArrayList<>();
				Set<String> shortIds = new HashSet<>();
				Map<String, Association> byLabel = new HashMap<>();
				for (Association association : repository.getAssociations()) {
					int numArtifacts = association.getRootNode().countArtifacts();
					if (numArtifacts > 0) {
						// the short id the tables show; the whole one should two share a prefix
						String id = association.getId();
						String label = id.length() > TableColumns.SHORT_ID_LENGTH && shortIds.add(id.substring(0, TableColumns.SHORT_ID_LENGTH))
								? id.substring(0, TableColumns.SHORT_ID_LENGTH) : id;
						associationBars.add(new Bar(label, numArtifacts, id));
						byLabel.put(label, association);
					}
				}
				int associationCount = associationBars.size();
				// the conditions of the bars shown only: every association's took 1.3 s for tinycc
				associationBars = largestFirst(associationBars, MAX_ASSOCIATION_BARS).stream()
						.map(bar -> new Bar(bar.label(), bar.value(), bar.tooltip() + "\n" + byLabel.get(bar.label()).computeCondition().toDisplayString()))
						.toList();
				List<Bar> featureBars = new ArrayList<>();
				for (Feature feature : repository.getFeatures()) {
					int numRevisions = feature.getRevisions().size();
					if (numRevisions > 0)
						featureBars.add(new Bar(feature.getName(), numRevisions, feature.getName()));
				}
				featureBars = largestFirst(featureBars, Integer.MAX_VALUE);
				List<Bar> shownAssociationBars = associationBars, shownFeatureBars = featureBars;

				Platform.runLater(() -> {
					if (ChartsView.this.refreshGeneration.get() != generation)
						return; // a newer load shows its own

					show(ChartsView.this.artifactsPerAssociationChart, ChartsView.this.artifactsPerAssociationSeries, shownAssociationBars);
					ChartsView.this.associationAxis.setLabel(associationCount > shownAssociationBars.size()
							? "The " + shownAssociationBars.size() + " largest of " + associationCount : null);
					show(ChartsView.this.revisionsPerFeatureChart, ChartsView.this.revisionsPerFeatureSeries, shownFeatureBars);

					ChartsView.this.artifactsPerDepthSeries.getData().clear();
					for (Map.Entry<Integer, Integer> entry : artifactsPerDepth.entrySet()) {
						ChartsView.this.artifactsPerDepthSeries.getData().add(new XYChart.Data<>(entry.getKey(), entry.getValue()));
					}

					if (!advanced)
						return; // folded away: not computed, the last shown kept
					final ObservableList<XYChart.Data<String, Number>> maxOrderData = ChartsView.this.modulesPerOrderSeries.getData();
					maxOrderData.clear();
					modulesPerOrderMap.forEach((key, value) -> maxOrderData.add(new XYChart.Data<>(Integer.toString(key), value)));

					final ObservableList<XYChart.Data<String, Number>> maxOrderAbsorbedData = ChartsView.this.modulesPerOrderAbsorbedSeries.getData();
					maxOrderAbsorbedData.clear();
					modulesPerOrderAbsorbedMap.forEach((key, value) -> maxOrderAbsorbedData.add(new XYChart.Data<>(Integer.toString(key), value)));
				});
				return null;
			}
		};

		TaskFailures.reportAndReenable(refreshTask);
		Thread thread = new Thread(refreshTask);
		thread.setDaemon(true);
		thread.start();
	}

	/** Counts and depths: no "0.5" between them. */
	private static final javafx.util.StringConverter<Number> WHOLE_NUMBERS = new javafx.util.StringConverter<>() {
		@Override
		public String toString(Number value) {
			return value.doubleValue() == Math.rint(value.doubleValue()) ? Long.toString(value.longValue()) : "";
		}

		@Override
		public Number fromString(String text) {
			return Double.valueOf(text);
		}
	};

	/** One bar: its label on the axis, its length, and what its tooltip says. */
	record Bar(String label, int value, String tooltip) {
	}

	/** The [max] largest, largest first; ties by label, so a reload keeps the order. */
	static List<Bar> largestFirst(List<Bar> bars, int max) {
		return bars.stream()
				.sorted(Comparator.comparingInt(Bar::value).reversed().thenComparing(Bar::label))
				.limit(max)
				.toList();
	}

	/** Bars along the x axis, their names down the y axis. */
	private static BarChart<Number, String> horizontalBarChart(CategoryAxis categoryAxis, String valueLabel) {
		NumberAxis valueAxis = new NumberAxis();
		valueAxis.setLabel(valueLabel);
		valueAxis.setMinorTickVisible(false);
		valueAxis.setTickLabelFormatter(WHOLE_NUMBERS);
		BarChart<Number, String> chart = new BarChart<>(valueAxis, categoryAxis);
		chart.setLegendVisible(false);
		chart.setAnimated(false);
		chart.setCategoryGap(4);
		return chart;
	}

	/** Shows [bars] largest at the top - a category axis runs bottom up - each with its tooltip, and the chart tall enough for them. */
	private static void show(BarChart<Number, String> chart, XYChart.Series<Number, String> series, List<Bar> bars) {
		List<XYChart.Data<Number, String>> data = new ArrayList<>();
		for (int i = bars.size() - 1; i >= 0; i--) {
			Bar bar = bars.get(i);
			XYChart.Data<Number, String> datum = new XYChart.Data<>(bar.value(), bar.label());
			datum.nodeProperty().addListener((observable, oldNode, node) -> {
				if (node != null)
					Tooltip.install(node, new Tooltip(bar.tooltip() + "\n" + bar.value()));
			});
			data.add(datum);
		}
		((CategoryAxis) chart.getYAxis()).getCategories().setAll(data.stream().map(XYChart.Data::getYValue).toList());
		series.getData().setAll(data);
		chart.setPrefHeight(Math.max(MIN_BAR_CHART_HEIGHT, BAR_CHART_PADDING + BAR_HEIGHT * bars.size()));
	}

	/** Called by {@code MainView#switchTo}: shown again, the charts are loaded again. */
	@Override
	public void setTabVisible(boolean tabVisible) {
		boolean becameVisible = tabVisible && !this.tabVisible;
		this.tabVisible = tabVisible;
		if (becameVisible && this.service.isInitialized())
			Platform.runLater(this::refresh);
	}

	@Override
	public void statusChangedEvent(EccoService service) {
		if (service.isInitialized()) {
			Platform.runLater(() -> {
				this.setDisable(false);
				if (this.tabVisible)
					this.refresh();
			});
		} else {
			Platform.runLater(() -> this.setDisable(true));
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
