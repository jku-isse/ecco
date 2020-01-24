package at.jku.isse.ecco.gui.view;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.composition.LazyCompositionRootNode;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.gui.ExceptionAlert;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.service.listener.EccoListener;
import javafx.application.Platform;
import javafx.beans.binding.When;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.util.Callback;

import java.util.ArrayList;
import java.util.Collection;

public class ArtifactsView extends BorderPane implements EccoListener {

	private final EccoService service;

	private final ObservableList<AssociationInfo> associationsData = FXCollections.observableArrayList();


	public ArtifactsView(final EccoService service) {
		this.service = service;


		// toolbar
		ToolBar toolBar = new ToolBar();
		this.setTop(toolBar);

		Button refreshButton = new Button("Refresh");

		Button selectAllButton = new Button("Select All");
		Button unselectAllButton = new Button("Unselect All");
		Button checkoutSelectedButton = new Button("Checkout Selected");

		Button composeSelectedButton = new Button("Compose Selected");

		CheckBox showEmptyAssociationsCheckBox = new CheckBox("Show Associations Without Artifacts");
		CheckBox useSimplifiedLabelsCheckBox = new CheckBox("Use Simplified Labels");

		CheckBox showBelowAtomicCheckBox = new CheckBox("Show Artifacts Below Atomic"); // TODO
		CheckBox showBelowFilesCheckBox = new CheckBox("Show Artifacts Below File Level"); // TODO

		toolBar.getItems().addAll(refreshButton, new Separator(), selectAllButton, unselectAllButton, checkoutSelectedButton, composeSelectedButton, new Separator(), showEmptyAssociationsCheckBox, new Separator(), useSimplifiedLabelsCheckBox, new Separator(), showBelowAtomicCheckBox, new Separator(), showBelowFilesCheckBox, new Separator());


		FilteredList<AssociationInfo> filteredData = new FilteredList<>(this.associationsData, p -> true);

		showEmptyAssociationsCheckBox.selectedProperty().addListener((ov, oldValue, newValue) -> {
			filteredData.setPredicate(associationInfo -> newValue || (associationInfo.getNumArtifacts() > 0));
		});


		// associations table
		TableView<AssociationInfo> associationsTable = new TableView<>();
		associationsTable.setEditable(true);
		associationsTable.setTableMenuButtonVisible(true);
		associationsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

		TableColumn<AssociationInfo, String> idAssociationsCol = new TableColumn<>("Id");
		TableColumn<AssociationInfo, String> conditionAssociationsCol = new TableColumn<>("Condition");
		TableColumn<AssociationInfo, Integer> numArtifactsAssociationsCol = new TableColumn<>("NumArtifacts");

		TableColumn<AssociationInfo, Boolean> selectedAssocationCol = new TableColumn<>("Selected");

		TableColumn<AssociationInfo, Color> highlightedAssocationCol = new TableColumn<>("Highlighted");

		TableColumn<AssociationInfo, String> associationsCol = new TableColumn<>("Associations");

		associationsCol.getColumns().setAll(idAssociationsCol, conditionAssociationsCol, numArtifactsAssociationsCol, selectedAssocationCol, highlightedAssocationCol);
		associationsTable.getColumns().setAll(associationsCol);

		idAssociationsCol.setCellValueFactory((TableColumn.CellDataFeatures<AssociationInfo, String> param) -> new ReadOnlyStringWrapper(param.getValue().getAssociation().getId()));
		conditionAssociationsCol.setCellValueFactory((TableColumn.CellDataFeatures<AssociationInfo, String> param) -> new When(useSimplifiedLabelsCheckBox.selectedProperty()).then(param.getValue().getAssociation().computeCondition().getSimpleModuleRevisionConditionString()).otherwise(param.getValue().getAssociation().computeCondition().getModuleRevisionConditionString()));
		numArtifactsAssociationsCol.setCellValueFactory((TableColumn.CellDataFeatures<AssociationInfo, Integer> param) -> new ReadOnlyObjectWrapper<>(param.getValue().getNumArtifacts()));


		selectedAssocationCol.setCellValueFactory(new PropertyValueFactory<>("selected"));
		selectedAssocationCol.setCellFactory(CheckBoxTableCell.forTableColumn(selectedAssocationCol));
		selectedAssocationCol.setEditable(true);


		class ColorPickerTableCell<Inputs> extends TableCell<Inputs, Color> {
			private ColorPicker cp;

			public ColorPickerTableCell(TableColumn<Inputs, Color> column) {
				this.getStyleClass().add("color-picker-table-cell");

				this.cp = new ColorPicker();

				this.cp.editableProperty().bind(column.editableProperty());
				this.cp.disableProperty().bind(column.editableProperty().not());

				this.cp.setOnShowing(event -> {
					getTableView().edit(getTableRow().getIndex(), column);
				});

				this.cp.valueProperty().addListener((observable, oldValue, newValue) -> {
					if (isEditing()) {
						commitEdit(newValue);
					}
				});
//				this.cp.setOnAction(event -> {
//					if (isEditing()) {
//						commitEdit(this.cp.getValue());
//					}
//				});
//				this.cp.setOnHiding(event -> {
//					if (isEditing()) {
//						commitEdit(this.cp.getValue());
//					}
//				});

				this.cp.setValue(getItem());

				this.setGraphic(this.cp);
				this.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
				this.setEditable(true);
				this.setAlignment(Pos.CENTER);
			}

			@Override
			protected void updateItem(Color item, boolean empty) {
				super.updateItem(item, empty);
//				this.cp.setVisible(!empty);
//				this.cp.setValue(item);

				setText(null);
				if (empty) {
					setGraphic(null);
				} else {
					this.cp.setValue(item);
					this.setGraphic(this.cp);
				}

				this.setBackground(new Background(new BackgroundFill(item, null, null)));
				//this.backgroundProperty().bind(Bindings.createObjectBinding(() -> new Background(new BackgroundFill(this.cp.getValue(), null, null)), this.cp.valueProperty()));
			}
		}

		highlightedAssocationCol.setCellValueFactory(new PropertyValueFactory<>("color"));
		highlightedAssocationCol.setCellFactory(new Callback<TableColumn<AssociationInfo, Color>, TableCell<AssociationInfo, Color>>() {
			@Override
			public TableCell<AssociationInfo, Color> call(TableColumn<AssociationInfo, Color> param) {
				return new ColorPickerTableCell<>(highlightedAssocationCol);
			}
		});
		highlightedAssocationCol.setEditable(true);


		SortedList<AssociationInfo> sortedData = new SortedList<>(filteredData);
		sortedData.comparatorProperty().bind(associationsTable.comparatorProperty());

		associationsTable.setItems(sortedData);


		ArtifactTreeView artifactTreeView = new ArtifactTreeView(service);
		artifactTreeView.setAssociationInfo(this.associationsData);

		// split panes
		SplitPane horizontalSplitPane = new SplitPane();
		horizontalSplitPane.setOrientation(Orientation.VERTICAL);
		horizontalSplitPane.getItems().addAll(associationsTable, artifactTreeView);

		this.setCenter(horizontalSplitPane);


		refreshButton.setOnAction(e -> {
			toolBar.setDisable(true);

			artifactTreeView.setRootNode(null);

			Task refreshTask = new Task<Void>() {
				@Override
				public Void call() throws EccoException {
					Collection<? extends Association> associations = ArtifactsView.this.service.getRepository().getAssociations();
					Platform.runLater(() -> {
						ArtifactsView.this.associationsData.clear();
						for (Association a : associations) {
							ArtifactsView.this.associationsData.add(new AssociationInfo(a));
						}
					});
					Platform.runLater(() -> toolBar.setDisable(false));
					return null;
				}
			};

			new Thread(refreshTask).start();
		});

		composeSelectedButton.setOnAction(new EventHandler<ActionEvent>() {
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
						LazyCompositionRootNode rootNode = new LazyCompositionRootNode();
						for (Association association : selectedAssociations) {
							rootNode.addOrigNode(association.getRootNode());
						}
						Platform.runLater(() -> artifactTreeView.setRootNode(rootNode));

						Platform.runLater(() -> toolBar.setDisable(false));
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

		unselectAllButton.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent e) {
				toolBar.setDisable(true);

				for (AssociationInfo assocInfo : ArtifactsView.this.associationsData) {
					assocInfo.setSelected(false);
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
					final LazyCompositionRootNode rootNode = new LazyCompositionRootNode();
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


		showEmptyAssociationsCheckBox.setSelected(false);
		useSimplifiedLabelsCheckBox.setSelected(true);


		Platform.runLater(() -> horizontalSplitPane.setDividerPosition(0, 0.2));


		// ecco service
		service.addListener(this);

		if (!service.isInitialized())
			this.setDisable(true);
	}


	@Override
	public void statusChangedEvent(EccoService service) {
		if (service.isInitialized()) {
			Platform.runLater(() -> this.setDisable(false));
		} else {
			Platform.runLater(() -> this.setDisable(true));
		}
	}


	public class AssociationInfo {
		private Association association;

		private BooleanProperty selected;

		private ObjectProperty<Color> color;

		private IntegerProperty numArtifacts;

		public AssociationInfo(Association association) {
			this.association = association;
			this.selected = new SimpleBooleanProperty(false);
			this.numArtifacts = new SimpleIntegerProperty(association.getRootNode().countArtifacts());
			this.color = new SimpleObjectProperty<Color>(Color.TRANSPARENT);
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

		public ObjectProperty<Color> colorProperty() {
			return this.color;
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
