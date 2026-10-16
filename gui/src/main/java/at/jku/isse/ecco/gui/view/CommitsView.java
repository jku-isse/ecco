package at.jku.isse.ecco.gui.view;

import at.jku.isse.ecco.composition.LazyCompositionRootNode;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.gui.TableColumns;
import at.jku.isse.ecco.gui.view.detail.ArtifactDetailView;
import at.jku.isse.ecco.gui.view.detail.CommitDetailView;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.service.listener.EccoListener;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.BorderPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

public class CommitsView extends BorderPane implements EccoListener {

	/** Sortable and short: "Thu Oct 15 08:54:22 CEST 2026" was cut off at the column's edge. */
	private static final java.time.format.DateTimeFormatter DATE_FORMAT = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(java.time.ZoneId.systemDefault());


	private EccoService service;

	// reports a row's tick too (an "update" change), so Compare follows the ticks
	final ObservableList<CommitInfo> commitsData = FXCollections.observableArrayList(info -> new javafx.beans.Observable[]{info.isSelected()});

	public CommitsView(EccoService service) {
		this.service = service;

		ToolBar toolBar = new ToolBar();
		this.setTop(toolBar);


		SplitPane splitPane = new SplitPane();
		this.setCenter(splitPane);

		// list of commits
		TableView<CommitInfo> commitsTable = new TableView<>();
		commitsTable.setEditable(true);
		commitsTable.setTableMenuButtonVisible(true);

		TableColumn<CommitInfo, String> idCol = new TableColumn<>("Id");
		TableColumn<CommitInfo, Boolean> selectedCommitCol = new TableColumn<>("Selected");
		TableColumn<CommitInfo, String> commitMessage = new TableColumn<>("Commit Message");
		TableColumn<CommitInfo, String> commiter = new TableColumn<>("Committer");
		TableColumn<CommitInfo, String> date = new TableColumn<>("Date");
		commitsTable.getColumns().setAll(List.of(idCol, selectedCommitCol, commitMessage, commiter, date));

		idCol.setCellValueFactory((TableColumn.CellDataFeatures<CommitInfo, String> param) -> new ReadOnlyObjectWrapper<>(param.getValue().getCommit().getId()));
		commitMessage.setCellValueFactory((TableColumn.CellDataFeatures<CommitInfo, String> param) -> new ReadOnlyObjectWrapper<>(param.getValue().getCommit().getCommitMessage()));
		commiter.setCellValueFactory((TableColumn.CellDataFeatures<CommitInfo, String> param) -> new ReadOnlyObjectWrapper<>(param.getValue().getCommit().getUsername()));
		date.setCellValueFactory((TableColumn.CellDataFeatures<CommitInfo, String> param) -> new ReadOnlyObjectWrapper<>(param.getValue().getCommit().getDate() == null ? "" : DATE_FORMAT.format(param.getValue().getCommit().getDate().toInstant())));

		selectedCommitCol.setCellFactory(column -> new CheckBoxTableCell<>());
		// at most two ticked: see CommitInfo - the cell used to add a listener every time it was shown
		selectedCommitCol.setCellValueFactory(cellData -> cellData.getValue().isSelected());
		commitsTable.setItems(this.commitsData);

		TableColumns.shortIds(idCol, this.commitsData);
		TableColumns.controlWidth(selectedCommitCol);
		TableColumns.fitToContent(commiter, this.commitsData);
		TableColumns.fitToContent(date, this.commitsData);
		TableColumns.growToFill(commitsTable, commitMessage);

		// commit details view
		CommitDetailView commitDetailView = new CommitDetailView();


		commitsTable.getSelectionModel().selectedItemProperty().addListener((observableValue, oldValue, newValue) -> {
			if (newValue != null) {
				commitDetailView.showCommit(newValue.getCommit());
			} else {
				commitDetailView.showCommit(null);
			}
		});

		// enabled with exactly two ticked: with any other number it opened an error with a stack trace
		Button compareButton = new Button("Compare");
		compareButton.setTooltip(new Tooltip("Tick two commits to compare their associations"));
		compareButton.disableProperty().bind(javafx.beans.binding.Bindings.createBooleanBinding(
				() -> this.tickedCommits().size() != 2, this.commitsData));
		toolBar.getItems().add(compareButton);
		compareButton.setOnAction(e -> {
			List<Commit> selectedCommits = this.tickedCommits();
			if (selectedCommits.size() != 2)
				return;
			CommitComparisonView commitComparisonView = new CommitComparisonView(service, selectedCommits.get(0), selectedCommits.get(1));
			Stage stage = new Stage();
			stage.initStyle(StageStyle.UTILITY);
			stage.initModality(Modality.WINDOW_MODAL);
			stage.setScene(new Scene(commitComparisonView));
			stage.setTitle("Commit Comparison");
			stage.show();
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
				artifactDetailView.showTree(ArtifactDetailView.findNodeWithArtifactViewerRec(artifactDetailView, rootNode));
			}
		});

		// add to split pane
		splitPane.getItems().addAll(commitsTable, detailView);

		Platform.runLater(() -> statusChangedEvent(service));

		service.addListener(this);
	}

	private List<Commit> tickedCommits() {
		List<Commit> ticked = new LinkedList<>();
		for (CommitInfo info : this.commitsData) {
			if (info.isSelected().get())
				ticked.add(info.getCommit());
		}
		return ticked;
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
			Platform.runLater(() -> {
				this.setDisable(true);
				this.commitsData.clear();
			});
		}
	}

	public class CommitInfo {
		private Commit commit;
		private BooleanProperty selected;

		public CommitInfo(Commit c) {
			this.commit = c;
			this.selected = new SimpleBooleanProperty(false);
			// a third tick is taken back
			this.selected.addListener((observable, wasTicked, ticked) -> {
				if (ticked && CommitsView.this.commitsData.stream().filter(x -> x.isSelected().get()).count() > 2)
					this.selected.set(false);
			});
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


