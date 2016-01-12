package at.jku.isse.ecco.gui;

import at.jku.isse.ecco.EccoException;
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
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ToolBar;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;

import java.nio.file.Path;
import java.util.Collection;

public class AssociationsView extends BorderPane implements EccoListener {

	private EccoService service;

	final ObservableList<AssociationInfo> associationsData = FXCollections.observableArrayList();

	public AssociationsView(EccoService service) {
		this.service = service;

		ToolBar toolBar = new ToolBar();
		this.setTop(toolBar);

		Button refreshButton = new Button("Refresh");

		refreshButton.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent e) {
				toolBar.setDisable(true);
				AssociationsView.this.associationsData.clear();

				Task refreshTask = new Task<Void>() {
					@Override
					public Void call() throws EccoException {
						Collection<Association> associations = AssociationsView.this.service.getAssociations();
						Platform.runLater(() -> {
							for (Association association : associations) {
								AssociationsView.this.associationsData.add(new AssociationInfo(String.valueOf(association.getId()), association.getName(), association.getPresenceCondition().toString()));
							}
						});
						Platform.runLater(() -> {
							toolBar.setDisable(false);
						});
						return null;
					}
				};

				new Thread(refreshTask).start();
			}
		});

		toolBar.getItems().add(refreshButton);


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


		this.setCenter(associationsTable);


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
			Collection<Association> associations = service.getAssociations();
			Platform.runLater(() -> {
				for (Association association : associations) {
					this.associationsData.add(new AssociationInfo(String.valueOf(association.getId()), association.getName(), association.getPresenceCondition().toString()));
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

	}

	@Override
	public void fileReadEvent(Path file, ArtifactReader reader) {

	}

	// TODO: add new associations
	public void associationsChangedEvent(Collection<Association> associations) {
		Platform.runLater(() -> {
			for (Association association : associations) {
				this.associationsData.add(new AssociationInfo(String.valueOf(association.getId()), association.getName(), association.getPresenceCondition().toString()));
			}
		});
	}

	@Override
	public void fileWriteEvent(Path file, ArtifactWriter writer) {

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
