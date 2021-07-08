package at.jku.isse.ecco.gui.view;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.composition.LazyCompositionRootNode;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.gui.ExceptionAlert;
import at.jku.isse.ecco.gui.view.detail.ArtifactDetailView;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.gui.view.detail.CommitDetailView;
import at.jku.isse.ecco.service.listener.EccoListener;
import at.jku.isse.ecco.tree.Node;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.BorderPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.*;

public class CommitsView extends BorderPane implements EccoListener {

	private EccoService service;

	final ObservableList<CommitInfo> commitsData = FXCollections.observableArrayList();

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
								CommitsView.this.commitsData.add(new CommitInfo(commit));
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
		TableView<CommitInfo> commitsTable = new TableView<>();
		commitsTable.setEditable(true);
		commitsTable.setTableMenuButtonVisible(true);
		commitsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

		TableColumn<CommitInfo, String> idCol = new TableColumn<>("Id");
		TableColumn<CommitInfo, Boolean> selectedCommitCol = new TableColumn<>("Selected");
		TableColumn<CommitInfo, String> commitMessage = new TableColumn<>("Commit Message");
		TableColumn<CommitInfo, String> commiter = new TableColumn<>("Commiter");
		TableColumn<CommitInfo, String> date = new TableColumn<>("Date");
		TableColumn<CommitInfo, String> commitsCol = new TableColumn<>("Commits");		//Table Title

		commitsCol.getColumns().addAll(idCol, selectedCommitCol, commitMessage, commiter, date);		//TBE Add columns
		commitsTable.getColumns().setAll(commitsCol);

		idCol.setCellValueFactory((TableColumn.CellDataFeatures<CommitInfo, String> param) -> new ReadOnlyObjectWrapper<>(param.getValue().getCommit().getId()));
		commitMessage.setCellValueFactory((TableColumn.CellDataFeatures<CommitInfo, String> param) -> new ReadOnlyObjectWrapper<>(param.getValue().getCommit().getCommitMassage()));
		commiter.setCellValueFactory((TableColumn.CellDataFeatures<CommitInfo, String> param) -> new ReadOnlyObjectWrapper<>(param.getValue().getCommit().getUsername()));
		date.setCellValueFactory((TableColumn.CellDataFeatures<CommitInfo, String> param) -> new ReadOnlyObjectWrapper<>(param.getValue().getCommit().getDate() == null ? "" : param.getValue().getCommit().getDate().toString()));  //TODO do better

		selectedCommitCol.maxWidthProperty().bind(commitsTable.widthProperty().divide(1.2));		//selected Column smaller than the other columns
		selectedCommitCol.setCellFactory(column -> new CheckBoxTableCell<>());
		selectedCommitCol.setCellValueFactory(cellData -> {
			CommitInfo cellValue = cellData.getValue();
			BooleanProperty property = cellValue.isSelected();

			// Add listener to handler change
			property.addListener((observable, oldValue, newValue) -> {
						if (commitsData.stream().filter(x -> x.isSelected().getValue()).count() > 2) {
							cellValue.setSelected(oldValue);
						} else {
							cellValue.setSelected(newValue);
						}
					});
			return property;
		});


		commitsTable.setItems(this.commitsData);


		// commit details view
		CommitDetailView commitDetailView = new CommitDetailView(service);

		commitsTable.getSelectionModel().selectedItemProperty().addListener((observableValue, oldValue, newValue) -> {
			if (newValue != null) {
				commitDetailView.showCommit(newValue.getCommit());
			} else {
				commitDetailView.showCommit(null);
			}
		});

		Button CompareButton = new Button("Compare");
		toolBar.getItems().add(CompareButton);
		CompareButton.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent e) {
				toolBar.setDisable(true);

				List<Commit> selectedCommits = new LinkedList<>();
				for (CommitInfo associationInfo : CommitsView.this.commitsData) {
					if (associationInfo.isSelected().getValue())
						selectedCommits.add(associationInfo.getCommit());
				}
				if(selectedCommits.size() == 2) {
					CommitComparisonView commitComparisonView =  new CommitComparisonView(service, selectedCommits.get(0), selectedCommits.get(1));
					Stage stage = new Stage();
					stage.initStyle(StageStyle.UTILITY);
					stage.initModality(Modality.WINDOW_MODAL);
					stage.setScene(new Scene(commitComparisonView));
					stage.setTitle("Commit Comparison");

					stage.show();
				} else {
					ExceptionAlert alert = new ExceptionAlert(new Throwable());
					alert.setTitle("Commit Compare Error");
					alert.setHeaderText("Please choose exactly two commits");

					alert.showAndWait();
				}

				Platform.runLater(() -> toolBar.setDisable(false));
			}
		});

		//Detail view
		ArtifactDetailView artifactDetailView = new ArtifactDetailView(service);
		SplitPane detailView = new SplitPane();
		detailView.setOrientation(Orientation.VERTICAL);
		detailView.getItems().addAll(commitDetailView, artifactDetailView);

		commitsTable.getSelectionModel().selectedItemProperty().addListener((observableValue, oldValue, newValue) -> {
			if (newValue != null) {
				LazyCompositionRootNode rootNode = new LazyCompositionRootNode();
				for (Association association : newValue.getCommit().getAssociations()) {
					rootNode.addOrigNode(association.getRootNode());
				}
				artifactDetailView.showTree(ArtifactDetailView.findBestArtifact(artifactDetailView, rootNode));
			}
		});

		// add to split pane
		splitPane.getItems().addAll(commitsTable, detailView);

		service.addListener(this);

		if (!service.isInitialized()) {
			this.setDisable(true);
		}
	}

	@Override
	public void statusChangedEvent(EccoService service) {
		if (service.isInitialized()) {
			Platform.runLater(() -> this.setDisable(false));
			Collection<Commit> commits = service.getCommits();
			Platform.runLater(() -> {
				this.commitsData.clear();
				for (Commit commit : commits) {
					this.commitsData.add(new CommitInfo(commit));
				}
			});
		} else {
			Platform.runLater(() -> this.setDisable(true));
		}
	}



/*	@Override
	public void commitsChangedEvent(EccoService service, Commit commit) {
		Platform.runLater(() -> this.commitsData.add();		//TODO check if new CommitInfo
	}*/

	public class CommitInfo {
		private Commit commit;
		private BooleanProperty selected;

		public CommitInfo(Commit c) {
			this.commit = c;
			this.selected = new SimpleBooleanProperty(false);
		}

		public Commit getCommit() {
			return commit;
		}

		public BooleanProperty isSelected() {
			return this.selected;
		}

		public void setSelected(boolean selected) {
			this.selected.set(selected);
		}
	}
}


