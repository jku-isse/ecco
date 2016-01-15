package at.jku.isse.ecco.gui;

import at.jku.isse.ecco.EccoService;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.listener.EccoListener;
import at.jku.isse.ecco.plugin.artifact.ArtifactReader;
import at.jku.isse.ecco.plugin.artifact.ArtifactWriter;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ToolBar;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;

import java.nio.file.Path;
import java.util.Collection;

public class CommitsView extends BorderPane implements EccoListener {

	private EccoService service;

	final ObservableList<CommitInfo> commitsData = FXCollections.observableArrayList();
	final ObservableList<AssociationInfo> associationsData = FXCollections.observableArrayList();

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

		TableColumn<CommitInfo, String> idCommitterCol = new TableColumn<CommitInfo, String>("Id");
		TableColumn<CommitInfo, String> committerCol = new TableColumn<CommitInfo, String>("Committer");
		TableColumn<CommitInfo, String> commitsCol = new TableColumn<CommitInfo, String>("Commits");

		commitsCol.getColumns().addAll(idCommitterCol, committerCol);
		commitsTable.getColumns().setAll(commitsCol);

		idCommitterCol.setCellValueFactory(new PropertyValueFactory<CommitInfo, String>("id"));
		committerCol.setCellValueFactory(new PropertyValueFactory<CommitInfo, String>("committer"));

		commitsTable.setItems(this.commitsData);

//		TitledPane commitsTitledPane = new TitledPane("Commits", commitsTable);
//		commitsTitledPane.setAnimated(false);
//		commitsTitledPane.setCollapsible(false);

		commitsTable.getSelectionModel().selectedItemProperty().addListener((observableValue, oldValue, newValue) -> {
			//Check whether item is selected and set value of selected item to Label
			if (newValue != null) {

				CommitsView.this.associationsData.clear();
				for (Association association : newValue.getAssociations()) {
					CommitsView.this.associationsData.add(new AssociationInfo(String.valueOf(association.getId()), association.getName(), association.getPresenceCondition().toString()));
				}
			}
		});


		// list of associations
		TableView<AssociationInfo> associationsTable = new TableView<AssociationInfo>();
		associationsTable.setEditable(false);
		associationsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

		TableColumn<AssociationInfo, String> idAssociationsCol = new TableColumn<AssociationInfo, String>("Id");
		TableColumn<AssociationInfo, String> nameAssociationsCol = new TableColumn<AssociationInfo, String>("Name");
		TableColumn<AssociationInfo, String> conditionAssociationsCol = new TableColumn<AssociationInfo, String>("Condition");
		TableColumn<AssociationInfo, String> associationsCol = new TableColumn<AssociationInfo, String>("Associations");

		associationsCol.getColumns().setAll(idAssociationsCol, nameAssociationsCol, conditionAssociationsCol);
		associationsTable.getColumns().setAll(associationsCol);

		idAssociationsCol.setCellValueFactory(new PropertyValueFactory<AssociationInfo, String>("id"));
		nameAssociationsCol.setCellValueFactory(new PropertyValueFactory<AssociationInfo, String>("name"));
		conditionAssociationsCol.setCellValueFactory(new PropertyValueFactory<AssociationInfo, String>("condition"));

		associationsTable.setItems(this.associationsData);

//		TitledPane associationsTitledPane = new TitledPane("Associations", associationsTable);
//		associationsTitledPane.setAnimated(false);
//		associationsTitledPane.setCollapsible(false);


		// add to split pane
		splitPane.getItems().addAll(commitsTable, associationsTable);


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
					this.commitsData.add(new CommitInfo(String.valueOf(commit.getId()), commit.getCommiter(), commit.getAssociations()));
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
			this.commitsData.add(new CommitInfo(String.valueOf(commit.getId()), commit.getCommiter(), commit.getAssociations()));
		});
	}

	@Override
	public void fileReadEvent(Path file, ArtifactReader reader) {

	}

	@Override
	public void fileWriteEvent(Path file, ArtifactWriter writer) {

	}

	public static class CommitInfo {
		private final SimpleStringProperty id;
		private final SimpleStringProperty committer;

		Collection<Association> associations;

		public Collection<Association> getAssociations() {
			return this.associations;
		}

		private CommitInfo(String id, String committer, Collection<Association> associations) {
			this.id = new SimpleStringProperty(id);
			this.committer = new SimpleStringProperty(committer);
			this.associations = associations;
		}

		public String getId() {
			return this.id.get();
		}

		public void setId(String id) {
			this.id.set(id);
		}

		public String getCommitter() {
			return this.committer.get();
		}

		public void setCommitter(String committer) {
			this.committer.set(committer);
		}
	}

	public static class AssociationInfo {
		private final SimpleStringProperty id;
		private final SimpleStringProperty name;
		private final SimpleStringProperty condition;

		private AssociationInfo(String id, String name, String condition) {
			this.id = new SimpleStringProperty(id);
			this.name = new SimpleStringProperty(name);
			this.condition = new SimpleStringProperty(condition);
		}

		public String getId() {
			return this.id.get();
		}

		public void setId(String id) {
			this.id.set(id);
		}

		public String getName() {
			return this.name.get();
		}

		public void setName(String id) {
			this.name.set(id);
		}

		public String getCondition() {
			return this.condition.get();
		}

		public void setCommitter(String committer) {
			this.condition.set(committer);
		}
	}

}
