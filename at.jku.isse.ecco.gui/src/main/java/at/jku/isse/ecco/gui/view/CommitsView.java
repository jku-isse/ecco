package at.jku.isse.ecco.gui.view;

import at.jku.isse.ecco.EccoService;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.gui.view.detail.CommitDetailView;
import at.jku.isse.ecco.listener.EccoListener;
import at.jku.isse.ecco.plugin.artifact.ArtifactReader;
import at.jku.isse.ecco.plugin.artifact.ArtifactWriter;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;

import java.nio.file.Path;
import java.util.Collection;

public class CommitsView extends BorderPane implements EccoListener {

	private EccoService service;

	final ObservableList<CommitInfo> commitsData = FXCollections.observableArrayList();

	public CommitsView(EccoService service) {
		this.service = service;

		ToolBar toolBar = new ToolBar();
		this.setTop(toolBar);


		SplitPane splitPane = new SplitPane();
		this.setCenter(splitPane);


		// list of commits
		TableView<CommitInfo> commitsTable = new TableView<CommitInfo>();
		commitsTable.setEditable(false);
		commitsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

		TableColumn<CommitInfo, Integer> idCommitterCol = new TableColumn<CommitInfo, Integer>("Id");
		TableColumn<CommitInfo, String> committerCol = new TableColumn<CommitInfo, String>("Committer");
		TableColumn<CommitInfo, String> commitsCol = new TableColumn<CommitInfo, String>("Commits");

		commitsCol.getColumns().addAll(idCommitterCol, committerCol);
		commitsTable.getColumns().setAll(commitsCol);

		idCommitterCol.setCellValueFactory((TableColumn.CellDataFeatures<CommitInfo, Integer> param) -> new ReadOnlyObjectWrapper<>(param.getValue().getCommit().getId()));
		committerCol.setCellValueFactory((TableColumn.CellDataFeatures<CommitInfo, String> param) -> new ReadOnlyStringWrapper(param.getValue().getCommit().getCommiter()));

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


		// add to split pane
		splitPane.getItems().addAll(commitsTable, commitDetailView);


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
			Collection<Commit> commits = service.getCommits();
			Platform.runLater(() -> {
				this.commitsData.clear();
				for (Commit commit : commits) {
					this.commitsData.add(new CommitInfo(commit));
				}
			});
		} else {
			Platform.runLater(() -> {
				this.setDisable(true);
			});
		}
	}

	@Override
	public void commitsChangedEvent(EccoService service, Commit commit) {
		Platform.runLater(() -> {
			this.commitsData.add(new CommitInfo(commit));
		});
	}

	@Override
	public void fileReadEvent(Path file, ArtifactReader reader) {

	}

	@Override
	public void fileWriteEvent(Path file, ArtifactWriter writer) {

	}


	public static class CommitInfo {
		private Commit commit;

		private CommitInfo(Commit commit) {
			this.commit = commit;
		}

		public Commit getCommit() {
			return this.commit;
		}

		public void setCommit(Commit commit) {
			this.commit = commit;
		}
	}

}
