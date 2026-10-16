package at.jku.isse.ecco.gui.view;

import at.jku.isse.ecco.gui.TabVisibilityAware;
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
import javafx.collections.FXCollections;
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
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Loads its charts when shown and again after every change of the repository while shown - there
 * was a Refresh button to press, and the charts were empty until then.
 */
public class ChartsView extends BorderPane implements EccoListener, TabVisibilityAware {

	private EccoService service;

	/** See {@link at.jku.isse.ecco.gui.view.graph.ArtifactGraphView#tabVisible}: nothing is computed while hidden. */
	private volatile boolean tabVisible = true;

	/** A later load supersedes one still running; see {@link #refresh()}. */
	private final AtomicLong refreshGeneration = new AtomicLong();

	private ObservableList<PieChart.Data> artifactsPerAssociationData;
	private ObservableList<PieChart.Data> revisionsPerFeature;
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


		{ // artifacts per association
			TitledPane artifactsPerAssociationPane = new TitledPane();
			artifactsPerAssociationPane.setAnimated(false);
			artifactsPerAssociationPane.setText("Artifacts per Association");

			this.artifactsPerAssociationData = FXCollections.observableArrayList();


			final PieChart pieChart = new PieChart(this.artifactsPerAssociationData);

			artifactsPerAssociationPane.setContent(pieChart);

			centerBox.getChildren().add(artifactsPerAssociationPane);
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
			final LineChart<Number, Number> artifactsPerDepthChart = new LineChart<>(xAxis, yAxis);

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
			modulesPerOrderPane.setAnimated(false);
			modulesPerOrderPane.setText("Modules per Order (raw vs. absorbed)");

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


		{ // revisions per feature
			TitledPane titledPane = new TitledPane();
			titledPane.setAnimated(false);
			titledPane.setText("Revisions per Feature");

			this.revisionsPerFeature = FXCollections.observableArrayList();

			final PieChart pieChart = new PieChart(revisionsPerFeature);

			titledPane.setContent(pieChart);

			centerBox.getChildren().add(titledPane);
		}

		Platform.runLater(() -> statusChangedEvent(service));

		service.addListener(this);
	}

	private void refresh() {
		long generation = this.refreshGeneration.incrementAndGet();

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
				for (Association association : repository.getAssociations()) {
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

				Map<String, Integer> artifactsPerAssociation = new java.util.LinkedHashMap<>();
				for (Association association : repository.getAssociations()) {
					int numArtifacts = association.getRootNode().countArtifacts();
					if (numArtifacts > 0)
						artifactsPerAssociation.put("A" + association.getId(), numArtifacts);
				}
				Map<String, Integer> revisionsPerFeature = new java.util.LinkedHashMap<>();
				for (Feature feature : repository.getFeatures()) {
					int numRevisions = feature.getRevisions().size();
					if (numRevisions > 0)
						revisionsPerFeature.put(feature.getName(), numRevisions);
				}

				Platform.runLater(() -> {
					if (ChartsView.this.refreshGeneration.get() != generation)
						return; // a newer load shows its own

					ChartsView.this.artifactsPerAssociationData.clear();
					artifactsPerAssociation.forEach((name, value) -> ChartsView.this.artifactsPerAssociationData.add(new PieChart.Data(name, value)));

					ChartsView.this.revisionsPerFeature.clear();
					revisionsPerFeature.forEach((name, value) -> ChartsView.this.revisionsPerFeature.add(new PieChart.Data(name, value)));

					ChartsView.this.artifactsPerDepthSeries.getData().clear();
					for (Map.Entry<Integer, Integer> entry : artifactsPerDepth.entrySet()) {
						ChartsView.this.artifactsPerDepthSeries.getData().add(new XYChart.Data<>(entry.getKey(), entry.getValue()));
					}

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
