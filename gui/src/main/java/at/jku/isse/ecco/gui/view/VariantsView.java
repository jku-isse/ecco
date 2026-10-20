package at.jku.isse.ecco.gui.view;

import at.jku.isse.ecco.gui.ConstraintWarnings;
import at.jku.isse.ecco.gui.ExceptionAlert;
import at.jku.isse.ecco.gui.RepositoryAccess;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.core.Variant;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.gui.TableColumns;
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
import java.util.List;
import java.util.function.Consumer;

public class VariantsView extends BorderPane implements EccoListener {

    private EccoService service;

    final ObservableList<VariantsInfo> variantsDataSelected = FXCollections.observableArrayList();

    private ToolBar toolBar;

    /**
     * Whether a toolbar action's write runs (see {@link #runWrite}) - a refresh it fires must not
     * enable the toolbar before it ends. FX thread.
     */
    private boolean writing;


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
        Button removeButton = new Button("Remove Selected Variant");
        toolBar.getItems().add(removeButton);
        removeButton.setOnAction(event -> {
            List<Variant> selectedVariants = this.selectedVariants();
            if (selectedVariants.isEmpty())
                return;
            // a removal that throws is shown and does not leave the toolbar disabled for good
            this.runWrite("Remove variants", () -> {
                synchronized (this.service) {
                    for (Variant variant : selectedVariants)
                        this.service.removeVariant(variant.getConfiguration());
                }
            }, () -> {
            });
        });


        toolBar.getItems().add(new Separator());

        Label baseDirLabel = new Label("Checkout Folder: ");
        // default to the directory the repository lives in, not whatever service.getBaseDir()
        // happens to still be set to from a previous, possibly unrelated operation -- same rationale
        // as CheckoutView's base-directory field -- still freely editable below before checkout.
        TextField baseDirTextField = new TextField(service.getRepositoryHomeDir().toString());
        baseDirTextField.setDisable(false);
        baseDirLabel.setLabelFor(baseDirTextField);
        Button selectBaseDirectoryButton = new Button("Browse...");
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
        checkoutSelectedButton.setOnAction(event -> {
            List<Variant> selectedVariants = this.selectedVariants();
            if (selectedVariants.isEmpty())
                return;
            String base = baseDirTextField.getText();

            // use composition here to merge selected associations
            // one folder per variant, named after it - or after its id where the name is empty or
            // shared with another selected variant (every committed variant is named "Commit"):
            // a second checkout into the same folder fails - e.g. a folder that already holds a
            // checkout, shown, not left to disable the toolbar for good
            this.runWrite("Checkout variants", () -> {
                // holding the lock throughout: the variants are read, and the base directory stays
                // this checkout's until its last variant is written - a commit reads it
                synchronized (this.service) {
                    for (Variant variant : selectedVariants) {
                        String name = variant.getName();
                        String varname = name;
                        if (name.equals("") || selectedVariants.stream().filter(other -> name.equals(other.getName())).count() > 1)
                            varname = variant.getId();
                        Path baseDir = Paths.get(base + File.separator + varname);
                        File checkoutfile = new File(String.valueOf(baseDir));
                        if (!checkoutfile.exists())
                            checkoutfile.mkdir();
                        this.service.setBaseDir(baseDir);
                        this.service.checkout(variant.getConfiguration());
                    }
                }
            }, () -> {
            });
        });

        TextField searchField = new TextField();
        Button searchButton = new Button("Search Feature Revision");
        toolBar.getItems().addAll(searchField, searchButton);
        searchButton.setOnAction(event -> {
            toolBar.setDisable(true);
            this.variantsDataSelected.clear();
            String revision = searchField.getText();

            // the variants and their configurations are read holding the lock, in the background
            RepositoryAccess.read(this.service, "Search feature revision", service -> {
                List<VariantsInfo> found = new ArrayList<>();
                for (Variant variant : service.getRepository().getVariants()) {
                    for (FeatureRevision f : variant.getConfiguration().getFeatureRevisions()) {
                        if (f.getFeatureRevisionString().equals(revision))
                            found.add(new VariantsInfo(variant));
                    }
                }
                return found;
            }, found -> {
                for (VariantsInfo info : found) {
                    this.variantsDataSelected.add(info);
                    this.refreshDerivedInfo(info);
                }
                toolBar.setDisable(this.writing);
            }, failure -> {
                toolBar.setDisable(this.writing);
                new ExceptionAlert(failure).show();
            });
        });


        Button addSelectedButton = new Button("Add Feature Revision");
        toolBar.getItems().addAll(addSelectedButton);
        addSelectedButton.setOnAction(event -> {
            String revision = searchField.getText();
            this.runFeatureRevisionAction("Add feature revision", variant -> {
                // full ids (the displayed ones are cut to 7 characters), no stray comma for an empty variant
                String configuration = at.jku.isse.ecco.feature.ConfigurationText.with(at.jku.isse.ecco.feature.ConfigurationText.print(
                        java.util.Arrays.asList(variant.getConfiguration().getFeatureRevisions()), true), revision);
                Configuration config = this.service.parseConfigurationString(configuration);
                this.service.updateVariant(config, variant.getName(), variant.getId());
            });
        });


        Button removeSelectedButton = new Button("Remove Feature Revision");
        toolBar.getItems().addAll(removeSelectedButton);
        removeSelectedButton.setOnAction(event -> {
            String revision = searchField.getText();
            this.runFeatureRevisionAction("Remove feature revision", variant -> {
                for (FeatureRevision f : variant.getConfiguration().getFeatureRevisions()) {
                    if (f.getFeatureRevisionString().equals(revision)) {
                        this.service.removeFeatureRevision(f, variant.getId());
                    }
                }
            });
        });


        TextField updateField = new TextField();
        toolBar.getItems().addAll(updateField);

        Button updateSelectedButton = new Button("Update Feature Revision");
        toolBar.getItems().addAll(updateSelectedButton);
        updateSelectedButton.setOnAction(event -> {
            String revision = searchField.getText();
            String update = updateField.getText();
            this.runFeatureRevisionAction("Update feature revision", variant -> {
                for (FeatureRevision f : variant.getConfiguration().getFeatureRevisions()) {
                    if (f.getFeatureRevisionString().equals(revision)) {
                        this.service.updateFeatureRevision(f, update, variant.getId());
                    }
                }
            });
        });
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

        TableColumn<VariantsInfo, String> idCol = new TableColumn<>("Id");
        TableColumn<VariantsInfo, String> nameCol = new TableColumn<>("Name");
        TableColumn<VariantsInfo, String> configCol = new TableColumn<>("Configuration");
        TableColumn<VariantsInfo, String> warningCol = new TableColumn<>("Constraint Warning");
        TableColumn<VariantsInfo, String> matchingCommitsCol = new TableColumn<>("Matching Commits");
        TableColumn<VariantsInfo, String> variantsCol = new TableColumn<>("Variants");
        TableColumn<VariantsView.VariantsInfo, Boolean> selectedVariantCol = new TableColumn<>("Selected");

        variantsCol.getColumns().addAll(idCol, nameCol, configCol, warningCol, matchingCommitsCol, selectedVariantCol);
        variantsTable.getColumns().setAll(variantsCol);


        idCol.setCellValueFactory((TableColumn.CellDataFeatures<VariantsInfo, String> param) -> new ReadOnlyStringWrapper(param.getValue().getId()));
        nameCol.setCellValueFactory((TableColumn.CellDataFeatures<VariantsInfo, String> param) -> new ReadOnlyStringWrapper(param.getValue().getName()));
        configCol.setCellValueFactory((TableColumn.CellDataFeatures<VariantsInfo, String> param) -> new ReadOnlyStringWrapper(param.getValue().getConfigurationText()));
        configCol.setCellFactory(TableColumns.wrappingCellFactory());

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
                    setStyle("-fx-text-fill: firebrick; -fx-wrap-text: true;");
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

        TableColumns.shortIds(idCol, sortedData);
        TableColumns.shortIds(matchingCommitsCol, sortedData);
        TableColumns.fitToContent(nameCol, sortedData);
        TableColumns.fitToContent(warningCol, sortedData);
        TableColumns.controlWidth(selectedVariantCol);
        TableColumns.growToFill(variantsTable, configCol);

        return variantsTable;
    }

    /** The variants whose "Selected" box is ticked. */
    private List<Variant> selectedVariants() {
        List<Variant> selectedVariants = new ArrayList<>();
        for (VariantsInfo variantInfo : this.variantsDataSelected) {
            if (variantInfo.isSelected())
                selectedVariants.add(variantInfo.getVariant());
        }
        return selectedVariants;
    }

    /**
     * Runs {@code write} - service calls that change the repository - in the background, not on the
     * FX thread (NFR-C3): a commit holds the service's lock for its whole run, and the window froze
     * waiting for it. The toolbar is disabled meanwhile; a failure is shown. The status event a
     * write fires refreshes the list.
     */
    private void runWrite(String name, Runnable write, Runnable done) {
        this.writing = true;
        this.toolBar.setDisable(true);
        RepositoryAccess.write(name, write, () -> {
            this.writing = false;
            this.toolBar.setDisable(false);
            done.run();
        }, failure -> {
            this.writing = false;
            this.toolBar.setDisable(false);
            new ExceptionAlert(failure).show();
        });
    }

    /**
     * Runs {@code perSelectedVariant} once for every currently-selected variant, in the background
     * holding the service's lock (it reads the variant's configuration, then changes it), then
     * reloads the full variant list -- the shape shared by the Add/Remove/Update Feature Revision
     * toolbar actions (built in {@link #buildToolBarActions()}). They ran on the FX thread.
     */
    private void runFeatureRevisionAction(String name, Consumer<Variant> perSelectedVariant) {
        List<Variant> selectedVariants = this.selectedVariants();
        this.runWrite(name, () -> {
            synchronized (this.service) {
                for (Variant variant : selectedVariants)
                    perSelectedVariant.accept(variant);
            }
        }, this::refresh);
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
        // The variants are read holding the service's lock (a commit adds one meanwhile), and what
        // the table shows of them is read along - see VariantsInfo.
        // A failure is surfaced rather than silently swallowed. A repository closed while the
        // refresh was under way (a checkout or commit fires it, and Close can follow at once) is no
        // error: the view is being disabled anyway. This showed a modal "Service is not initialized"
        // dialog.
        RepositoryAccess.read(this.service, "Variants refresh", service -> {
            List<VariantsInfo> infos = new ArrayList<>();
            for (Variant variant : service.getRepository().getVariants())
                infos.add(new VariantsInfo(variant));
            return infos;
        }, infos -> {
            this.variantsDataSelected.setAll(infos);
            this.toolBar.setDisable(this.writing);
            this.refreshDerivedInfoBatch(infos);
        }, failure -> {
            this.toolBar.setDisable(this.writing);
            if (this.service.isInitialized())
                new ExceptionAlert(failure).show();
        });
    }

    /**
     * Batched version of {@link #refreshDerivedInfo(VariantsInfo)} for {@link #refresh()} (which runs
     * after every add/remove/update-variant/commit/accept-constraint event, potentially for MANY
     * variants at once) -- computes every entry in ONE background read instead of spawning one
     * thread per variant. The read holds the service's lock (NFR-C1b): the constraints and commits it
     * reads are changed by a commit meanwhile. Skipped if a write is already running when the batch
     * starts - the event that write fires refreshes again.
     */
    private void refreshDerivedInfoBatch(List<VariantsInfo> infos) {
        if (!this.service.isInitialized() || this.service.isWriteInProgress() || infos.isEmpty()) return;
        RepositoryAccess.read(this.service, "Variant warnings", service -> {
            List<String[]> derived = new ArrayList<>();
            for (VariantsInfo info : infos)
                derived.add(this.derivedInfo(info));
            return derived;
        }, derived -> {
            for (int i = 0; i < infos.size(); i++) {
                infos.get(i).setWarning(derived.get(i)[0]);
                infos.get(i).setMatchingCommits(derived.get(i)[1]);
            }
        }, failure -> new ExceptionAlert(failure).show());
    }

    /**
     * Live constraint-violation feedback (see {@code EccoService#checkConstraintViolations}) and
     * matching-commit lookup for one variant, read in the background holding the service's lock and
     * written back into {@code info}'s properties on the FX thread -- the "Constraint
     * Warning"/"Matching Commits" columns render them in place. Used by the search action;
     * {@link #refresh()} uses the batched {@link #refreshDerivedInfoBatch} instead.
     */
    private void refreshDerivedInfo(VariantsInfo info) {
        if (!this.service.isInitialized() || this.service.isWriteInProgress()) {
            info.setWarning("");
            info.setMatchingCommits("");
            return;
        }
        RepositoryAccess.read(this.service, "Variant warning", service -> this.derivedInfo(info), derived -> {
            info.setWarning(derived[0]);
            info.setMatchingCommits(derived[1]);
        }, failure -> new ExceptionAlert(failure).show());
    }

    /** {warning, matching commits} of {@code info}'s variant; holding the service's lock. */
    private String[] derivedInfo(VariantsInfo info) {
        Configuration configuration = info.getVariant().getConfiguration();
        return new String[]{ConstraintWarnings.describe(this.service, configuration), describeMatchingCommits(configuration)};
    }

    /**
     * Matches this variant's configuration against every commit's configuration by {@code equals()}
     * (there's no stored id-to-id or object-to-object cross reference between {@code Commit} and
     * {@code Variant} in the data model -- both entities get independent random UUIDs, and a Variant
     * is deduplicated across commits sharing an identical configuration -- so this is the only
     * available correlation). Comma-joined commit ids; empty if none match. Holding the service's
     * lock: the commits are read.
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
        // the main window's way: with Cmd+W, Cmd+Q and Esc, which these lacked
        at.jku.isse.ecco.gui.Dialogs.open(this.getScene().getWindow(), title, content, Modality.WINDOW_MODAL);
    }


    public class VariantsInfo {

        private Variant variant;

        /** What the table shows of the variant, read with it - the cells must not read the live variant on the FX thread. */
        private final String id;
        private final String name;
        private final String configurationText;

        private BooleanProperty selected;

        private final javafx.beans.property.SimpleStringProperty warning = new javafx.beans.property.SimpleStringProperty("");
        private final javafx.beans.property.SimpleStringProperty matchingCommits = new javafx.beans.property.SimpleStringProperty("");


        /** Holding the service's lock: it reads the variant. */
        public VariantsInfo(Variant variant) {
            this.variant = variant;
            this.id = variant.getId();
            this.name = variant.getName();
            this.configurationText = variant.getConfiguration().toString();
            this.selected = new SimpleBooleanProperty(false);
        }

        public Variant getVariant() {
            return this.variant;
        }

        public String getId() {
            return this.id;
        }

        public String getName() {
            return this.name;
        }

        public String getConfigurationText() {
            return this.configurationText;
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
