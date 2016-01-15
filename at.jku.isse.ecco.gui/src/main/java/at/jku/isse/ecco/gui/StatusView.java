package at.jku.isse.ecco.gui;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.EccoService;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.listener.EccoListener;
import at.jku.isse.ecco.plugin.artifact.ArtifactReader;
import at.jku.isse.ecco.plugin.artifact.ArtifactWriter;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * TODO: avoid rebuilding the whole scene with every event!
 */
public class StatusView extends BorderPane implements EccoListener {

	private EccoService service;

	// gui elements
	VBox content;

	public StatusView(EccoService service) {
		this.service = service;

		ToolBar toolBar = new ToolBar();
		this.setTop(toolBar);

		this.content = new VBox();
		this.setCenter(this.content);

		service.addListener(this);

		this.statusChangedEvent(service);
	}

	// ECCO EVENTS

	@Override
	public void statusChangedEvent(EccoService service) {
		Platform.runLater(() -> {
			// show initialization view
			this.content.getChildren().clear();

			FlowPane flowPane = new FlowPane();
			flowPane.setHgap(10);
			flowPane.setVgap(10);
			flowPane.setPadding(new Insets(10, 10, 10, 10));
			flowPane.setRowValignment(VPos.TOP);

			this.content.getChildren().add(flowPane);

			{ // begin status
				GridPane gridPane = new GridPane();
				gridPane.setHgap(10);
				gridPane.setVgap(10);
				gridPane.setPadding(new Insets(10, 10, 10, 10));

				ColumnConstraints col1constraint = new ColumnConstraints();
				ColumnConstraints col2constraint = new ColumnConstraints();
				col2constraint.setFillWidth(true);
				col2constraint.setHgrow(Priority.ALWAYS);
				gridPane.getColumnConstraints().addAll(col1constraint, col2constraint);

				TitledPane titledPane = new TitledPane("Status", gridPane);
				titledPane.setContent(gridPane);
				titledPane.setAnimated(false);
				titledPane.setCollapsible(false);

				titledPane.prefWidthProperty().bind(flowPane.widthProperty().subtract(20));
				gridPane.prefWidthProperty().bind(titledPane.widthProperty().subtract(20));

				flowPane.getChildren().add(titledPane);


				int row = 0;

				Label baseDirLabel = new Label("Base Directory: ");
				gridPane.add(baseDirLabel, 0, row, 1, 1);
				TextField baseDirUrl = new TextField(service.getBaseDir().toString());
				gridPane.add(baseDirUrl, 1, row, 1, 1);
				row++;

				Button setBaseDirButton = new Button("Set Base Directory");
				setBaseDirButton.setOnAction(new EventHandler<ActionEvent>() {
					@Override
					public void handle(ActionEvent e) {
						StatusView.this.service.setBaseDir(Paths.get(baseDirUrl.getText()));
					}
				});
				gridPane.add(setBaseDirButton, 0, row, 2, 1);
				row++;


				Label repositoryDirLabel = new Label("Repository Directory: ");
				gridPane.add(repositoryDirLabel, 0, row, 1, 1);
				TextField repositoryDirUrl = new TextField(service.getRepositoryDir().toString());
				gridPane.add(repositoryDirUrl, 1, row, 1, 1);
//				Label repositoryDirNameLabel = new Label("/.ecco");
//				gridPane.add(repositoryDirNameLabel, 2, row, 1, 1);
				row++;

				if (!service.isInitialized()) {

					Label notInitializedLabel = new Label("The ECCO Service has not been initialized yet.");
					gridPane.add(notInitializedLabel, 0, row, 2, 1);
					row++;

					if (service.repositoryExists()) { // repository exists
						// detect existing repository and show button to initialize service
						service.detectRepository();

						Label alreadyExistsLabel = new Label("An existing repository was detected.");
						gridPane.add(alreadyExistsLabel, 0, row, 2, 1);
						row++;
					} else {
//						Button initializeButton = new Button("Initialize ECCO Service");
//						initializeButton.setOnAction(new EventHandler<ActionEvent>() {
//							@Override
//							public void handle(ActionEvent e) {
//								initializeButton.setDisable(true);
//								StatusView.this.service.setRepositoryDir(Paths.get(repositoryDirUrl.getText()));
//								// detect repository from current location
//								new Thread(new Task<Void>() {
//									@Override
//									public Void call() throws EccoException {
//										StatusView.this.service.init();
//										return null;
//									}
//								}).start();
//							}
//						});
//						gridPane.add(initializeButton, 0, row, 2, 1);
//						row++;

						// show button for creating repository
						Button createRepositoryButton = new Button("Create / Load Repository");
						createRepositoryButton.setOnAction(new EventHandler<ActionEvent>() {
							@Override
							public void handle(ActionEvent e) {
								createRepositoryButton.setDisable(true);
								StatusView.this.service.setRepositoryDir(Paths.get(repositoryDirUrl.getText()));
								// detect repository from current location
								new Thread(new Task<Void>() {
									@Override
									public Void call() throws EccoException {
										if (StatusView.this.service.repositoryDirectoryExists())
											StatusView.this.service.init();
										else
											StatusView.this.service.createRepository();
										return null;
									}
								}).start();
							}
						});
						gridPane.add(createRepositoryButton, 0, row, 2, 1);
						row++;
					}
				} else {
					Label inintializedLabel = new Label("The ECCO Service is initialized.");
					gridPane.add(inintializedLabel, 0, row, 2, 1);
					row++;

					repositoryDirUrl.setEditable(false);
					repositoryDirUrl.setDisable(true);
				}
			} // end status
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
