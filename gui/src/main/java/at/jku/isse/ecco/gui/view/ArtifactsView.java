package at.jku.isse.ecco.gui.view;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.AssociationInfo;
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
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Callback;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.*;

public class ArtifactsView extends BorderPane implements EccoListener {

    private final EccoService service;

    private final ObservableList<AssociationInfoImpl> associationsData = FXCollections.observableArrayList();


    public ArtifactsView(final EccoService service) {
        this.service = service;


        // toolbar
        ToolBar toolBar = new ToolBar();
        this.setTop(toolBar);

        Button refreshButton = new Button("Refresh");

        // selection
        MenuItem selectAllMenuItem = new MenuItem("Select All");
        MenuItem unselectAllMenuItem = new MenuItem("Unselect All");
        MenuItem selectByConfigurationMenuItem = new MenuItem("Select by Configuration");
        MenuButton selectionMenuButton = new MenuButton("Selection");
        selectionMenuButton.getItems().addAll(
                selectAllMenuItem,
                selectByConfigurationMenuItem,
                new SeparatorMenuItem(),
                unselectAllMenuItem
        );

        Button checkoutSelectedButton = new Button("Checkout Selected");
        Button composeSelectedButton = new Button("Compose Selected");

        CheckBox showEmptyAssociationsCheckBox = new CheckBox("Show Associations Without Artifacts");
        CheckBox useSimplifiedLabelsCheckBox = new CheckBox("Use Simplified Labels");

        CheckBox showBelowAtomicCheckBox = new CheckBox("Show Artifacts Below Atomic"); // TODO
        showBelowAtomicCheckBox.setDisable(true);
        CheckBox showBelowFilesCheckBox = new CheckBox("Show Artifacts Below File Level"); // TODO
        showBelowFilesCheckBox.setDisable(true);

        toolBar.getItems().addAll(refreshButton, new Separator(),
                selectionMenuButton, checkoutSelectedButton, composeSelectedButton, new Separator(),
                showEmptyAssociationsCheckBox, new Separator(),
                useSimplifiedLabelsCheckBox, new Separator(),
                showBelowAtomicCheckBox, new Separator(),
                showBelowFilesCheckBox, new Separator());


        FilteredList<AssociationInfoImpl> filteredData = new FilteredList<>(this.associationsData, p -> true);

        showEmptyAssociationsCheckBox.selectedProperty().addListener((ov, oldValue, newValue) ->
                filteredData.setPredicate(associationInfo -> newValue || (associationInfo.getNumArtifacts() > 0)));

        // associations table
        TableView<AssociationInfoImpl> associationsTable = new TableView<>();
        associationsTable.setEditable(true);
        associationsTable.setTableMenuButtonVisible(true);
        associationsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<AssociationInfoImpl, String> idAssociationsCol = new TableColumn<>("Id");
        TableColumn<AssociationInfoImpl, String> conditionAssociationsCol = new TableColumn<>("Condition");
        TableColumn<AssociationInfoImpl, Integer> numArtifactsAssociationsCol = new TableColumn<>("NumArtifacts");

        TableColumn<AssociationInfoImpl, Boolean> selectedAssocationCol = new TableColumn<>("Selected");

        TableColumn<AssociationInfoImpl, Color> highlightedAssocationCol = new TableColumn<>("Highlighted");

        TableColumn<AssociationInfoImpl, String> associationsCol = new TableColumn<>("Associations");

        associationsCol.getColumns().setAll(idAssociationsCol, conditionAssociationsCol, numArtifactsAssociationsCol, selectedAssocationCol, highlightedAssocationCol);
        associationsTable.getColumns().setAll(associationsCol);

        idAssociationsCol.setCellValueFactory((TableColumn.CellDataFeatures<AssociationInfoImpl, String> param) -> new ReadOnlyStringWrapper(param.getValue().getAssociation().getId()));
        conditionAssociationsCol.setCellValueFactory((TableColumn.CellDataFeatures<AssociationInfoImpl, String> param) -> new When(useSimplifiedLabelsCheckBox.selectedProperty()).then(param.getValue().getAssociation().computeCondition().getSimpleModuleRevisionConditionString()).otherwise(param.getValue().getAssociation().computeCondition().getModuleRevisionConditionString()));
        numArtifactsAssociationsCol.setCellValueFactory((TableColumn.CellDataFeatures<AssociationInfoImpl, Integer> param) -> new ReadOnlyObjectWrapper<>(param.getValue().getNumArtifacts()));


        selectedAssocationCol.setCellValueFactory(new PropertyValueFactory<>("selected"));
        selectedAssocationCol.setCellFactory(CheckBoxTableCell.forTableColumn(selectedAssocationCol));
        selectedAssocationCol.setEditable(true);


        class ColorPickerTableCell<Inputs> extends TableCell<Inputs, Color> {
            private final ColorPicker cp;

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
        highlightedAssocationCol.setCellFactory(new Callback<TableColumn<AssociationInfoImpl, Color>, TableCell<AssociationInfoImpl, Color>>() {
            @Override
            public TableCell<AssociationInfoImpl, Color> call(TableColumn<AssociationInfoImpl, Color> param) {
                return new ColorPickerTableCell<>(highlightedAssocationCol);
            }
        });
        highlightedAssocationCol.setEditable(true);


        SortedList<AssociationInfoImpl> sortedData = new SortedList<>(filteredData);
        sortedData.comparatorProperty().bind(associationsTable.comparatorProperty());

        associationsTable.setItems(sortedData);


        ArtifactTreeView artifactTreeView = new ArtifactTreeView(service);

        // split panes
        SplitPane horizontalSplitPane = new SplitPane();
        horizontalSplitPane.setOrientation(Orientation.VERTICAL);
        horizontalSplitPane.getItems().addAll(associationsTable, artifactTreeView);

        this.setCenter(horizontalSplitPane);


        refreshButton.setOnAction(e -> {
            toolBar.setDisable(true);

            artifactTreeView.setRootNode(null);

            Thread th =  new Thread(() -> {
                Collection<? extends Association> associations = ArtifactsView.this.service.getRepository().getAssociations();
                Platform.runLater(() -> {
                    ArtifactsView.this.associationsData.clear();
                    for (Association a : associations) {
                        ArtifactsView.this.associationsData.add(new AssociationInfoImpl(a));
                    }
                    artifactTreeView.setAssociationInfo(ArtifactsView.this.associationsData);

                    toolBar.setDisable(false);
                });
            });
            th.start();
        });

        composeSelectedButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent e) {
                toolBar.setDisable(true);

                Task<Void> composeTask = new Task<>() {
                    @Override
                    public Void call() throws EccoException {
                        Collection<Association> selectedAssociations = new ArrayList<>();
                        for (AssociationInfoImpl associationInfo : ArtifactsView.this.associationsData) {
                            if (associationInfo.isSelected())
                                selectedAssociations.add(associationInfo.getAssociation());
                        }

                        // use composition here to merge selected associations
                        LazyCompositionRootNode rootNode = new LazyCompositionRootNode();
                        for (Association association : selectedAssociations) {
                            rootNode.addOrigNode(association.getRootNode());
                        }
                        Platform.runLater(() -> {
                            artifactTreeView.setRootNode(rootNode);
                            toolBar.setDisable(false);
                        });

                        return null;
                    }
                };

                new Thread(composeTask).start();
            }
        });

        selectAllMenuItem.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent e) {
                toolBar.setDisable(true);
                setAllAssociationsSelected(true);
                toolBar.setDisable(false);
            }
        });

        unselectAllMenuItem.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent e) {
                toolBar.setDisable(true);
                setAllAssociationsSelected(false);
                toolBar.setDisable(false);
            }
        });

        selectByConfigurationMenuItem.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                toolBar.setDisable(true);

                TextInputDialog dialog = new TextInputDialog();
                dialog.setTitle("Configuration");
                dialog.setGraphic(null);
                dialog.setHeaderText(null);
                dialog.setContentText("Configuration:");
                dialog.setResizable(true);
                final Window wnd = dialog.getDialogPane().getScene().getWindow();
                Stage stage = (Stage)wnd;
                stage.setMinWidth(500);
                stage.setMinHeight(150);
                Optional<String> result = dialog.showAndWait();

                if (!result.isPresent()) {
                    toolBar.setDisable(false);
                    return;
                }
                String config = result.get();

                Task<Void> selectionTask = new Task<>() {
                    @Override
                    public Void call() throws EccoException {
                        Set<Association> ass = service.getAssociations(config);
                        LinkedList<AssociationInfoImpl> toSelect = new LinkedList<AssociationInfoImpl>();
                        for (AssociationInfoImpl ai : associationsData) {
                            if (ass.contains(ai.getAssociation())) {
                                toSelect.add(ai);
                            }
                        }

                        Platform.runLater(() -> {
                            setAllAssociationsSelected(false);
                            for (AssociationInfoImpl a : toSelect) {
                                a.setSelected(true);
                            }
                            toolBar.setDisable(false);
                        });

                        return null;
                    }
                };

                new Thread(selectionTask).start();
            }
        });

        checkoutSelectedButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent e) {
                toolBar.setDisable(true);

                Collection<Association> selectedAssociations = new ArrayList<>();
                for (AssociationInfoImpl associationInfo : ArtifactsView.this.associationsData) {
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


        Platform.runLater(() -> {
            horizontalSplitPane.setDividerPosition(0, 0.2);
            if (!service.isInitialized())
                this.setDisable(true);
        });

        // ecco service
        service.addListener(this);
    }

    private void setAllAssociationsSelected(boolean flag) {
        for (AssociationInfoImpl assocInfo : ArtifactsView.this.associationsData) {
            assocInfo.setSelected(flag);
        }
    }

    @Override
    public void statusChangedEvent(EccoService service) {
        if (service.isInitialized()) {
            Platform.runLater(() -> this.setDisable(false));
        } else {
            Platform.runLater(() -> this.setDisable(true));
        }
    }

    public class AssociationInfoImpl implements AssociationInfo {
        private final Association association;

        private final BooleanProperty selected;

        private final ObjectProperty<Color> color;

        private final IntegerProperty numArtifacts;

        private final LinkedList<PropertyChangeListener> listeners;

        public AssociationInfoImpl(Association association) {
            this.association = association;
            this.listeners = new LinkedList<>();
            this.selected = new SimpleBooleanProperty(false);
            this.selected.addListener((observable, oldValue, newValue) ->
                    onPropertyChanged("selected", oldValue, newValue));
            this.numArtifacts = new SimpleIntegerProperty(association.getRootNode().countArtifacts());
            this.numArtifacts.addListener((observable, oldValue, newValue) ->
                    onPropertyChanged("numArtifacts", oldValue, newValue));
            this.color = new SimpleObjectProperty<>(Color.TRANSPARENT);
            this.color.addListener((observable, oldValue, newValue) ->
                    onPropertyChanged("color", oldValue, newValue));
        }

        @Override
        public final Association getAssociation() {
            return this.association;
        }

        @Override
        public void addPropertyChangeListener(PropertyChangeListener listener) {
            listeners.add(listener);
        }

        @Override
        public void removePropertyChangeListener(PropertyChangeListener listener) {
            listeners.remove(listener);
        }

        @Override
        public Object getPropertyValue(String propertyName) {
            switch (propertyName) {
                case "color":
                    return colorProperty().getValue();
                case "selected":
                    return selectedProperty().getValue();
                case "numArtifacts":
                    return numArtifactsProperty().getValue();
                default:
                    return null;
            }
        }

        public boolean isSelected() {
            return this.selected.get();
        }

        public void setSelected(boolean selected) { this.selected.set(selected); }

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

        private void onPropertyChanged(String propertyName, Object oldValue, Object newValue) {
            listeners.forEach(pcl -> pcl.propertyChange(
                    new PropertyChangeEvent(this, propertyName, oldValue, newValue)));
        }
    }
}
