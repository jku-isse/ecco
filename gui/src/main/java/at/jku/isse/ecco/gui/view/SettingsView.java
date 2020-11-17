package at.jku.isse.ecco.gui.view;

import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.service.listener.EccoListener;
import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.adapter.ArtifactWriter;
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
	private Button setBaseDirButton;
	private CheckBox settingsManualModeCheckBox;
	private Button setModeButton;

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
			//this.baseDirUrl.setDisable(true);
			gridPane.add(baseDirUrl, 1, row, 1, 1);
			row++;

			Label repositoryDirLabel = new Label("Repository Directory: ");
			gridPane.add(repositoryDirLabel, 0, row, 1, 1);
			this.repositoryDirUrl = new TextField(service.getRepositoryDir().toString());
			this.repositoryDirUrl.setEditable(false);
			//this.repositoryDirUrl.setDisable(true);
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

			// base directory
			Label baseDirLabel = new Label("Base Directory: ");
			gridPane.add(baseDirLabel, 0, row, 1, 1);
			this.settingsBaseDirUrl = new TextField(service.getBaseDir().toString());
			gridPane.add(this.settingsBaseDirUrl, 1, row, 1, 1);

			this.setBaseDirButton = new Button("Set Base Directory");
			setBaseDirButton.setOnAction(new EventHandler<ActionEvent>() {
				@Override
				public void handle(ActionEvent e) {
					SettingsView.this.service.setBaseDir(Paths.get(SettingsView.this.settingsBaseDirUrl.getText()));
					SettingsView.this.updateValues();
				}
			});
			gridPane.add(setBaseDirButton, 2, row, 1, 1);
			row++;

//			// manual mode
//			Label manualModeLabel = new Label("Manual Mode: ");
//			gridPane.add(manualModeLabel, 0, row, 1, 1);
//			this.settingsManualModeCheckBox = new CheckBox("Manual Mode");
//			gridPane.add(this.settingsManualModeCheckBox, 1, row, 1, 1);
//
//			this.setModeButton = new Button("Set Mode");
//			setModeButton.setOnAction(new EventHandler<ActionEvent>() {
//				@Override
//				public void handle(ActionEvent e) {
//					try {
//						SettingsView.this.service.setManualMode(SettingsView.this.settingsManualModeCheckBox.isSelected());
//					} catch (EccoException eccoException) {
//						new ExceptionAlert(eccoException).show();
//					}
//					SettingsView.this.updateValues();
//				}
//			});
//			gridPane.add(setModeButton, 2, row, 1, 1);
//			row++;


			this.settingsBaseDirUrl.textProperty().addListener((observableValue, oldValue, newValue) -> {
				if (!Paths.get(newValue).equals(SettingsView.this.service.getBaseDir())) {
					SettingsView.this.setBaseDirButton.setDisable(false);
				} else {
					SettingsView.this.setBaseDirButton.setDisable(true);
				}
			});

//			this.settingsManualModeCheckBox.selectedProperty().addListener((observableValue, oldValue, newValue) -> {
//				if (newValue != SettingsView.this.service.isManualMode()) {
//					SettingsView.this.setModeButton.setDisable(false);
//				} else {
//					SettingsView.this.setModeButton.setDisable(true);
//				}
//			});
		}


		{ // plugins
			PluginsView pluginsView = new PluginsView(service);

			TitledPane pluginsPane = new TitledPane("Plugins", pluginsView);
			pluginsPane.setContent(pluginsView);
			pluginsPane.setAnimated(false);
			pluginsPane.setCollapsible(false);
			pluginsPane.setPadding(new Insets(10, 10, 10, 10));


			//pluginsPane.prefWidthProperty().bind(this.content.widthProperty().subtract(20));
			pluginsPane.prefWidthProperty().bind(this.content.widthProperty());


			this.content.getChildren().add(pluginsPane);
		}


		service.addListener(this);

		this.statusChangedEvent(service);
	}


	private void updateValues() {
		if (service.isInitialized()) {
			this.statusLabel.setText("The ECCO Service is initialized.");

			this.baseDirUrl.setText(service.getBaseDir().toString());
			this.repositoryDirUrl.setText(service.getRepositoryDir().toString());
			this.settingsBaseDirUrl.setText(service.getBaseDir().toString());
//			this.settingsManualModeCheckBox.setSelected(service.isManualMode());

			if (!Paths.get(this.settingsBaseDirUrl.getText()).equals(SettingsView.this.service.getBaseDir())) {
				setBaseDirButton.setDisable(false);
			} else {
				setBaseDirButton.setDisable(true);
			}

//			if (this.settingsManualModeCheckBox.isSelected() != SettingsView.this.service.isManualMode()) {
//				SettingsView.this.setModeButton.setDisable(false);
//			} else {
//				SettingsView.this.setModeButton.setDisable(true);
//			}
		} else {
			this.statusLabel.setText("The ECCO Service has not been initialized yet.");
		}
	}


	// ECCO EVENTS

	@Override
	public void statusChangedEvent(EccoService service) {
		Platform.runLater(() -> {
			this.updateValues();
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
