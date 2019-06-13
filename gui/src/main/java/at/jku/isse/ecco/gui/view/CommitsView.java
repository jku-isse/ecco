package at.jku.isse.ecco.gui.view;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.gui.view.detail.CommitDetailView;
import at.jku.isse.ecco.service.listener.EccoListener;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;

import java.util.Collection;

public class CommitsView extends BorderPane implements EccoListener {

	private EccoService service;

	final ObservableList<Commit> commitsData = FXCollections.observableArrayList();


	public CommitsView(EccoService service) {
		this.service = service;


		ToolBar toolBar = new ToolBar();
		this.setTop(toolBar);

		Button refreshButton = new Button("Refresh");
		toolBar.getItems().add(refreshButton);
		refreshButton.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent e) {
				toolBar.setDisable(true);
				CommitsView.this.commitsData.clear();

				Task commitsRefreshTask = new Task<Void>() {
					@Override
					public Void call() throws EccoException {
						Collection<Commit> commits = CommitsView.this.service.getCommits();
						Platform.runLater(() -> {
							for (Commit commit : commits) {
								CommitsView.this.commitsData.add(commit);
							}
						});
						Platform.runLater(() -> toolBar.setDisable(false));
						return null;
					}
				};

				new Thread(commitsRefreshTask).start();
			}
		});

		toolBar.getItems().add(new Separator());


		SplitPane splitPane = new SplitPane();
		this.setCenter(splitPane);


		// list of commits
		TableView<Commit> commitsTable = new TableView<>();
		commitsTable.setEditable(false);
		commitsTable.setTableMenuButtonVisible(true);
		commitsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

		TableColumn<Commit, String> idCol = new TableColumn<>("Id");
		TableColumn<Commit, String> commitsCol = new TableColumn<>("Commits");

		commitsCol.getColumns().addAll(idCol);
		commitsTable.getColumns().setAll(commitsCol);

		idCol.setCellValueFactory((TableColumn.CellDataFeatures<Commit, String> param) -> new ReadOnlyObjectWrapper<>(param.getValue().getId()));

		commitsTable.setItems(this.commitsData);


		// commit details view
		CommitDetailView commitDetailView = new CommitDetailView(service);


		commitsTable.getSelectionModel().selectedItemProperty().addListener((observableValue, oldValue, newValue) -> {
			if (newValue != null) {
				commitDetailView.showCommit(newValue);
			} else {
				commitDetailView.showCommit(null);
			}
		});


		// add to split pane
		splitPane.getItems().addAll(commitsTable, commitDetailView);


		service.addListener(this);

		if (!service.isInitialized())
			this.setDisable(true);
	}


	@Override
	public void statusChangedEvent(EccoService service) {
		if (service.isInitialized()) {
			Platform.runLater(() -> this.setDisable(false));
			Collection<Commit> commits = service.getCommits();
			Platform.runLater(() -> {
				this.commitsData.clear();
				for (Commit commit : commits) {
					this.commitsData.add(commit);
				}
			});
		} else {
			Platform.runLater(() -> this.setDisable(true));
		}
	}

	@Override
	public void commitsChangedEvent(EccoService service, Commit commit) {
		Platform.runLater(() -> this.commitsData.add(commit));
	}

}
