package at.jku.isse.ecco.gui.view;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.core.Commit;
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
import java.util.List;
import java.util.function.Consumer;

public class VariantsView extends BorderPane implements EccoListener {

    private EccoService service;

    final ObservableList<VariantsInfo> variantsDataSelected = FXCollections.observableArrayList();

    private ToolBar toolBar;


    public VariantsView(EccoService service) {
        super();
        this.service = service;

        this.toolBar = new ToolBar();
        this.setTop(this.toolBar);

        SplitPane splitPane = new SplitPane();
        this.setCenter(splitPane);

        this.buildToolBarActions();

        TableView<VariantsInfo> variantsTable = this.buildVariantsTable();

        // commit details view
        VariantDetailView variantDetailView = new VariantDetailView(service);

        // just show the detail view for the newly-selected row -- reloading the ENTIRE variants
        // list here (as this used to do) raced with refresh()'s own background reload (see
        // refresh()'s comment) and was the direct cause of duplicated/missing rows after a
        // commit/checkout/save-as-variant, since clearing the list mid-selection-change can itself
        // trigger another selection-changed event.
        variantsTable.getSelectionModel().selectedItemProperty().addListener((observableValue, oldValue, newValue) -> {
            if (newValue != null) {
                variantDetailView.showVariant(newValue.getVariant());
            } else {
                variantDetailView.showVariant(null);
            }
        });

        // add to split pane
        splitPane.getItems().addAll(variantsTable, variantDetailView);

        Platform.runLater(() -> statusChangedEvent(service));

        service.addListener(this);
    }

    /**
     * Builds every toolbar button/field and wires its action handler: select all/unselect all, add/
     * remove variant, checkout, and search/add/remove/update feature revision. Split out of the
     * constructor purely for readability -- no behavior change from the previous single-constructor
     * version.
     */
    private void buildToolBarActions() {
        ToolBar toolBar = this.toolBar;

        // no manual "Refresh" button -- statusChangedEvent() (triggered by every add/remove/update
        // variant and by commit/accept-constraint actions) auto-refreshes this table instead.
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

        Label baseDirLabel = new Label("Base Directory: ");
        // default to the directory the repository lives in, not whatever service.getBaseDir()
        // happens to still be set to from a previous, possibly unrelated operation -- same rationale
        // as CheckoutView's base-directory field -- still freely editable below before checkout.
        TextField baseDirTextField = new TextField(service.getRepositoryHomeDir().toString());
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
            } catch (Exception ignored) {
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
                                        VariantsInfo info = new VariantsInfo(variant);
                                        VariantsView.this.variantsDataSelected.add(info);
                                        VariantsView.this.refreshDerivedInfo(info);
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
        addSelectedButton.setOnAction(event -> this.runFeatureRevisionAction(variantInfo -> {
            String configuration = variantInfo.getVariant().getConfiguration().toString() + "," + searchField.getText();
            String name = variantInfo.getVariant().getName();
            String id = variantInfo.getVariant().getId();
            Configuration config = VariantsView.this.service.parseConfigurationString(configuration);
            VariantsView.this.service.updateVariant(config, name, id);
        }));


        Button removeSelectedButton = new Button("Remove Feature Revision");
        toolBar.getItems().addAll(removeSelectedButton);
        removeSelectedButton.setOnAction(event -> this.runFeatureRevisionAction(variantInfo -> {
            for (FeatureRevision f : variantInfo.getVariant().getConfiguration().getFeatureRevisions()) {
                if (f.getFeatureRevisionString().equals(searchField.getText())) {
                    VariantsView.this.service.removeFeatureRevision(f, variantInfo.getVariant().getId());
                }
            }
        }));


        TextField updateField = new TextField();
        toolBar.getItems().addAll(updateField);

        Button updateSelectedButton = new Button("Update Feature Revision");
        toolBar.getItems().addAll(updateSelectedButton);
        updateSelectedButton.setOnAction(event -> this.runFeatureRevisionAction(variantInfo -> {
            for (FeatureRevision f : variantInfo.getVariant().getConfiguration().getFeatureRevisions()) {
                if (f.getFeatureRevisionString().equals(searchField.getText())) {
                    VariantsView.this.service.updateFeatureRevision(f, updateField.getText(), variantInfo.getVariant().getId());
                }
            }
        }));
    }

    /**
     * Builds the variants table: columns, cell factories, and sorting. Split out of the constructor
     * purely for readability -- no behavior change from the previous single-constructor version.
     */
    private TableView<VariantsInfo> buildVariantsTable() {
        FilteredList<VariantsView.VariantsInfo> filteredData = new FilteredList<>(this.variantsDataSelected, p -> true);

        // list of variants
        TableView<VariantsInfo> variantsTable = new TableView<>();
        variantsTable.setEditable(true);
        variantsTable.setTableMenuButtonVisible(true);
        variantsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<VariantsInfo, String> idCol = new TableColumn<>("Id");
        TableColumn<VariantsInfo, String> nameCol = new TableColumn<>("Name");
        TableColumn<VariantsInfo, String> configCol = new TableColumn<>("Configuration");
        TableColumn<VariantsInfo, String> warningCol = new TableColumn<>("Constraint Warning");
        TableColumn<VariantsInfo, String> matchingCommitsCol = new TableColumn<>("Matching Commits");
        TableColumn<VariantsInfo, String> variantsCol = new TableColumn<>("Variants");
        TableColumn<VariantsView.VariantsInfo, Boolean> selectedVariantCol = new TableColumn<>("Selected");

        variantsCol.getColumns().addAll(idCol, nameCol, configCol, warningCol, matchingCommitsCol, selectedVariantCol);
        variantsTable.getColumns().setAll(variantsCol);


        idCol.setCellValueFactory((TableColumn.CellDataFeatures<VariantsInfo, String> param) -> new ReadOnlyStringWrapper(param.getValue().getVariant().getId()));
        nameCol.setCellValueFactory((TableColumn.CellDataFeatures<VariantsInfo, String> param) -> new ReadOnlyStringWrapper(param.getValue().getVariant().getName()));
        configCol.setCellValueFactory((TableColumn.CellDataFeatures<VariantsInfo, String> param) -> new ReadOnlyStringWrapper(param.getValue().getVariant().getConfiguration().toString()));

        // live constraint-violation feedback -- see EccoService.checkConstraintViolations; kept
        // current by refreshDerivedInfo(), called whenever the list is (re)populated.
        warningCol.setCellValueFactory(param -> param.getValue().warningProperty());
        warningCol.setCellFactory(col -> new TableCell<VariantsInfo, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isEmpty()) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    setStyle("-fx-text-fill: firebrick;");
                }
            }
        });

        // there's no stored link between a Commit and a Variant in the data model (independent
        // random UUIDs, and a Variant is deduplicated across commits sharing a configuration) --
        // this matches by Configuration.equals() at display time instead (see describeMatchingCommits).
        matchingCommitsCol.setCellValueFactory(param -> param.getValue().matchingCommitsProperty());


        selectedVariantCol.setCellValueFactory(new PropertyValueFactory<>("selected"));
        selectedVariantCol.setCellFactory(CheckBoxTableCell.forTableColumn(selectedVariantCol));
        selectedVariantCol.setEditable(true);

        //variantsTable.setItems(this.variantsData);
        SortedList<VariantsView.VariantsInfo> sortedData = new SortedList<>(filteredData);
        sortedData.comparatorProperty().bind(variantsTable.comparatorProperty());
        variantsTable.setItems(sortedData);

        return variantsTable;
    }

    /**
     * Runs {@code perSelectedVariant} once for every currently-selected variant, then reloads the
     * full variant list and recomputes derived info -- the shape shared by the Add/Remove/Update
     * Feature Revision toolbar actions (built in {@link #buildToolBarActions()}), which previously
     * each duplicated this choreography with only the per-variant action differing. Snapshots the
     * selection before clearing it, and clears/reloads {@link #variantsDataSelected} together inside
     * the same {@code Platform.runLater} block -- not clear-now/add-later -- for the same reason
     * {@link #refresh()} does: interleaving with other code touching {@link #variantsDataSelected}
     * (e.g. the table's own selection-changed listener) can otherwise produce duplicate/missing rows.
     */
    private void runFeatureRevisionAction(Consumer<VariantsInfo> perSelectedVariant) {
        this.toolBar.setDisable(true);
        ObservableList<VariantsInfo> variantsDataSelectedAux = FXCollections.observableArrayList();
        variantsDataSelectedAux.addAll(this.variantsDataSelected);
        this.variantsDataSelected.clear();

        Task<Void> task = new Task<Void>() {
            @Override
            public Void call() throws EccoException {
                Platform.runLater(() -> {
                    for (VariantsInfo variantInfo : variantsDataSelectedAux) {
                        if (variantInfo.isSelected()) {
                            perSelectedVariant.accept(variantInfo);
                        }
                    }
                    Collection<? extends Variant> variants = VariantsView.this.service.getRepository().getVariants();
                    for (Variant variant : variants) {
                        VariantsInfo info = new VariantsInfo(variant);
                        VariantsView.this.variantsDataSelected.add(info);
                        VariantsView.this.refreshDerivedInfo(info);
                    }
                });
                Platform.runLater(() -> VariantsView.this.toolBar.setDisable(false));
                return null;
            }
        };

        new Thread(task).start();
    }


    @Override
    public void statusChangedEvent(EccoService service) {
        if (service.isInitialized()) {
            // fireStatusChangedEvent() can be invoked from a background thread (e.g. commit()'s
            // Task, or CheckoutDetailView's "Save as Variant" thread) -- refresh() touches JavaFX
            // UI directly, so both it and setDisable must run on the FX thread, not whatever thread
            // fired the event.
            Platform.runLater(() -> {
                this.setDisable(false);
                this.refresh();
            });
        } else {
            Platform.runLater(() -> {
                this.setDisable(true);
                this.variantsDataSelected.clear();
            });
        }
    }

    /**
     * Reloads the known-variants list from the repository -- called by {@link #statusChangedEvent}
     * whenever the repository changes (add/remove/update variant, commit, accept/reject constraint),
     * so the table stays current without a manual refresh action. Also (re)computes each variant's
     * live constraint-violation status and matching-commit ids.
     */
    private void refresh() {
        this.toolBar.setDisable(true);

        // clear() and the add-loop must happen TOGETHER, once the background load completes -- not
        // clear-now/add-later, which left a window where other code touching variantsDataSelected
        // (e.g. the table's own selection-changed listener) could interleave and produce duplicate
        // rows, or the freshly-added variant getting raced away entirely.
        Task<ArrayList<Variant>> variantsRefreshTask = new Task<ArrayList<Variant>>() {
            @Override
            public ArrayList<Variant> call() throws EccoException {
                return new ArrayList<>(VariantsView.this.service.getRepository().getVariants());
            }
        };
        variantsRefreshTask.setOnSucceeded(event -> {
            VariantsView.this.variantsDataSelected.clear();
            List<VariantsInfo> infos = new ArrayList<>();
            for (Variant variant : variantsRefreshTask.getValue()) {
                VariantsInfo info = new VariantsInfo(variant);
                VariantsView.this.variantsDataSelected.add(info);
                infos.add(info);
            }
            VariantsView.this.toolBar.setDisable(false);
            VariantsView.this.refreshDerivedInfoBatch(infos);
        });
        // surfaced rather than silently swallowed -- e.g. getRepository() can throw if the
        // repository is uninitialized/mid-transaction, and this Task previously had no failure
        // handler at all, so such a failure looked identical to "nothing to refresh".
        variantsRefreshTask.setOnFailed(event -> {
            VariantsView.this.toolBar.setDisable(false);
            new at.jku.isse.ecco.gui.ExceptionAlert(variantsRefreshTask.getException()).showAndWait();
        });

        new Thread(variantsRefreshTask).start();
    }

    /**
     * Batched version of {@link #refreshDerivedInfo(VariantsInfo)} for {@link #refresh()} (which runs
     * after every add/remove/update-variant/commit/accept-constraint event, potentially for MANY
     * variants at once) -- computes every entry SEQUENTIALLY on a single background thread instead of
     * spawning one thread per variant. There's a real, pre-existing, unsynchronized-access gap between
     * this kind of background repository read and other code (e.g. {@code ArtifactsView}) that holds
     * live {@code Association} references and reads them directly on the FX thread while a write
     * (e.g. {@code commit()}, on its own background thread) is in progress -- see
     * {@code EccoService#isWriteInProgress}. This doesn't close that gap, but avoids piling on
     * unnecessary additional concurrent read traffic while a write is in flight, and skips entirely if
     * one is already running when the batch starts.
     */
    private void refreshDerivedInfoBatch(List<VariantsInfo> infos) {
        if (!this.service.isInitialized() || this.service.isWriteInProgress() || infos.isEmpty()) return;
        new Thread(() -> {
            for (VariantsInfo info : infos) {
                if (this.service.isWriteInProgress()) break;
                String warning = describeConstraintViolations(info.getVariant().getConfiguration());
                String matchingCommits = describeMatchingCommits(info.getVariant().getConfiguration());
                Platform.runLater(() -> {
                    info.setWarning(warning);
                    info.setMatchingCommits(matchingCommits);
                });
            }
        }).start();
    }

    /**
     * Live constraint-violation feedback (see {@code EccoService#checkConstraintViolations}) and
     * matching-commit lookup for one variant, computed off the FX thread and written back into
     * {@code info}'s properties -- the "Constraint Warning"/"Matching Commits" columns render them
     * in place. Used by the individual-selection/search/feature-revision actions; {@link #refresh()}
     * uses the batched {@link #refreshDerivedInfoBatch} instead.
     */
    private void refreshDerivedInfo(VariantsInfo info) {
        if (!this.service.isInitialized() || this.service.isWriteInProgress()) {
            info.setWarning("");
            info.setMatchingCommits("");
            return;
        }
        new Thread(() -> {
            String warning = describeConstraintViolations(info.getVariant().getConfiguration());
            String matchingCommits = describeMatchingCommits(info.getVariant().getConfiguration());
            Platform.runLater(() -> {
                info.setWarning(warning);
                info.setMatchingCommits(matchingCommits);
            });
        }).start();
    }

    /** Empty string if no violations. */
    private String describeConstraintViolations(Configuration configuration) {
        try {
            java.util.List<String> violations = this.service.checkConstraintViolations(configuration);
            return violations.isEmpty() ? "" : "Violates accepted constraint(s): " + String.join("; ", violations);
        } catch (RuntimeException e) {
            return "";
        }
    }

    /**
     * Matches this variant's configuration against every commit's configuration by {@code equals()}
     * (there's no stored id-to-id or object-to-object cross reference between {@code Commit} and
     * {@code Variant} in the data model -- both entities get independent random UUIDs, and a Variant
     * is deduplicated across commits sharing an identical configuration -- so this is the only
     * available correlation). Comma-joined commit ids; empty if none match.
     */
    private String describeMatchingCommits(Configuration configuration) {
        try {
            java.util.List<String> ids = new java.util.ArrayList<>();
            for (Commit commit : this.service.getCommits()) {
                if (commit.getConfiguration() != null && commit.getConfiguration().equals(configuration)) {
                    ids.add(commit.getId());
                }
            }
            return String.join(", ", ids);
        } catch (RuntimeException e) {
            return "";
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

        private final javafx.beans.property.SimpleStringProperty warning = new javafx.beans.property.SimpleStringProperty("");
        private final javafx.beans.property.SimpleStringProperty matchingCommits = new javafx.beans.property.SimpleStringProperty("");


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

        public String getWarning() {
            return this.warning.get();
        }

        public void setWarning(String warning) {
            this.warning.set(warning == null ? "" : warning);
        }

        public javafx.beans.property.SimpleStringProperty warningProperty() {
            return this.warning;
        }

        public String getMatchingCommits() {
            return this.matchingCommits.get();
        }

        public void setMatchingCommits(String matchingCommits) {
            this.matchingCommits.set(matchingCommits == null ? "" : matchingCommits);
        }

        public javafx.beans.property.SimpleStringProperty matchingCommitsProperty() {
            return this.matchingCommits;
        }

    }


}
