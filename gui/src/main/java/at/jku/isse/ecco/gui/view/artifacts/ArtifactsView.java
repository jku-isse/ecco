package at.jku.isse.ecco.gui.view.artifacts;

import at.jku.isse.ecco.gui.TaskFailures;
import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.composition.LazyCompositionRootNode;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.gui.CategoricalColorPalette;
import at.jku.isse.ecco.gui.ExceptionAlert;
import at.jku.isse.ecco.gui.MinimizationResults;
import at.jku.isse.ecco.gui.TableColumns;
import at.jku.isse.ecco.gui.io.ConfigurationPickerDialog;
import at.jku.isse.ecco.gui.io.DeleteDirectoryContentsDialog;
import at.jku.isse.ecco.gui.io.Directory;
import at.jku.isse.ecco.gui.io.FeatureTogglePanel;
import at.jku.isse.ecco.gui.view.detail.AssociationDetailView;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.service.listener.EccoListener;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.util.Callback;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class ArtifactsView extends BorderPane implements EccoListener {

    private static final double BAR_WIDTH = 60;
    private static final double BAR_HEIGHT = 10;

    private final EccoService service;
    private final MinimizationResults minimizationResults;

    // reports a row's tick too (an "update" change), so ticking composes (see scheduleCompose())
    private final ObservableList<AssociationInfoImpl> associationsData =
            FXCollections.observableArrayList(info -> new javafx.beans.Observable[]{info.selectedProperty()});
    private int maxNumArtifacts = 1;

    private final ToolBar toolBar;
    private final ArtifactTreeView artifactTreeView;

    private FeatureTogglePanel featureTogglePanel;
    private final AtomicLong configurationGeneration = new AtomicLong();
    private final AtomicLong composeGeneration = new AtomicLong();
    // composes once the ticks have settled - Select All ticks every row, one by one
    private final javafx.animation.PauseTransition composeDelay = new javafx.animation.PauseTransition(javafx.util.Duration.millis(250));
    private final ToggleButton featuresButton = new ToggleButton("Features");
    private final AtomicLong refreshGeneration = new AtomicLong();


    public ArtifactsView(final EccoService service, MinimizationResults minimizationResults) {
        this.service = service;
        this.minimizationResults = minimizationResults;

        // the shared minimization run (see MinimizationResults): its button is here, with the results.
        // Live: as the shared run (or an undo of one) changes an entry, push it into whichever
        // currently-displayed row has that association id, without needing a table refresh.
        minimizationResults.getMinimizedByAssociationId().addListener((MapChangeListener<String, String>) change -> {
            for (AssociationInfoImpl info : ArtifactsView.this.associationsData) {
                if (info.getAssociation().getId().equals(change.getKey())) {
                    info.setMinimizedCondition(change.wasAdded() ? change.getValueAdded() : null);
                    break;
                }
            }
        });


        // toolbar
        toolBar = new ToolBar();
        this.setTop(toolBar);

        ProgressBar minimizeProgressBar = new ProgressBar(0);
        minimizeProgressBar.setMaxWidth(Double.MAX_VALUE);
        minimizeProgressBar.progressProperty().bind(minimizationResults.progressProperty());
        minimizeProgressBar.visibleProperty().bind(minimizationResults.runningProperty());
        this.setBottom(minimizeProgressBar);

        // selection
        // buttons: a "Selection" menu held just these two
        Button selectAllButton = new Button("Select All");
        selectAllButton.setTooltip(new Tooltip("Tick every association"));
        Button unselectAllButton = new Button("Unselect All");
        unselectAllButton.setTooltip(new Tooltip("Untick every association"));

        Button checkoutSelectedButton = new Button("Checkout Selected...");
        checkoutSelectedButton.setTooltip(new Tooltip("Checks out exactly the ticked associations, into a folder you choose"));
        featuresButton.setTooltip(new Tooltip("Tick features: the associations a checkout of them takes are ticked"));

        CheckBox useSimplifiedLabelsCheckBox = new CheckBox("Show Minimized Conditions");
        useSimplifiedLabelsCheckBox.setTooltip(new Tooltip("Where Minimize Presence Conditions has simplified a condition, show that instead of the full one"));

        // the tree follows the ticks - there is no Compose button any more; the associations of a
        // configuration are ticked from the Features pane (Selection > Select by Configuration did
        // the same in a dialog: the pane opens that dialog now, for exact revisions)
        toolBar.getItems().addAll(
                featuresButton, selectAllButton, unselectAllButton, checkoutSelectedButton, new Separator(),
                useSimplifiedLabelsCheckBox, minimizationResults.newRunButton());

        TableView<AssociationInfoImpl> associationsTable = this.buildAssociationsTable(useSimplifiedLabelsCheckBox, this.associationsData);


        artifactTreeView = new ArtifactTreeView(service);

        // the details of the selected row (its id, condition, modules and artifacts) - they had a
        // tab of their own, Associations, with the same table again; the ticks still pick the tree
        AssociationDetailView associationDetailView = new AssociationDetailView(service);
        Label noDetails = new Label("Select an association to see its condition, modules and artifacts.");
        noDetails.setWrapText(true);
        noDetails.getStyleClass().add("text-muted");
        StackPane details = new StackPane(noDetails, associationDetailView);
        associationsTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            associationDetailView.showAssociation(newValue == null ? null : newValue.getAssociation());
            noDetails.setVisible(newValue == null);
        });

        // split panes
        SplitPane horizontalSplitPane = new SplitPane();
        horizontalSplitPane.setOrientation(Orientation.VERTICAL);
        horizontalSplitPane.getItems().addAll(associationsTable, artifactTreeView);

        SplitPane detailsSplitPane = new SplitPane(horizontalSplitPane, details);
        this.setCenter(detailsSplitPane);


        this.wireToolBarActions(selectAllButton, unselectAllButton, checkoutSelectedButton);

        this.composeDelay.setOnFinished(e -> this.composeTicked());
        this.associationsData.addListener((javafx.collections.ListChangeListener<AssociationInfoImpl>) change -> this.composeDelay.playFromStart());
        this.featuresButton.selectedProperty().addListener((observable, wasShown, show) -> this.showFeatures(show));

        useSimplifiedLabelsCheckBox.setSelected(true);

        Platform.runLater(() -> {
            // the associations got a fifth of the height - one row at 1200x600
            horizontalSplitPane.setDividerPosition(0, 0.35);
            detailsSplitPane.setDividerPosition(0, 0.65);
            // loaded here too, not only after the next change of the repository: there was a
            // Refresh button for that
            if (!service.isInitialized())
                this.setDisable(true);
            else
                this.refresh();
        });

        // ecco service
        service.addListener(this);
    }

    /**
     * Builds the associations table: id/condition/artifact-count/selected/highlighted columns, plus
     * the local {@code ColorPickerTableCell} used for the "Highlighted" column's color-picker cells.
     * Split out of the constructor purely for readability -- no behavior change from the previous
     * single-constructor version.
     */
    private TableView<AssociationInfoImpl> buildAssociationsTable(CheckBox useSimplifiedLabelsCheckBox, ObservableList<AssociationInfoImpl> rows) {
        TableView<AssociationInfoImpl> associationsTable = new TableView<>();
        associationsTable.setPlaceholder(new Label("No associations yet: commit a variant first."));
        associationsTable.setEditable(true);
        associationsTable.setTableMenuButtonVisible(true);

        TableColumn<AssociationInfoImpl, String> idAssociationsCol = new TableColumn<>("Id");
        TableColumn<AssociationInfoImpl, String> conditionAssociationsCol = new TableColumn<>("Condition");
        TableColumn<AssociationInfoImpl, Integer> numArtifactsAssociationsCol = new TableColumn<>("Artifacts");

        TableColumn<AssociationInfoImpl, Boolean> selectedAssocationCol = new TableColumn<>("Selected");

        TableColumn<AssociationInfoImpl, Color> highlightedAssocationCol = new TableColumn<>("Colour");

        // the ticks first: they are what this table is for - picking what the tree and a checkout take
        associationsTable.getColumns().setAll(List.of(selectedAssocationCol, idAssociationsCol, conditionAssociationsCol, numArtifactsAssociationsCol, highlightedAssocationCol));

        idAssociationsCol.setCellValueFactory((TableColumn.CellDataFeatures<AssociationInfoImpl, String> param) -> new ReadOnlyStringWrapper(param.getValue().getAssociation().getId()));
        // "then" is a live property (see MinimizationResults/AssociationInfoImpl), not a one-shot
        // computed string, so this column updates on its own as a shared "Minimize Presence
        // Conditions" run fills it in -- unlike the old getSimpleModuleRevisionConditionString(),
        // which was a truncation to the lowest-order module(s), not a real minimization, and was
        // only ever computed once per row build.
        conditionAssociationsCol.setCellValueFactory((TableColumn.CellDataFeatures<AssociationInfoImpl, String> param) -> {
            // computeCondition() reads the live, shared Association/AssociationCounter object graph
            // with no synchronization, which a background commit() concurrently mutates -- a
            // pre-existing, known race (see EccoService#isWriteInProgress javadoc) that has actually
            // crashed the FX thread with an AIOOBE from a table layout pass racing a commit. This is
            // advisory-only, not a real fix (the flag can still flip false->true right after this
            // check), but it closes the common case; statusChangedEvent's refresh() re-renders every
            // row with real data as soon as the write actually finishes.
            String rawCondition = ArtifactsView.this.service.isWriteInProgress()
                    ? "…"
                    : param.getValue().getFullCondition();
            // simplified: the minimized condition where there is one - the full one until then; the
            // column was empty until someone minimized the conditions
            StringProperty minimized = param.getValue().minimizedConditionProperty();
            return Bindings.createStringBinding(() -> useSimplifiedLabelsCheckBox.isSelected() && minimized.get() != null && !minimized.get().isEmpty()
                    ? minimized.get() : rawCondition, useSimplifiedLabelsCheckBox.selectedProperty(), minimized);
        });
        // a condition is often longer than the column: all of it in the tooltip
        conditionAssociationsCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                setTooltip(empty || item == null || item.isEmpty() ? null : new Tooltip(item));
            }
        });
        numArtifactsAssociationsCol.setCellValueFactory((TableColumn.CellDataFeatures<AssociationInfoImpl, Integer> param) -> new ReadOnlyObjectWrapper<>(param.getValue().getNumArtifacts()));
        numArtifactsAssociationsCol.setCellFactory(col -> new TableCell<AssociationInfoImpl, Integer>() {
            private final Region track = new Region();
            private final Region fill = new Region();
            private final Label valueLabel = new Label();
            private final StackPane bar = new StackPane(track, fill);
            private final HBox content = new HBox(6, bar, valueLabel);

            {
                track.setPrefSize(BAR_WIDTH, BAR_HEIGHT);
                track.setMaxSize(BAR_WIDTH, BAR_HEIGHT);
                track.setStyle("-fx-background-color: #e1e0d9; -fx-background-radius: 2;");

                fill.setPrefHeight(BAR_HEIGHT);
                fill.setMaxHeight(BAR_HEIGHT);
                fill.setStyle("-fx-background-color: #2a78d6; -fx-background-radius: 2;");

                bar.setAlignment(Pos.CENTER_LEFT);
                content.setAlignment(Pos.CENTER_LEFT);
            }

            @Override
            protected void updateItem(Integer value, boolean empty) {
                super.updateItem(value, empty);

                if (empty || value == null) {
                    setGraphic(null);
                } else {
                    double ratio = ArtifactsView.this.maxNumArtifacts <= 0 ? 0 : Math.min(1.0, value / (double) ArtifactsView.this.maxNumArtifacts);
                    fill.setPrefWidth(BAR_WIDTH * ratio);
                    fill.setMaxWidth(BAR_WIDTH * ratio);
                    valueLabel.setText(String.valueOf(value));
                    setGraphic(content);
                }
            }
        });


        selectedAssocationCol.setCellValueFactory(new PropertyValueFactory<>("selected"));
        selectedAssocationCol.setCellFactory(CheckBoxTableCell.forTableColumn(selectedAssocationCol));
        selectedAssocationCol.setEditable(true);


        class ColorPickerTableCell<Inputs> extends TableCell<Inputs, Color> {
            private final ColorPicker cp;

            public ColorPickerTableCell(TableColumn<Inputs, Color> column) {
                this.getStyleClass().add("color-picker-table-cell");

                this.cp = new ColorPicker();
                // the swatch only, as a plain button: its colour's name ("#c0392b") was cut off at
                // the column's edge, and a combo box with an arrow on every row was heavy
                this.cp.getStyleClass().add(ColorPicker.STYLE_CLASS_BUTTON);
                this.cp.setStyle("-fx-color-label-visible: false;");

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
                    // an association never ticked has no colour - an empty swatch on every such row was noise
                    this.setGraphic(Color.TRANSPARENT.equals(item) ? null : this.cp);
                }
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


        SortedList<AssociationInfoImpl> sortedData = new SortedList<>(rows);
        sortedData.comparatorProperty().bind(associationsTable.comparatorProperty());

        associationsTable.setItems(sortedData);

        TableColumns.shortIds(idAssociationsCol, associationsTable.getItems());
        TableColumns.barWidth(numArtifactsAssociationsCol, BAR_WIDTH);
        TableColumns.controlWidth(selectedAssocationCol);
        TableColumns.controlWidth(highlightedAssocationCol);
        TableColumns.growToFill(associationsTable, conditionAssociationsCol);

        return associationsTable;
    }

    /**
     * Wires the Select All/Unselect All/Checkout Selected handlers. Split out of the constructor purely for
     * readability -- no behavior change from the previous single-constructor version; called at the
     * same point in construction as before (after {@link #artifactTreeView} and the split panes
     * exist, since several of these handlers reference them).
     */
    private void wireToolBarActions(Button selectAllButton, Button unselectAllButton, Button checkoutSelectedButton) {
        selectAllButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent e) {
                toolBar.setDisable(true);
                setAllAssociationsSelected(true);
                toolBar.setDisable(false);
            }
        });

        unselectAllButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent e) {
                toolBar.setDisable(true);
                setAllAssociationsSelected(false);
                toolBar.setDisable(false);
            }
        });

        checkoutSelectedButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent checkoutSelectedClickEvent) {
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

                    // into a folder the user chooses: it always went into the repository's own
                    // folder, whose files the "delete the contents?" question then offered to delete
                    Path target = chooseCheckoutDirectory();
                    if (target == null) {
                        toolBar.setDisable(false);
                        return;
                    }
                    // the working directory stays: it was set to the folder chosen, also when cancelled,
                    // and the refresh that set off cleared the ticks of the associations

                    // Cancel stops the checkout, as in the Checkout dialog - it used to be ignored,
                    // so the checkout was written over the directory's contents anyway
                    try {
                        if (!Directory.isEmpty(target)
                                && !confirmClearDirectory(target)) {
                            toolBar.setDisable(false);
                            return;
                        }
                    }catch (IOException e) {
                        ExceptionAlert alert = new ExceptionAlert(e);
                        alert.setTitle("Checkout Error");
                        alert.setHeaderText("Checkout Error");

                        alert.showAndWait();
                        toolBar.setDisable(false);
                        return;
                    }

                    Task<Void> checkoutTask = new CheckoutTask(service, rootNode, target);
                    new Thread(checkoutTask).start();
                }

                toolBar.setDisable(false);
            }
        });
    }

    /**
     * Asks whether to delete the contents of the non-empty {@code baseDir} before checking out into
     * it, and deletes them if so. Package-visible for testing.
     *
     * @return true if the contents were deleted, false to cancel the checkout.
     */
    boolean confirmClearDirectory(Path baseDir) throws IOException {
        return new DeleteDirectoryContentsDialog(baseDir).showBlocked();
    }

    /** The folder to check the ticked associations out into, or null if none was chosen. Package-visible for testing. */
    Path chooseCheckoutDirectory() {
        javafx.stage.DirectoryChooser chooser = new javafx.stage.DirectoryChooser();
        chooser.setTitle("Check out the ticked associations into");
        if (service.getRepositoryHomeDir() != null && java.nio.file.Files.isDirectory(service.getRepositoryHomeDir()))
            chooser.setInitialDirectory(service.getRepositoryHomeDir().toFile());
        java.io.File chosen = chooser.showDialog(this.getScene() == null ? null : this.getScene().getWindow());
        return chosen == null ? null : chosen.toPath();
    }

    /**
     * Ticks the associations a checkout of [configuration] takes (EccoService#getAssociations: the
     * whole compose pipeline, also its surplus suppression) - from the Features pane on every toggle,
     * and from its Exact Configuration dialog. The pane used the certain conditions instead, and the two
     * could tick different associations for the same configuration. In the background (the service
     * may be busy); a later configuration's result supersedes an earlier one's.
     */
    private void selectConfiguration(Configuration configuration) {
        long generation = this.configurationGeneration.incrementAndGet();
        List<AssociationInfoImpl> rows = new ArrayList<>(this.associationsData);
        Task<Set<Association>> selectionTask = new Task<>() {
            @Override
            protected Set<Association> call() {
                return service.getAssociations(configuration);
            }
        };
        selectionTask.setOnSucceeded(event -> {
            if (this.configurationGeneration.get() != generation)
                return;
            Set<Association> taken = selectionTask.getValue();
            for (AssociationInfoImpl row : rows)
                row.setSelected(taken.contains(row.getAssociation()));
        });
        TaskFailures.reportAndReenable(selectionTask);
        new Thread(selectionTask).start();
    }

    /**
     * Shows the composition of the ticked associations in the tree - after every change of the ticks
     * (see composeDelay); there was a Compose Selected button to press. A later composition
     * supersedes an earlier one still being built.
     */
    private void composeTicked() {
        long generation = this.composeGeneration.incrementAndGet();
        Collection<Association> ticked = new ArrayList<>();
        for (AssociationInfoImpl associationInfo : this.associationsData)
            if (associationInfo.isSelected())
                ticked.add(associationInfo.getAssociation());
        if (ticked.isEmpty()) {
            this.artifactTreeView.setRootNode(null);
            return;
        }
        Task<LazyCompositionRootNode> composeTask = new Task<>() {
            @Override
            protected LazyCompositionRootNode call() {
                LazyCompositionRootNode rootNode = new LazyCompositionRootNode();
                for (Association association : ticked)
                    rootNode.addOrigNode(association.getRootNode());
                return rootNode;
            }
        };
        composeTask.setOnSucceeded(event -> {
            if (this.composeGeneration.get() == generation)
                this.artifactTreeView.setRootNode(composeTask.getValue());
        });
        TaskFailures.reportAndReenable(composeTask);
        new Thread(composeTask).start();
    }

    /** Docks the Features pane at the left, or takes it away; it is built from the repository's features when first shown. */
    private void showFeatures(boolean show) {
        if (!show) {
            this.setLeft(null);
            return;
        }
        if (this.featureTogglePanel == null) {
            this.featureTogglePanel = new FeatureTogglePanel(service, this::selectConfiguration);
            Button exactButton = new Button("Exact Configuration...");
            exactButton.setTooltip(new Tooltip("Pick features with their revisions, or type a configuration - the pane takes the latest revisions"));
            exactButton.setMaxWidth(Double.MAX_VALUE);
            exactButton.setOnAction(e -> this.pickExactConfiguration());
            this.featureTogglePanel.getChildren().add(exactButton);
        } else
            this.featureTogglePanel.reloadConstraints();
        this.featureTogglePanel.setPrefWidth(240);
        this.setLeft(this.featureTogglePanel);
    }

    /** The configuration dialog (exact revisions, or typed): ticks its associations and its features in the pane. Package-visible for testing. */
    void pickExactConfiguration() {
        Optional<Configuration> result = this.askConfiguration();
        if (result.isEmpty())
            return;
        if (this.featureTogglePanel != null)
            this.featureTogglePanel.showConfiguration(result.get());
        this.selectConfiguration(result.get());
    }

    /** Package-visible so tests can answer the dialog. */
    Optional<Configuration> askConfiguration() {
        return new ConfigurationPickerDialog(service).showAndWait();
    }

    private void setAllAssociationsSelected(boolean flag) {
        for (AssociationInfoImpl assocInfo : ArtifactsView.this.associationsData) {
            assocInfo.setSelected(flag);
        }
    }

    /** Plain-data result of a background {@link #refresh()} computation - see that method. */
    private static final class RefreshResult {
        final List<? extends Association> associations;
        final int maxNumArtifacts;
        // per association id, computed here in the background: the artifact count and the full condition as shown
        final Map<String, Integer> numArtifacts = new HashMap<>();
        final Map<String, String> conditions = new HashMap<>();
        // persisted minimized conditions still valid for the repository as it is now
        final Map<String, String> validMinimized;

        RefreshResult(List<? extends Association> associations, int maxNumArtifacts, Map<String, String> validMinimized) {
            this.associations = associations;
            this.maxNumArtifacts = maxNumArtifacts;
            this.validMinimized = validMinimized;
        }
    }

    private void refresh() {
        // statusChangedEvent can fire many times in quick succession (e.g. once per folder from
        // both setBaseDir() and commit() during a multi-folder Commit, or once per commit in a
        // large bulk-commit session), each spawning its own background computation here - without
        // a generation guard, whichever one happens to finish last wins, which isn't necessarily
        // the one started last, so an earlier commit's stale (or empty, mid-commit) association
        // list could silently overwrite the true final state. Same pattern as
        // applyLiveConfiguration()'s liveConfigurationGeneration.
        //
        // Deliberately does NOT clear associationsData/the tree up front, unlike earlier versions
        // of this method: on a large repository, this computation (countArtifacts() over every
        // association) can take longer than the gap between commits during a fast bulk-commit
        // session, so every attempt keeps losing the generation race to the next one - clearing
        // eagerly meant the tab stayed visibly blank for the whole session instead of just showing
        // the previous (slightly stale, but real) state until a refresh actually wins.
        //
        // Uses a real Task (unlike the raw Thread this used to be) specifically so a failure here -
        // e.g. an OutOfMemoryError from walking a very large tree - surfaces via setOnFailed()
        // instead of silently killing the thread and leaving the toolbar disabled and the tree
        // stuck on whatever it last showed until the app is restarted.
        long generation = refreshGeneration.incrementAndGet();

        Platform.runLater(() -> toolBar.setDisable(true));

        Task<RefreshResult> refreshTask = new Task<>() {
            @Override
            protected RefreshResult call() {
                // sorted by id (stable regardless of the repository's own iteration order) so
                // that, as long as the set of associations doesn't change, each one keeps the
                // same auto-assigned color across refreshes rather than reshuffling
                List<? extends Association> associations = ArtifactsView.this.service.getRepository().getAssociations().stream()
                        .sorted(Comparator.comparing(Association::getId))
                        .toList();
                int max = 1;
                Map<String, Integer> counts = new HashMap<>();
                Map<String, String> conditions = new HashMap<>();
                for (Association a : associations) {
                    int count = a.getRootNode().countArtifacts();
                    counts.put(a.getId(), count);
                    conditions.put(a.getId(), a.computeCondition().toDisplayString());
                    max = Math.max(max, count);
                }
                RefreshResult result = new RefreshResult(associations, max, ArtifactsView.this.service.validMinimizedConditions());
                result.numArtifacts.putAll(counts);
                result.conditions.putAll(conditions);
                return result;
            }
        };
        refreshTask.setOnSucceeded(event -> {
            if (refreshGeneration.get() != generation) {
                return;
            }

            RefreshResult result = refreshTask.getValue();

            // set before mutating associationsData so cells never render against a stale max
            this.maxNumArtifacts = result.maxNumArtifacts;
            artifactTreeView.setRootNode(null);
            this.associationsData.clear();
            int index = 0;
            for (Association a : result.associations) {
                // prefer the persisted value, if still valid (see EccoService#validMinimizedConditions)
                // -- no timing dependency on whether MinimizationResults' own seeding (async, via
                // Platform.runLater) has already run by the time this refresh's own background Task
                // completes. Falls back to the shared live map, which MinimizationResults keeps to
                // valid results too; kept in sync afterward by the MapChangeListener registered in
                // the constructor.
                String minimizedCondition = result.validMinimized.get(a.getId());
                if (minimizedCondition == null) {
                    minimizedCondition = this.minimizationResults.getMinimizedByAssociationId().get(a.getId());
                }
                AssociationInfoImpl associationInfo = new AssociationInfoImpl(a, minimizedCondition,
                        result.numArtifacts.getOrDefault(a.getId(), 0), result.conditions.get(a.getId()));
                // color is only actually assigned once the association is selected (so the
                // "Highlighted" column and the code viewers stay blank for everything else),
                // but its slot is fixed now so the color stays the same association's color
                // across selections rather than depending on selection order
                Color assignedColor = CategoricalColorPalette.tintForBackground(CategoricalColorPalette.colorForIndex(index));
                associationInfo.selectedProperty().addListener((observable, wasSelected, isSelected) -> {
                    if (isSelected && associationInfo.colorProperty().get().equals(Color.TRANSPARENT)) {
                        associationInfo.colorProperty().set(assignedColor);
                    }
                });
                index++;
                this.associationsData.add(associationInfo);
            }
            artifactTreeView.setAssociationInfo(this.associationsData);
            // the features may have changed: built again, when shown again
            this.featureTogglePanel = null;
            if (this.featuresButton.isSelected())
                this.showFeatures(true);

            toolBar.setDisable(false);
        });
        refreshTask.setOnFailed(event -> {
            if (refreshGeneration.get() == generation) {
                toolBar.setDisable(false);
            }
            if (!at.jku.isse.ecco.gui.TaskFailures.repositoryClosed(refreshTask.getException()))
                new ExceptionAlert(refreshTask.getException()).show();
        });
        new Thread(refreshTask).start();
    }

    @Override
    public void statusChangedEvent(EccoService service) {
        if (service.isInitialized()) {
            Platform.runLater(() -> this.setDisable(false));
            refresh();
        } else {
            Platform.runLater(() -> {
                this.setDisable(true);
                this.artifactTreeView.setRootNode(null);
                this.associationsData.clear();
                this.maxNumArtifacts = 1;
                this.featuresButton.setSelected(false);
                this.featureTogglePanel = null;
            });
        }
    }
}
