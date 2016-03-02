package at.jku.isse.ecco.gui.view;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.EccoService;
import at.jku.isse.ecco.EccoUtil;
import at.jku.isse.ecco.composition.CompositionRootNode;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.gui.ExceptionAlert;
import at.jku.isse.ecco.gui.view.detail.ArtifactDetailView;
import at.jku.isse.ecco.listener.EccoListener;
import at.jku.isse.ecco.plugin.artifact.ArtifactReader;
import at.jku.isse.ecco.plugin.artifact.ArtifactWriter;
import at.jku.isse.ecco.tree.RootNode;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Orientation;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;

public class ArtifactsView extends BorderPane implements EccoListener {

	private EccoService service;

	private final ObservableList<AssociationInfo> associationsData = FXCollections.observableArrayList();

	private final ArtifactDetailView artifactDetailView;

	public ArtifactsView(EccoService service) {
		this.service = service;

		this.artifactDetailView = new ArtifactDetailView(service);


		// toolbar
		ToolBar toolBar = new ToolBar();
		this.setTop(toolBar);

		Button refreshButton = new Button("Refresh");
		toolBar.getItems().add(refreshButton);

		Button compositionButton = new Button("Compose");
		toolBar.getItems().add(compositionButton);

		Button selectAllButton = new Button("Select All");
		toolBar.getItems().add(selectAllButton);

		Button checkoutSelectedButton = new Button("Checkout Selected");
		toolBar.getItems().add(checkoutSelectedButton);


		FilteredList<AssociationInfo> filteredData = new FilteredList<>(this.associationsData, p -> true);

		CheckBox showEmptyAssociationsCheckBox = new CheckBox("Show Associations Without Artifacts");
		toolBar.getItems().add(showEmptyAssociationsCheckBox);
		showEmptyAssociationsCheckBox.selectedProperty().addListener(new ChangeListener<Boolean>() {
			public void changed(ObservableValue<? extends Boolean> ov, Boolean oldValue, Boolean newValue) {
				filteredData.setPredicate(associationInfo -> {
					if (newValue) {
						return true;
					} else {
						return (associationInfo.getNumArtifacts() > 0);
					}
				});
			}
		});


		// associations table
		TableView<AssociationInfo> associationsTable = new TableView<>();
		associationsTable.setEditable(true);
		associationsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

		TableColumn<AssociationInfo, Integer> idAssociationsCol = new TableColumn<>("Id");
		TableColumn<AssociationInfo, String> nameAssociationsCol = new TableColumn<>("Name");
		TableColumn<AssociationInfo, String> conditionAssociationsCol = new TableColumn<>("Condition");
		TableColumn<AssociationInfo, Integer> numArtifactsAssociationsCol = new TableColumn<>("NumArtifacts");

		TableColumn<AssociationInfo, Boolean> selectedAssocationCol = new TableColumn<>("Selected");

		TableColumn<AssociationInfo, String> associationsCol = new TableColumn<>("Associations");

		associationsCol.getColumns().setAll(idAssociationsCol, nameAssociationsCol, conditionAssociationsCol, numArtifactsAssociationsCol, selectedAssocationCol);
		associationsTable.getColumns().setAll(associationsCol);

		idAssociationsCol.setCellValueFactory((TableColumn.CellDataFeatures<AssociationInfo, Integer> param) -> new ReadOnlyObjectWrapper<>(param.getValue().getAssociation().getId()));
		nameAssociationsCol.setCellValueFactory((TableColumn.CellDataFeatures<AssociationInfo, String> param) -> new ReadOnlyStringWrapper(param.getValue().getAssociation().getName()));
		conditionAssociationsCol.setCellValueFactory((TableColumn.CellDataFeatures<AssociationInfo, String> param) -> new ReadOnlyStringWrapper(param.getValue().getAssociation().getPresenceCondition().toString()));
		numArtifactsAssociationsCol.setCellValueFactory((TableColumn.CellDataFeatures<AssociationInfo, Integer> param) -> new ReadOnlyObjectWrapper<>(param.getValue().getNumArtifacts()));


		selectedAssocationCol.setCellValueFactory(new PropertyValueFactory<>("selected"));
		selectedAssocationCol.setCellFactory(CheckBoxTableCell.forTableColumn(selectedAssocationCol));
		selectedAssocationCol.setEditable(true);


		SortedList<AssociationInfo> sortedData = new SortedList<>(filteredData);
		sortedData.comparatorProperty().bind(associationsTable.comparatorProperty());

		associationsTable.setItems(sortedData);


		ArtifactsTreeView artifactTreeView = new ArtifactsTreeView();


		artifactTreeView.getSelectionModel().selectedItemProperty().addListener((observableValue, oldValue, newValue) -> {
			if (newValue != null) {
				this.artifactDetailView.showTree(newValue.getValue());
			}
		});


		// horizontal split pane
		SplitPane horizontalSplitPane = new SplitPane();
		horizontalSplitPane.setOrientation(Orientation.VERTICAL);

		horizontalSplitPane.getItems().add(associationsTable);
		horizontalSplitPane.getItems().add(artifactTreeView);
		horizontalSplitPane.getItems().add(this.artifactDetailView);


		this.setCenter(horizontalSplitPane);


		refreshButton.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent e) {
				toolBar.setDisable(true);

				Task refreshTask = new Task<Void>() {
					@Override
					public Void call() throws EccoException {
						Collection<Association> associations = ArtifactsView.this.service.getAssociations();
						Platform.runLater(() -> {
							ArtifactsView.this.associationsData.clear();
							for (Association a : associations) {
								ArtifactsView.this.associationsData.add(new AssociationInfo(a));
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

		compositionButton.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent e) {
				toolBar.setDisable(true);

				Task composeTask = new Task<Void>() {
					@Override
					public Void call() throws EccoException {
						Collection<Association> selectedAssociations = new ArrayList<>();
						for (AssociationInfo associationInfo : ArtifactsView.this.associationsData) {
							if (associationInfo.isSelected())
								selectedAssociations.add(associationInfo.getAssociation());
						}

						// use composition here to merge selected associations
						CompositionRootNode rootNode = null;
						if (!selectedAssociations.isEmpty()) {
							rootNode = new CompositionRootNode();
							for (Association association : selectedAssociations) {
								rootNode.addOrigNode(association.getRootNode());
							}
						}
						final RootNode finalRootNode = rootNode;
						//final RootNode finalRootNode = selectedAssociations.iterator().next().getRootNode();
						Platform.runLater(() -> {
							artifactTreeView.setRootNode(finalRootNode);
						});

						Platform.runLater(() -> {
							toolBar.setDisable(false);
						});
						return null;
					}
				};

				new Thread(composeTask).start();
			}
		});

		selectAllButton.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent e) {
				toolBar.setDisable(true);

				for (AssociationInfo assocInfo : ArtifactsView.this.associationsData) {
					assocInfo.setSelected(true);
				}

				toolBar.setDisable(false);
			}
		});

		checkoutSelectedButton.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent e) {
				toolBar.setDisable(true);

				Collection<Association> selectedAssociations = new ArrayList<>();
				for (AssociationInfo associationInfo : ArtifactsView.this.associationsData) {
					if (associationInfo.isSelected())
						selectedAssociations.add(associationInfo.getAssociation());
				}

				// use composition here to merge selected associations
				if (!selectedAssociations.isEmpty()) {
					final CompositionRootNode rootNode = new CompositionRootNode();
					for (Association association : selectedAssociations) {
						rootNode.addOrigNode(association.getRootNode());
					}

					Task checkoutTask = new Task<Void>() {
						@Override
						public Void call() throws EccoException {
							ArtifactsView.this.service.checkout(rootNode);
							return null;
						}

						public void finished() {
							//toolBar.setDisable(false);
						}

						@Override
						public void succeeded() {
							super.succeeded();
							this.finished();

							Alert alert = new Alert(Alert.AlertType.INFORMATION);
							alert.setTitle("Checkout Successful");
							alert.setHeaderText("Checkout Successful");
							alert.setContentText("Checkout Successful!");

							alert.showAndWait();
						}

						@Override
						public void cancelled() {
							super.cancelled();
						}

						@Override
						public void failed() {
							super.failed();
							this.finished();

							ExceptionAlert alert = new ExceptionAlert(this.getException());
							alert.setTitle("Checkout Error");
							alert.setHeaderText("Checkout Error");

							alert.showAndWait();
						}
					};
					new Thread(checkoutTask).start();
				}

				toolBar.setDisable(false);
			}
		});


		showEmptyAssociationsCheckBox.setSelected(true);


		// ecco service
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

	@Override
	public void fileWriteEvent(Path file, ArtifactWriter writer) {

	}


	public class AssociationInfo {
		private Association association;

		private BooleanProperty selected;

		private IntegerProperty numArtifacts;

		public AssociationInfo(Association association) {
			this.association = association;
			this.selected = new SimpleBooleanProperty(false);
			this.numArtifacts = new SimpleIntegerProperty(EccoUtil.countArtifactsInAssociation(association));
		}

		public Association getAssociation() {
			return this.association;
		}

		public boolean isSelected() {
			return this.selected.get();
		}

		public void setSelected(boolean selected) {
			this.selected.set(selected);
		}

		public BooleanProperty selectedProperty() {
			return this.selected;
		}

		public int getNumArtifacts() {
			return this.numArtifacts.get();
		}

		public void setNumArtifacts(int numArtifacts) {
			this.numArtifacts.set(numArtifacts);
		}

		public IntegerProperty numArtifactsProperty() {
			return this.numArtifacts;
		}
	}

}
