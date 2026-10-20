package at.jku.isse.ecco.gui.view;

import at.jku.isse.ecco.composition.LazyCompositionRootNode;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.gui.TableColumns;
import at.jku.isse.ecco.gui.view.artifacts.ArtifactTreeView;
import at.jku.isse.ecco.gui.view.detail.CommitDetailView;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.service.listener.EccoListener;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.ArrayList;
import java.util.List;

public class CommitsView extends BorderPane implements EccoListener {

	/** Sortable and short: "Thu Oct 15 08:54:22 CEST 2026" was cut off at the column's edge. */
	private static final java.time.format.DateTimeFormatter DATE_FORMAT = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(java.time.ZoneId.systemDefault());


	private EccoService service;

	final ObservableList<Commit> commitsData = FXCollections.observableArrayList();

	final TableView<Commit> commitsTable = new TableView<>();

	/**
	 * Lists the commits again: they were read on the FX thread at first (waiting for the service's
	 * lock while a commit ran) and listed from the repository's own collection, which a commit adds to.
	 */
	private final at.jku.isse.ecco.gui.LatestRefresh<List<Commit>> refresher;

	public CommitsView(EccoService service) {
		this.service = service;
		this.refresher = new at.jku.isse.ecco.gui.LatestRefresh<>("Commits", service, svc -> List.copyOf(svc.getCommits()), commits -> {
			this.setDisable(false);
			this.commitsData.clear();
			this.commitsData.addAll(commits);
		}, failure -> new at.jku.isse.ecco.gui.ExceptionAlert(failure).show());

		ToolBar toolBar = new ToolBar();
		this.setTop(toolBar);

		// list of commits: two selected rows are compared - there was a column of ticks for that,
		// beside the row selection that showed the details
		commitsTable.setTableMenuButtonVisible(true);
		commitsTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

		TableColumn<Commit, String> idCol = new TableColumn<>("Id");
		TableColumn<Commit, String> commitMessage = new TableColumn<>("Commit Message");
		TableColumn<Commit, String> commiter = new TableColumn<>("Committer");
		TableColumn<Commit, String> date = new TableColumn<>("Date");
		commitsTable.getColumns().setAll(List.of(idCol, commitMessage, commiter, date));

		idCol.setCellValueFactory((TableColumn.CellDataFeatures<Commit, String> param) -> new ReadOnlyObjectWrapper<>(param.getValue().getId()));
		commitMessage.setCellValueFactory((TableColumn.CellDataFeatures<Commit, String> param) -> new ReadOnlyObjectWrapper<>(param.getValue().getCommitMessage()));
		commiter.setCellValueFactory((TableColumn.CellDataFeatures<Commit, String> param) -> new ReadOnlyObjectWrapper<>(param.getValue().getUsername()));
		date.setCellValueFactory((TableColumn.CellDataFeatures<Commit, String> param) -> new ReadOnlyObjectWrapper<>(param.getValue().getDate() == null ? "" : DATE_FORMAT.format(param.getValue().getDate().toInstant())));
		commitsTable.setItems(this.commitsData);

		TableColumns.shortIds(idCol, this.commitsData);
		TableColumns.fitToContent(commiter, this.commitsData);
		TableColumns.fitToContent(date, this.commitsData);
		TableColumns.growToFill(commitsTable, commitMessage);

		// the details and the artifacts of the commit selected last
		CommitDetailView commitDetailView = new CommitDetailView(service);
		ArtifactTreeView artifactTreeView = new ArtifactTreeView(service);
		artifactTreeView.setPlaceholder("Select a commit to see its artifacts.");
		int[] selected = {0}; // counts the selections: a read overtaken by a later one shows nothing
		commitsTable.getSelectionModel().selectedItemProperty().addListener((observableValue, oldValue, newValue) -> {
			int read = ++selected[0];
			if (newValue == null) {
				commitDetailView.show(null);
				artifactTreeView.setRootNode(null);
				return;
			}
			// its details, and its artifacts as a tree to pick from (it showed the first node of them
			// that had a viewer) - read holding the service's lock, in the background: a commit
			// running meanwhile changes the associations, their conditions and trees
			at.jku.isse.ecco.gui.RepositoryAccess.read(service, "Commit details", svc -> {
				CommitDetailView.Details details = CommitDetailView.read(newValue);
				LazyCompositionRootNode rootNode = new LazyCompositionRootNode();
				for (Association association : newValue.getAssociations())
					rootNode.addOrigNode(association.getRootNode());
				return new Selected(details, rootNode);
			}, shown -> {
				if (read != selected[0])
					return;
				commitDetailView.show(shown.details());
				artifactTreeView.setRootNode(shown.artifacts());
			}, failure -> new at.jku.isse.ecco.gui.ExceptionAlert(failure).show());
		});

		// enabled with exactly two selected: with any other number it opened an error with a stack trace
		Button compareButton = new Button("Compare");
		compareButton.setTooltip(new Tooltip("Select two commits (Shift- or " + (System.getProperty("os.name", "").startsWith("Mac") ? "Cmd" : "Ctrl")
				+ "-click) to compare their associations"));
		compareButton.disableProperty().bind(javafx.beans.binding.Bindings.size(commitsTable.getSelectionModel().getSelectedItems()).isNotEqualTo(2));
		toolBar.getItems().add(compareButton);
		compareButton.setOnAction(e -> {
			List<Commit> selectedCommits = this.selectedCommits();
			if (selectedCommits.size() != 2)
				return;
			CommitComparisonView commitComparisonView = new CommitComparisonView(service, selectedCommits.get(0), selectedCommits.get(1));
			// over the main window, with Cmd+W and Esc: it had no owner - "window modal" to nothing
			at.jku.isse.ecco.gui.Dialogs.open(this.getScene().getWindow(), "Commit Comparison", commitComparisonView, Modality.WINDOW_MODAL);
		});

		SplitPane top = new SplitPane(commitsTable, commitDetailView);
		SplitPane splitPane = new SplitPane(top, artifactTreeView);
		splitPane.setOrientation(Orientation.VERTICAL);
		this.setCenter(splitPane);
		Platform.runLater(() -> {
			top.setDividerPosition(0, 0.6);
			splitPane.setDividerPosition(0, 0.45);
		});

		Platform.runLater(() -> statusChangedEvent(service));

		service.addListener(this);
	}

	/** A selected commit as shown: its details, and its artifacts composed from its associations. */
	private record Selected(CommitDetailView.Details details, LazyCompositionRootNode artifacts) {
	}

	/** The selected commits in the order they are listed. */
	private List<Commit> selectedCommits() {
		List<Commit> selected = new ArrayList<>();
		for (Commit commit : this.commitsData)
			if (this.commitsTable.getSelectionModel().getSelectedItems().contains(commit))
				selected.add(commit);
		return selected;
	}

	@Override
	public void statusChangedEvent(EccoService service) {
		if (service.isInitialized()) {
			this.refresher.request();
		} else {
			this.refresher.clear();
			Platform.runLater(() -> {
				this.setDisable(true);
				this.commitsData.clear();
			});
		}
	}
}
