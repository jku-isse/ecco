package at.jku.isse.ecco.gui.view;

import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.service.listener.EccoListener;
import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.adapter.ArtifactWriter;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.nio.file.Path;

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

	public SettingsView(EccoService service) {
		this.service = service;

		ToolBar toolBar = new ToolBar();
		this.setTop(toolBar);


		this.content = new VBox();
		this.content.setPadding(new Insets(10, 10, 10, 10));

		this.setCenter(this.content);


		{ // status - no TitledPane wrapper: this whole view is already labeled "Status" via
			// MainView's header, so a nested section titled "Status" again was redundant
			GridPane gridPane = new GridPane();
			gridPane.setHgap(10);
			gridPane.setVgap(10);
			gridPane.setPadding(new Insets(10, 10, 10, 10));

			ColumnConstraints col1constraint = new ColumnConstraints();
			ColumnConstraints col2constraint = new ColumnConstraints();
			col2constraint.setFillWidth(true);
			col2constraint.setHgrow(Priority.ALWAYS);
			gridPane.getColumnConstraints().addAll(col1constraint, col2constraint);

			gridPane.prefWidthProperty().bind(this.content.widthProperty().subtract(20));

			this.content.getChildren().add(gridPane);
			this.content.setMargin(gridPane, new Insets(10, 10, 10, 10));


			int row = 0;

			Label baseDirLabel = new Label("Base Directory: ");
			gridPane.add(baseDirLabel, 0, row, 1, 1);
			this.baseDirUrl = new TextField(service.getBaseDir().toString());
			this.baseDirUrl.setEditable(false);
			gridPane.add(baseDirUrl, 1, row, 1, 1);
			row++;

			Label repositoryDirLabel = new Label("Repository Directory: ");
			gridPane.add(repositoryDirLabel, 0, row, 1, 1);
			this.repositoryDirUrl = new TextField(service.getRepositoryDir().toString());
			this.repositoryDirUrl.setEditable(false);
			gridPane.add(repositoryDirUrl, 1, row, 1, 1);
			row++;

			this.statusLabel = new Label();
			gridPane.add(this.statusLabel, 0, row, 2, 1);
			row++;
		}


		service.addListener(this);

		// synchronous, unlike statusChangedEvent(), so the view starts in the correct disabled
		// state immediately - construction already happens on the JavaFX Application Thread
		this.updateValues();
	}


	private void updateValues() {
		if (service.isInitialized()) {
			this.setDisable(false);

			this.statusLabel.setText("The ECCO Service is initialized.");

			this.baseDirUrl.setText(service.getBaseDir().toString());
			this.repositoryDirUrl.setText(service.getRepositoryDir().toString());
		} else {
			this.setDisable(true);

			this.statusLabel.setText("The ECCO Service has not been initialized yet.");
		}
	}


	// ECCO EVENTS

	@Override
	public final void statusChangedEvent(EccoService service) {
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
