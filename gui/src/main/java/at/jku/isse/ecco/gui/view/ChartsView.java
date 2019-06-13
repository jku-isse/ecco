package at.jku.isse.ecco.gui.view;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.adapter.ArtifactWriter;
import at.jku.isse.ecco.composition.LazyCompositionRootNode;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.service.listener.EccoListener;
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
import java.util.Map;
import java.util.TreeMap;

public class ChartsView extends BorderPane implements EccoListener {

	private EccoService service;

	private ObservableList<PieChart.Data> artifactsPerAssociationData;
	private ObservableList<PieChart.Data> revisionsPerFeature;
	private XYChart.Series<Number, Number> artifactsPerDepthSeries;
	private XYChart.Series<String, Number> modulesPerOrderSeries;
	private XYChart.Series artifactsPerDepthAndOrderSeries;

	public ChartsView(EccoService service) {
		this.service = service;


		// toolbar
		ToolBar toolBar = new ToolBar();
		this.setTop(toolBar);

		Button refreshButton = new Button("Refresh");
		refreshButton.setOnAction(e -> {
			toolBar.setDisable(true);

			Task refreshTask = new Task<Void>() {
				@Override
				public Void call() throws EccoException {

					// artifacts per depth
					LazyCompositionRootNode compRootNode = new LazyCompositionRootNode();
					for (Association association : ChartsView.this.service.getRepository().getAssociations()) {
						compRootNode.addOrigNode(association.getRootNode());
					}
					Map<Integer, Integer> artifactsPerDepth = compRootNode.countArtifactsPerDepth();

					// modules per order
					Repository repository = ChartsView.this.service.getRepository();
					final Map<Integer, Integer> modulesPerOrderMap = new TreeMap<>();
					for (Association association : repository.getAssociations()) {
						for (Module module : association.computeCondition().getModules().keySet()) {
							//For the specified order -> If no order of this type is seen yet, it must be the first. Otherwise 1 is added to the old value
							modulesPerOrderMap.compute(module.getOrder(), (key, oldValue) -> oldValue == null ? 1 : oldValue + 1);
						}
					}

					Platform.runLater(() -> {
						// artifacts per association
						ChartsView.this.artifactsPerAssociationData.clear();
						for (Association association : ChartsView.this.service.getRepository().getAssociations()) {
							int numArtifacts = association.getRootNode().countArtifacts();
							if (numArtifacts > 0)
								ChartsView.this.artifactsPerAssociationData.add(new PieChart.Data("A" + association.getId(), numArtifacts));
						}

						// revisions per feature
						ChartsView.this.revisionsPerFeature.clear();
						for (Feature feature : ChartsView.this.service.getRepository().getFeatures()) {
							int numRevisions = feature.getRevisions().size();
							if (numRevisions > 0)
								ChartsView.this.revisionsPerFeature.add(new PieChart.Data(feature.getName(), numRevisions));
						}

						// artifacts per depth
						ChartsView.this.artifactsPerDepthSeries.getData().clear();
						for (Map.Entry<Integer, Integer> entry : artifactsPerDepth.entrySet()) {
							ChartsView.this.artifactsPerDepthSeries.getData().add(new XYChart.Data<>(entry.getKey(), entry.getValue()));
						}

						// modules per order
						{
							final ObservableList<XYChart.Data<String, Number>> maxOrderData = ChartsView.this.modulesPerOrderSeries.getData();
							maxOrderData.clear();
							modulesPerOrderMap.forEach((key, value) -> maxOrderData.add(new XYChart.Data<>(Integer.toString(key), value)));
						}

//						// artifacts per depth and order
//						ChartsView.this.artifactsPerDepthAndOrderSeries.getData().clear();
//						ChartsView.this.artifactsPerDepthAndOrderSeries.getData().add(new XYChart.Data(0, 0));

						toolBar.setDisable(false);
					});
					return null;
				}
			};

			new Thread(refreshTask).start();
		});

		Button exportButton = new Button("Export");

		toolBar.getItems().setAll(refreshButton, new Separator(), exportButton, new Separator());


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
			pieChart.setTitle("Artifacts per Association");

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
			artifactsPerDepthChart.setTitle("Artifacts per Depth");

			this.artifactsPerDepthSeries = new XYChart.Series<>();
			this.artifactsPerDepthSeries.setName("Number of Artifacts at Depth");

			artifactsPerDepthChart.getData().setAll(this.artifactsPerDepthSeries);

			artifactsPerDepthPane.setContent(artifactsPerDepthChart);

			centerBox.getChildren().add(artifactsPerDepthPane);
		}


		{ // modules per order
			TitledPane modulesPerOrderPane = new TitledPane();
			modulesPerOrderPane.setAnimated(false);
			modulesPerOrderPane.setText("Modules per Order");

			final CategoryAxis xAxis = new CategoryAxis();
			final NumberAxis yAxis = new NumberAxis();
			final BarChart<String, Number> bc = new BarChart<>(xAxis, yAxis);
			bc.setTitle("Modules per Order");
			xAxis.setLabel("Order");
			yAxis.setLabel("Number of Modules");
			yAxis.setTickUnit(1.0);
			yAxis.setMinorTickVisible(false);

			this.modulesPerOrderSeries = new XYChart.Series<>();
			this.modulesPerOrderSeries.setName("Number of Modules");

			bc.getData().setAll(this.modulesPerOrderSeries);

			modulesPerOrderPane.setContent(bc);

			centerBox.getChildren().add(modulesPerOrderPane);
		}


//		{ // artifacts per depth and order
//			TitledPane titlePane = new TitledPane();
//			titlePane.setAnimated(false);
//			titlePane.setText("Artifacts per Depth and Order");
//			final NumberAxis xAxis = new NumberAxis(1, 53, 4);
//			final NumberAxis yAxis = new NumberAxis(0, 80, 10);
//			final BubbleChart<Number, Number> blc = new BubbleChart<Number, Number>(xAxis, yAxis);
//			xAxis.setLabel("Order");
//			yAxis.setLabel("Depth");
//			blc.setTitle("Artifacts per Depth and Order");
//
//			this.artifactsPerDepthAndOrderSeries = new XYChart.Series();
//			this.artifactsPerDepthAndOrderSeries.setName("Artifacts per Depth and Order");
//
//			blc.getData().addAll(this.artifactsPerDepthAndOrderSeries);
//
//			titlePane.setContent(blc);
//
//			centerBox.getChildren().add(titlePane);
//		}


		{ // revisions per feature
			TitledPane titledPane = new TitledPane();
			titledPane.setAnimated(false);
			titledPane.setText("Revisions per Feature");

			this.revisionsPerFeature = FXCollections.observableArrayList();

			final PieChart pieChart = new PieChart(revisionsPerFeature);
			pieChart.setTitle("Revisions per Feature");

			titledPane.setContent(pieChart);

			centerBox.getChildren().add(titledPane);
		}


		service.addListener(this);

		if (!service.isInitialized())
			this.setDisable(true);
	}

	@Override
	public void statusChangedEvent(EccoService service) {
		if (service.isInitialized()) {
			Platform.runLater(() -> {
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
