package at.jku.isse.ecco.gui;

import at.jku.isse.ecco.EccoService;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.listener.EccoListener;
import at.jku.isse.ecco.plugin.artifact.ArtifactReader;
import at.jku.isse.ecco.plugin.artifact.ArtifactWriter;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * TODO: avoid rebuilding the whole scene with every event!
 */
public class SettingsView extends BorderPane implements EccoListener {

	private EccoService service;

	// gui elements
	private VBox content;
	private TextField baseDirUrl;
	private TextField repositoryDirUrl;
	private Label statusLabel;
	private TextField settingsBaseDirUrl;

	public SettingsView(EccoService service) {
		this.service = service;

		ToolBar toolBar = new ToolBar();
		this.setTop(toolBar);


		this.content = new VBox();
		this.content.setPadding(new Insets(10, 10, 10, 10));

		this.setCenter(this.content);


		{ // status
			GridPane gridPane = new GridPane();
			gridPane.setHgap(10);
			gridPane.setVgap(10);
			gridPane.setPadding(new Insets(10, 10, 10, 10));

			ColumnConstraints col1constraint = new ColumnConstraints();
			ColumnConstraints col2constraint = new ColumnConstraints();
			col2constraint.setFillWidth(true);
			col2constraint.setHgrow(Priority.ALWAYS);
			gridPane.getColumnConstraints().addAll(col1constraint, col2constraint);

			TitledPane statusPane = new TitledPane("Status", gridPane);
			statusPane.setContent(gridPane);
			statusPane.setAnimated(false);
			statusPane.setCollapsible(false);


			statusPane.prefWidthProperty().bind(this.content.widthProperty().subtract(20));
			gridPane.prefWidthProperty().bind(statusPane.widthProperty().subtract(20));


			this.content.getChildren().add(statusPane);
			this.content.setMargin(statusPane, new Insets(10, 10, 10, 10));


			int row = 0;

			Label baseDirLabel = new Label("Base Directory: ");
			gridPane.add(baseDirLabel, 0, row, 1, 1);
			this.baseDirUrl = new TextField(service.getBaseDir().toString());
			this.baseDirUrl.setEditable(false);
			this.baseDirUrl.setDisable(true);
			gridPane.add(baseDirUrl, 1, row, 1, 1);
			row++;

			Label repositoryDirLabel = new Label("Repository Directory: ");
			gridPane.add(repositoryDirLabel, 0, row, 1, 1);
			this.repositoryDirUrl = new TextField(service.getRepositoryDir().toString());
			this.repositoryDirUrl.setEditable(false);
			this.repositoryDirUrl.setDisable(true);
			gridPane.add(repositoryDirUrl, 1, row, 1, 1);
			row++;

			this.statusLabel = new Label();
			gridPane.add(this.statusLabel, 0, row, 2, 1);
			row++;
		}


		{ // settings
			GridPane gridPane = new GridPane();
			gridPane.setHgap(10);
			gridPane.setVgap(10);
			gridPane.setPadding(new Insets(10, 10, 10, 10));

			ColumnConstraints col1constraint = new ColumnConstraints();
			ColumnConstraints col2constraint = new ColumnConstraints();
			col2constraint.setFillWidth(true);
			col2constraint.setHgrow(Priority.ALWAYS);
			gridPane.getColumnConstraints().addAll(col1constraint, col2constraint);

			TitledPane settingsPane = new TitledPane("Settings", gridPane);
			settingsPane.setContent(gridPane);
			settingsPane.setAnimated(false);
			settingsPane.setCollapsible(false);


			settingsPane.prefWidthProperty().bind(this.content.widthProperty().subtract(20));
			gridPane.prefWidthProperty().bind(settingsPane.widthProperty().subtract(20));


			this.content.getChildren().add(settingsPane);
			this.content.setMargin(settingsPane, new Insets(10, 10, 10, 10));


			int row = 0;

			Label baseDirLabel = new Label("Base Directory: ");
			gridPane.add(baseDirLabel, 0, row, 1, 1);
			this.settingsBaseDirUrl = new TextField(service.getBaseDir().toString());
			gridPane.add(this.settingsBaseDirUrl, 1, row, 1, 1);
			row++;

			Button setBaseDirButton = new Button("Set Base Directory");
			setBaseDirButton.setOnAction(new EventHandler<ActionEvent>() {
				@Override
				public void handle(ActionEvent e) {
					SettingsView.this.service.setBaseDir(Paths.get(baseDirUrl.getText()));
				}
			});
			gridPane.add(setBaseDirButton, 0, row, 2, 1);
			row++;


			this.settingsBaseDirUrl.textProperty().addListener((observableValue, oldValue, newValue) -> {
				if (!newValue.equals(SettingsView.this.service.getBaseDir().toString())) {
					setBaseDirButton.setDisable(false);
				} else {
					setBaseDirButton.setDisable(true);
				}
			});
		}


		service.addListener(this);

		this.statusChangedEvent(service);
	}

	// ECCO EVENTS

	@Override
	public void statusChangedEvent(EccoService service) {
		Platform.runLater(() -> {
			if (service.isInitialized()) {
				this.statusLabel.setText("The ECCO Service is initialized.");
			} else {
				this.statusLabel.setText("The ECCO Service has not been initialized yet.");
			}
			this.baseDirUrl.setText(service.getBaseDir().toString());
			this.repositoryDirUrl.setText(service.getRepositoryDir().toString());
			this.settingsBaseDirUrl.setText(service.getBaseDir().toString());


//			// show initialization view
//			this.content.getChildren().clear();
//
//			FlowPane flowPane = new FlowPane();
//			flowPane.setHgap(10);
//			flowPane.setVgap(10);
//			flowPane.setPadding(new Insets(10, 10, 10, 10));
//			flowPane.setRowValignment(VPos.TOP);
//
//			this.content.getChildren().add(flowPane);
//
//			{ // begin status
//				GridPane gridPane = new GridPane();
//				gridPane.setHgap(10);
//				gridPane.setVgap(10);
//				gridPane.setPadding(new Insets(10, 10, 10, 10));
//
//				ColumnConstraints col1constraint = new ColumnConstraints();
//				ColumnConstraints col2constraint = new ColumnConstraints();
//				col2constraint.setFillWidth(true);
//				col2constraint.setHgrow(Priority.ALWAYS);
//				gridPane.getColumnConstraints().addAll(col1constraint, col2constraint);
//
//				TitledPane titledPane = new TitledPane("Status", gridPane);
//				titledPane.setContent(gridPane);
//				titledPane.setAnimated(false);
//				titledPane.setCollapsible(false);
//
//				titledPane.prefWidthProperty().bind(flowPane.widthProperty().subtract(20));
//				gridPane.prefWidthProperty().bind(titledPane.widthProperty().subtract(20));
//
//				flowPane.getChildren().add(titledPane);
//
//
//				int row = 0;
//
//				Label baseDirLabel = new Label("Base Directory: ");
//				gridPane.add(baseDirLabel, 0, row, 1, 1);
//				TextField baseDirUrl = new TextField(service.getBaseDir().toString());
//				gridPane.add(baseDirUrl, 1, row, 1, 1);
//				row++;
//
//				Button setBaseDirButton = new Button("Set Base Directory");
//				setBaseDirButton.setOnAction(new EventHandler<ActionEvent>() {
//					@Override
//					public void handle(ActionEvent e) {
//						SettingsView.this.service.setBaseDir(Paths.get(baseDirUrl.getText()));
//					}
//				});
//				gridPane.add(setBaseDirButton, 0, row, 2, 1);
//				row++;
//
//
//				Label repositoryDirLabel = new Label("Repository Directory: ");
//				gridPane.add(repositoryDirLabel, 0, row, 1, 1);
//				TextField repositoryDirUrl = new TextField(service.getRepositoryDir().toString());
//				gridPane.add(repositoryDirUrl, 1, row, 1, 1);
////				Label repositoryDirNameLabel = new Label("/.ecco");
////				gridPane.add(repositoryDirNameLabel, 2, row, 1, 1);
//				row++;
//
//				if (!service.isInitialized()) {
//
//					Label notInitializedLabel = new Label("The ECCO Service has not been initialized yet.");
//					gridPane.add(notInitializedLabel, 0, row, 2, 1);
//					row++;
//
//					if (service.repositoryExists()) { // repository exists
//						// detect existing repository and show button to initialize service
//						service.detectRepository();
//
//						Label alreadyExistsLabel = new Label("An existing repository was detected.");
//						gridPane.add(alreadyExistsLabel, 0, row, 2, 1);
//						row++;
//					} else {
////						Button initializeButton = new Button("Initialize ECCO Service");
////						initializeButton.setOnAction(new EventHandler<ActionEvent>() {
////							@Override
////							public void handle(ActionEvent e) {
////								initializeButton.setDisable(true);
////								StatusView.this.service.setRepositoryDir(Paths.get(repositoryDirUrl.getText()));
////								// detect repository from current location
////								new Thread(new Task<Void>() {
////									@Override
////									public Void call() throws EccoException {
////										StatusView.this.service.init();
////										return null;
////									}
////								}).start();
////							}
////						});
////						gridPane.add(initializeButton, 0, row, 2, 1);
////						row++;
//
//						// show button for creating repository
//						Button createRepositoryButton = new Button("Create / Load Repository");
//						createRepositoryButton.setOnAction(new EventHandler<ActionEvent>() {
//							@Override
//							public void handle(ActionEvent e) {
//								createRepositoryButton.setDisable(true);
//								SettingsView.this.service.setRepositoryDir(Paths.get(repositoryDirUrl.getText()));
//								// detect repository from current location
//								new Thread(new Task<Void>() {
//									@Override
//									public Void call() throws EccoException {
//										if (SettingsView.this.service.repositoryDirectoryExists())
//											SettingsView.this.service.init();
//										else
//											SettingsView.this.service.createRepository();
//										return null;
//									}
//								}).start();
//							}
//						});
//						gridPane.add(createRepositoryButton, 0, row, 2, 1);
//						row++;
//					}
//				} else {
//					Label inintializedLabel = new Label("The ECCO Service is initialized.");
//					gridPane.add(inintializedLabel, 0, row, 2, 1);
//					row++;
//
//					repositoryDirUrl.setEditable(false);
//					repositoryDirUrl.setDisable(true);
//				}
//			} // end status
		});
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
