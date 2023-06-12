package at.jku.isse.ecco.gui.view;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.core.Variant;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.gui.view.detail.VariantDetailView;
import at.jku.isse.ecco.gui.view.operation.VariantView;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.service.listener.EccoListener;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;

public class VariantsView extends BorderPane implements EccoListener {

    private EccoService service;

    final ObservableList<VariantsInfo> variantsDataSelected = FXCollections.observableArrayList();


    public VariantsView(EccoService service) {
        super();
        this.service = service;


        ToolBar toolBar = new ToolBar();
        this.setTop(toolBar);

        Button refreshButton = new Button("Refresh");
        toolBar.getItems().add(refreshButton);
        refreshButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent e) {
                toolBar.setDisable(true);
                VariantsView.this.variantsDataSelected.clear();

                Task variantsRefreshTask = new Task<Void>() {
                    @Override
                    public Void call() throws EccoException {
                        Collection<? extends Variant> variants = VariantsView.this.service.getRepository().getVariants();
                        Platform.runLater(() -> {
                            for (Variant variant : variants) {
                                //VariantsView.this.variantsData.add(variant);
                                VariantsView.this.variantsDataSelected.add(new VariantsInfo(variant));
                            }
                        });
                        Platform.runLater(() -> toolBar.setDisable(false));
                        return null;
                    }
                };

                new Thread(variantsRefreshTask).start();
            }
        });

        toolBar.getItems().addAll(new Separator());
        Button selectAllButton = new Button("Select All");
        toolBar.getItems().addAll(selectAllButton, new Separator());
        Button unselectAllButton = new Button("Unselect All");
        toolBar.getItems().addAll(unselectAllButton);

        selectAllButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent e) {
                //toolBar.setDisable(true);
                toolBar.setDisable(false);
                for (VariantsView.VariantsInfo variantInfo : VariantsView.this.variantsDataSelected) {
                    variantInfo.setSelected(true);
                }

                toolBar.setDisable(false);
            }
        });

        unselectAllButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent e) {
                //toolBar.setDisable(true);
                toolBar.setDisable(false);
                for (VariantsView.VariantsInfo variantInfo : VariantsView.this.variantsDataSelected) {
                    variantInfo.setSelected(false);
                }

                toolBar.setDisable(false);
            }
        });

        toolBar.getItems().addAll(new Separator());
        Button addButton = new Button("Add New Variant");
        addButton.setDisable(false);
        toolBar.getItems().add(addButton);
        addButton.setOnAction(event -> this.openDialog("Add New Variant", new VariantView(this.service)));


        toolBar.getItems().addAll(new Separator());
        Button removeButton = new Button("Remove Variant Selected");
        toolBar.getItems().add(removeButton);
        removeButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent e) {
                toolBar.setDisable(true);

                Collection<Variant> selectedVariants = new ArrayList<>();
                for (VariantsView.VariantsInfo variantInfo : VariantsView.this.variantsDataSelected) {
                    if (variantInfo.isSelected())
                        selectedVariants.add(variantInfo.getVariant());
                }

                if (!selectedVariants.isEmpty()) {
                    for (Variant variant : selectedVariants) {
                        VariantsView.this.service.removeVariant(variant.getConfiguration());
                    }

                }

                toolBar.setDisable(false);

            }
        });


        toolBar.getItems().add(new Separator());
        FilteredList<VariantsView.VariantsInfo> filteredData = new FilteredList<>(this.variantsDataSelected, p -> true);



        SplitPane splitPane = new SplitPane();
        this.setCenter(splitPane);


        Label baseDirLabel = new Label("Base Directory: ");
        TextField baseDirTextField = new TextField(service.getBaseDir().toString());
        baseDirTextField.setDisable(false);
        baseDirLabel.setLabelFor(baseDirTextField);
        Button selectBaseDirectoryButton = new Button("...");
        toolBar.getItems().addAll(baseDirLabel, baseDirTextField, selectBaseDirectoryButton);

        selectBaseDirectoryButton.setOnAction(event -> {
            final DirectoryChooser directoryChooser = new DirectoryChooser();
            try {
                Path directory = Paths.get(baseDirTextField.getText());
                if (Files.exists(directory) && Files.isDirectory(directory))
                    directoryChooser.setInitialDirectory(directory.toFile());
            } catch (Exception e) {
                // do nothing
            }
            final File selectedDirectory = directoryChooser.showDialog(this.getScene().getWindow());
            if (selectedDirectory != null) {
                baseDirTextField.setText(selectedDirectory.toPath().toString());
            }
        });

        Button checkoutSelectedButton = new Button("Checkout");
        toolBar.getItems().addAll(checkoutSelectedButton, new Separator());
        checkoutSelectedButton.setOnAction(new EventHandler<ActionEvent>() {

            @Override
            public void handle(ActionEvent e) {
                toolBar.setDisable(true);

                Collection<Variant> selectedVariants = new ArrayList<>();
                for (VariantsView.VariantsInfo variantInfo : VariantsView.this.variantsDataSelected) {
                    if (variantInfo.isSelected())
                        selectedVariants.add(variantInfo.getVariant());
                }

                // use composition here to merge selected associations
                if (!selectedVariants.isEmpty()) {
                    for (Variant variant : selectedVariants) {
                        String varname = variant.getName();
                        if (varname.equals(""))
                            varname = variant.getId();
                        Path baseDir = Paths.get(baseDirTextField.getText() + File.separator + varname);
                        File checkoutfile = new File(String.valueOf(baseDir));
                        if (!checkoutfile.exists())
                            checkoutfile.mkdir();
                        VariantsView.this.service.setBaseDir(baseDir);
                        VariantsView.this.service.checkout(variant.getConfiguration());
                    }


                }

                toolBar.setDisable(false);
            }
        });

        TextField searchField = new TextField();
        Button searchButton = new Button("Search Feature Revision");
        toolBar.getItems().addAll(searchField, searchButton);
        searchButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent e) {
                toolBar.setDisable(true);
                VariantsView.this.variantsDataSelected.clear();

                Task searchTask = new Task<Void>() {
                    @Override
                    public Void call() throws EccoException {
                        Collection<? extends Variant> variants = VariantsView.this.service.getRepository().getVariants();
                        Platform.runLater(() -> {
                            for (Variant variant : variants) {
                                for (FeatureRevision f : variant.getConfiguration().getFeatureRevisions()) {
                                    if (f.getFeatureRevisionString().equals(searchField.getText())) {
                                        VariantsView.this.variantsDataSelected.add(new VariantsInfo(variant));
                                    }
                                }
                            }
                        });
                        Platform.runLater(() -> toolBar.setDisable(false));
                        return null;
                    }
                };

                new Thread(searchTask).start();
            }
        });


        Button addSelectedButton = new Button("Add Feature Revision");
        toolBar.getItems().addAll(addSelectedButton);
        addSelectedButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent e) {
                toolBar.setDisable(true);
                ObservableList<VariantsInfo> variantsDataSelectedAux = FXCollections.observableArrayList();
                variantsDataSelectedAux.addAll(VariantsView.this.variantsDataSelected);
                VariantsView.this.variantsDataSelected.clear();

                Task addFeatureRevisionTask = new Task<Void>() {
                    @Override
                    public Void call() throws EccoException {
                        Platform.runLater(() -> {
                            for (VariantsInfo variantInfo : variantsDataSelectedAux) {
                                if (variantInfo.isSelected()) {
                                    String configuration = variantInfo.getVariant().getConfiguration().toString() + "," + searchField.getText();
                                    String name = variantInfo.getVariant().getName();
                                    String id = variantInfo.getVariant().getId();
                                    Configuration config = VariantsView.this.service.parseConfigurationString(configuration);
                                    VariantsView.this.service.updateVariant(config, name, id, VariantsView.this.service);
                                }
                            }
                            Collection<? extends Variant> variants = VariantsView.this.service.getRepository().getVariants();
                            for (Variant variant : variants) {
                                VariantsView.this.variantsDataSelected.add(new VariantsInfo(variant));
                            }
                        });
                        Platform.runLater(() -> toolBar.setDisable(false));
                        return null;
                    }
                };

                new Thread(addFeatureRevisionTask).start();
            }
        });


        Button removeSelectedButton = new Button("Remove Feature Revision");
        toolBar.getItems().addAll(removeSelectedButton);
        removeSelectedButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent e) {
                toolBar.setDisable(true);
                ObservableList<VariantsInfo> variantsDataSelectedAux = FXCollections.observableArrayList();
                variantsDataSelectedAux.addAll(VariantsView.this.variantsDataSelected);
                VariantsView.this.variantsDataSelected.clear();

                Task updateFeatureRevisionTask = new Task<Void>() {
                    @Override
                    public Void call() throws EccoException {
                        Platform.runLater(() -> {
                            for (VariantsInfo variantInfo : variantsDataSelectedAux) {
                                if (variantInfo.isSelected()) {
                                    for (FeatureRevision f : variantInfo.getVariant().getConfiguration().getFeatureRevisions()) {
                                        if (f.getFeatureRevisionString().equals(searchField.getText())) {
                                            VariantsView.this.service.removeFeatureRevision(f, variantInfo.getVariant().getId(), VariantsView.this.service);
                                        }
                                    }
                                }
                            }
                            Collection<? extends Variant> variants = VariantsView.this.service.getRepository().getVariants();
                            for (Variant variant : variants) {
                                VariantsView.this.variantsDataSelected.add(new VariantsInfo(variant));
                            }
                        });
                        Platform.runLater(() -> toolBar.setDisable(false));
                        return null;
                    }
                };

                new Thread(updateFeatureRevisionTask).start();
            }
        });


        TextField updateField = new TextField();
        toolBar.getItems().addAll(updateField);

        Button updateSelectedButton = new Button("Update Feature Revision");
        toolBar.getItems().addAll(updateSelectedButton);
        updateSelectedButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent e) {
                toolBar.setDisable(true);
                ObservableList<VariantsInfo> variantsDataSelectedAux = FXCollections.observableArrayList();
                variantsDataSelectedAux.addAll(VariantsView.this.variantsDataSelected);
                VariantsView.this.variantsDataSelected.clear();

                Task updateFeatureRevisionTask = new Task<Void>() {
                    @Override
                    public Void call() throws EccoException {
                        Platform.runLater(() -> {
                            for (VariantsInfo variantInfo : variantsDataSelectedAux) {
                                if (variantInfo.isSelected()) {
                                    for (FeatureRevision f : variantInfo.getVariant().getConfiguration().getFeatureRevisions()) {
                                        if (f.getFeatureRevisionString().equals(searchField.getText())) {
                                            VariantsView.this.service.updateFeatureRevision(f, updateField.getText(), variantInfo.getVariant().getId(), VariantsView.this.service);
                                        }
                                    }
                                }
                            }
                            Collection<? extends Variant> variants = VariantsView.this.service.getRepository().getVariants();
                            for (Variant variant : variants) {
                                VariantsView.this.variantsDataSelected.add(new VariantsInfo(variant));
                            }
                        });
                        Platform.runLater(() -> toolBar.setDisable(false));
                        return null;
                    }
                };

                new Thread(updateFeatureRevisionTask).start();
            }
        });


        // list of variants
        TableView<VariantsInfo> variantsTable = new TableView<>();
        variantsTable.setEditable(true);
        variantsTable.setTableMenuButtonVisible(true);
        variantsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<VariantsInfo, String> idCol = new TableColumn<>("Id");
        TableColumn<VariantsInfo, String> nameCol = new TableColumn<>("Name");
        TableColumn<VariantsInfo, String> configCol = new TableColumn<>("Configuration");
        TableColumn<VariantsInfo, String> variantsCol = new TableColumn<>("Variants");
        TableColumn<VariantsView.VariantsInfo, Boolean> selectedVariantCol = new TableColumn<>("Selected");

        variantsCol.getColumns().addAll(idCol, nameCol, configCol, selectedVariantCol);
        variantsTable.getColumns().setAll(variantsCol);


        idCol.setCellValueFactory((TableColumn.CellDataFeatures<VariantsInfo, String> param) -> new ReadOnlyStringWrapper(param.getValue().getVariant().getId()));
        nameCol.setCellValueFactory((TableColumn.CellDataFeatures<VariantsInfo, String> param) -> new ReadOnlyStringWrapper(param.getValue().getVariant().getName()));
        configCol.setCellValueFactory((TableColumn.CellDataFeatures<VariantsInfo, String> param) -> new ReadOnlyStringWrapper(param.getValue().getVariant().getConfiguration().toString()));


        selectedVariantCol.setCellValueFactory(new PropertyValueFactory<>("selected"));
        selectedVariantCol.setCellFactory(CheckBoxTableCell.forTableColumn(selectedVariantCol));
        selectedVariantCol.setEditable(true);

        //variantsTable.setItems(this.variantsData);
        SortedList<VariantsView.VariantsInfo> sortedData = new SortedList<>(filteredData);
        sortedData.comparatorProperty().bind(variantsTable.comparatorProperty());
        variantsTable.setItems(sortedData);

        // commit details view
        VariantDetailView variantDetailView = new VariantDetailView(service);


        variantsTable.getSelectionModel().selectedItemProperty().addListener((observableValue, oldValue, newValue) -> {
            if (newValue != null) {
                variantDetailView.showVariant(newValue.getVariant());
                VariantsView.this.variantsDataSelected.clear();
                Collection<? extends Variant> variants = VariantsView.this.service.getRepository().getVariants();
                for (Variant variant : variants) {
                    VariantsView.this.variantsDataSelected.add(new VariantsView.VariantsInfo(variant));
                }
            } else {
                variantDetailView.showVariant(null);
            }
        });


        // add to split pane
        splitPane.getItems().addAll(variantsTable, variantDetailView);

        Platform.runLater(() -> statusChangedEvent(service));

        service.addListener(this);
    }


    @Override
    public void statusChangedEvent(EccoService service) {
        if (service.isInitialized()) {
            Platform.runLater(() -> this.setDisable(false));
        } else {
            Platform.runLater(() -> this.setDisable(true));
        }
    }

    private void openDialog(String title, Parent content) {
        final Stage dialog = new Stage();
        dialog.initStyle(StageStyle.UTILITY);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.initOwner(VariantsView.this.getScene().getWindow());

        Scene dialogScene = new Scene(content);
        dialog.setScene(dialogScene);
        dialog.setTitle(title);

//		dialog.setMinWidth(400);
//		dialog.setMinHeight(200);

        dialog.show();
        dialog.requestFocus();
    }

    public class VariantsInfo {

        private Variant variant;

        private BooleanProperty selected;


        public VariantsInfo(Variant variant) {
            this.variant = variant;
            this.selected = new SimpleBooleanProperty(false);
        }

        public Variant getVariant() {
            return this.variant;
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

    }


}
