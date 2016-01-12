package at.jku.isse.ecco.gui;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.EccoService;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.listener.EccoListener;
import at.jku.isse.ecco.plugin.artifact.ArtifactReader;
import at.jku.isse.ecco.plugin.artifact.ArtifactWriter;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.VPos;
import javafx.scene.chart.*;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;

import java.nio.file.Path;

public class ChartsView extends BorderPane implements EccoListener {

	private EccoService service;

	private ObservableList<PieChart.Data> artifactsPerAssociationData;

	final static String austria = "Austria";
	final static String brazil = "Brazil";
	final static String france = "France";
	final static String italy = "Italy";
	final static String usa = "USA";

	public ChartsView(EccoService service) {
		this.service = service;


		// toolbar
		ToolBar toolBar = new ToolBar();
		this.setTop(toolBar);

		Button refreshButton = new Button("Refresh");
		toolBar.getItems().add(refreshButton);

		refreshButton.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent e) {
				toolBar.setDisable(true);

				Task refreshTask = new Task<Void>() {
					@Override
					public Void call() throws EccoException {

						Platform.runLater(() -> {
							ChartsView.this.artifactsPerAssociationData.clear();
							for (Association association : ChartsView.this.service.getAssociations()) {
								ChartsView.this.artifactsPerAssociationData.add(new PieChart.Data("A" + association.getId(), association.countArtifacts()));
							}
							toolBar.setDisable(false);
						});
						return null;
					}
				};

				new Thread(refreshTask).start();
			}
		});

		Button exportButton = new Button("Export");
		toolBar.getItems().add(exportButton);

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


		// TODO: these are dummy graphs from a javafx tutorial. fill them with actual data!


		{ // artifacts per association
			TitledPane artifactsPerAssociation = new TitledPane();
			artifactsPerAssociation.setAnimated(false);
			artifactsPerAssociation.setText("Artifacts per Depth");

			this.artifactsPerAssociationData = FXCollections.observableArrayList();


			final PieChart pieChart = new PieChart(this.artifactsPerAssociationData);
			pieChart.setTitle("Artifacts per Association");

			artifactsPerAssociation.setContent(pieChart);

			centerBox.getChildren().add(artifactsPerAssociation);
		}


		{ // modules per order
			TitledPane modulesPerOrderPane = new TitledPane();
			modulesPerOrderPane.setAnimated(false);
			modulesPerOrderPane.setText("Modules per Order");

			final CategoryAxis xAxis = new CategoryAxis();
			final NumberAxis yAxis = new NumberAxis();
			final BarChart<String, Number> bc = new BarChart<String, Number>(xAxis, yAxis);
			bc.setTitle("Modules per Order");
			xAxis.setLabel("Order");
			yAxis.setLabel("Number of Modules");

			XYChart.Series series1 = new XYChart.Series();
			series1.setName("2003");
			series1.getData().add(new XYChart.Data(austria, 25601.34));
			series1.getData().add(new XYChart.Data(brazil, 20148.82));
			series1.getData().add(new XYChart.Data(france, 10000));
			series1.getData().add(new XYChart.Data(italy, 35407.15));
			series1.getData().add(new XYChart.Data(usa, 12000));

			XYChart.Series series2 = new XYChart.Series();
			series2.setName("2004");
			series2.getData().add(new XYChart.Data(austria, 57401.85));
			series2.getData().add(new XYChart.Data(brazil, 41941.19));
			series2.getData().add(new XYChart.Data(france, 45263.37));
			series2.getData().add(new XYChart.Data(italy, 117320.16));
			series2.getData().add(new XYChart.Data(usa, 14845.27));

			XYChart.Series series3 = new XYChart.Series();
			series3.setName("2005");
			series3.getData().add(new XYChart.Data(austria, 45000.65));
			series3.getData().add(new XYChart.Data(brazil, 44835.76));
			series3.getData().add(new XYChart.Data(france, 18722.18));
			series3.getData().add(new XYChart.Data(italy, 17557.31));
			series3.getData().add(new XYChart.Data(usa, 92633.68));

			bc.getData().addAll(series1, series2, series3);

			modulesPerOrderPane.setContent(bc);

			centerBox.getChildren().add(modulesPerOrderPane);
		}


		{ // modules per order
			TitledPane artifactsPerDepthPane = new TitledPane();
			artifactsPerDepthPane.setAnimated(false);
			artifactsPerDepthPane.setText("Artifacts per Depth");

			final CategoryAxis xAxis = new CategoryAxis();
			final NumberAxis yAxis = new NumberAxis();
			final BarChart<String, Number> barChart = new BarChart<String, Number>(xAxis, yAxis);
			barChart.setTitle("Modules per Order");
			xAxis.setLabel("Order");
			yAxis.setLabel("Number of Modules");

			XYChart.Series series1 = new XYChart.Series();
			series1.setName("2003");
			series1.getData().add(new XYChart.Data(austria, 25601.34));
			series1.getData().add(new XYChart.Data(brazil, 20148.82));
			series1.getData().add(new XYChart.Data(france, 10000));
			series1.getData().add(new XYChart.Data(italy, 35407.15));
			series1.getData().add(new XYChart.Data(usa, 12000));

			XYChart.Series series2 = new XYChart.Series();
			series2.setName("2004");
			series2.getData().add(new XYChart.Data(austria, 57401.85));
			series2.getData().add(new XYChart.Data(brazil, 41941.19));
			series2.getData().add(new XYChart.Data(france, 45263.37));
			series2.getData().add(new XYChart.Data(italy, 117320.16));
			series2.getData().add(new XYChart.Data(usa, 14845.27));

			XYChart.Series series3 = new XYChart.Series();
			series3.setName("2005");
			series3.getData().add(new XYChart.Data(austria, 45000.65));
			series3.getData().add(new XYChart.Data(brazil, 44835.76));
			series3.getData().add(new XYChart.Data(france, 18722.18));
			series3.getData().add(new XYChart.Data(italy, 17557.31));
			series3.getData().add(new XYChart.Data(usa, 92633.68));

			barChart.getData().addAll(series1, series2, series3);

			artifactsPerDepthPane.setContent(barChart);

			centerBox.getChildren().add(artifactsPerDepthPane);
		}


		{ // modules per order
			TitledPane modulesPerOrderPane = new TitledPane();
			modulesPerOrderPane.setAnimated(false);
			modulesPerOrderPane.setText("Modules per Order");

			final CategoryAxis xAxis = new CategoryAxis();
			final NumberAxis yAxis = new NumberAxis();
			final BarChart<String, Number> bc = new BarChart<String, Number>(xAxis, yAxis);
			bc.setTitle("Modules per Order");
			xAxis.setLabel("Order");
			yAxis.setLabel("Number of Modules");

			XYChart.Series series1 = new XYChart.Series();
			series1.setName("2003");
			series1.getData().add(new XYChart.Data(austria, 25601.34));
			series1.getData().add(new XYChart.Data(brazil, 20148.82));
			series1.getData().add(new XYChart.Data(france, 10000));
			series1.getData().add(new XYChart.Data(italy, 35407.15));
			series1.getData().add(new XYChart.Data(usa, 12000));

			XYChart.Series series2 = new XYChart.Series();
			series2.setName("2004");
			series2.getData().add(new XYChart.Data(austria, 57401.85));
			series2.getData().add(new XYChart.Data(brazil, 41941.19));
			series2.getData().add(new XYChart.Data(france, 45263.37));
			series2.getData().add(new XYChart.Data(italy, 117320.16));
			series2.getData().add(new XYChart.Data(usa, 14845.27));

			XYChart.Series series3 = new XYChart.Series();
			series3.setName("2005");
			series3.getData().add(new XYChart.Data(austria, 45000.65));
			series3.getData().add(new XYChart.Data(brazil, 44835.76));
			series3.getData().add(new XYChart.Data(france, 18722.18));
			series3.getData().add(new XYChart.Data(italy, 17557.31));
			series3.getData().add(new XYChart.Data(usa, 92633.68));

			bc.getData().addAll(series1, series2, series3);

			modulesPerOrderPane.setContent(bc);

			centerBox.getChildren().add(modulesPerOrderPane);
		}


		{ // modules per order
			TitledPane artifactsPerDepthPane = new TitledPane();
			artifactsPerDepthPane.setAnimated(false);
			artifactsPerDepthPane.setText("Artifacts per Depth");

			final CategoryAxis xAxis = new CategoryAxis();
			final NumberAxis yAxis = new NumberAxis();
			final BarChart<String, Number> barChart = new BarChart<String, Number>(xAxis, yAxis);
			barChart.setTitle("Modules per Order");
			xAxis.setLabel("Order");
			yAxis.setLabel("Number of Modules");

			XYChart.Series series1 = new XYChart.Series();
			series1.setName("2003");
			series1.getData().add(new XYChart.Data(austria, 25601.34));
			series1.getData().add(new XYChart.Data(brazil, 20148.82));
			series1.getData().add(new XYChart.Data(france, 10000));
			series1.getData().add(new XYChart.Data(italy, 35407.15));
			series1.getData().add(new XYChart.Data(usa, 12000));

			XYChart.Series series2 = new XYChart.Series();
			series2.setName("2004");
			series2.getData().add(new XYChart.Data(austria, 57401.85));
			series2.getData().add(new XYChart.Data(brazil, 41941.19));
			series2.getData().add(new XYChart.Data(france, 45263.37));
			series2.getData().add(new XYChart.Data(italy, 117320.16));
			series2.getData().add(new XYChart.Data(usa, 14845.27));

			XYChart.Series series3 = new XYChart.Series();
			series3.setName("2005");
			series3.getData().add(new XYChart.Data(austria, 45000.65));
			series3.getData().add(new XYChart.Data(brazil, 44835.76));
			series3.getData().add(new XYChart.Data(france, 18722.18));
			series3.getData().add(new XYChart.Data(italy, 17557.31));
			series3.getData().add(new XYChart.Data(usa, 92633.68));

			barChart.getData().addAll(series1, series2, series3);

			artifactsPerDepthPane.setContent(barChart);

			centerBox.getChildren().add(artifactsPerDepthPane);
		}


		{ // artifacts per association
			TitledPane artifactsPerAssociation = new TitledPane();
			artifactsPerAssociation.setAnimated(false);
			artifactsPerAssociation.setText("Artifacts per Depth");

			ObservableList<PieChart.Data> pieChartData = FXCollections.observableArrayList(
					new PieChart.Data("Grapefruit", 13),
					new PieChart.Data("Oranges", 25),
					new PieChart.Data("Plums", 10),
					new PieChart.Data("Pears", 22),
					new PieChart.Data("Apples", 30));
			final PieChart pieChart = new PieChart(pieChartData);
			pieChart.setTitle("Artifacts per Association");

			artifactsPerAssociation.setContent(pieChart);

			centerBox.getChildren().add(artifactsPerAssociation);
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
