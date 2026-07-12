package at.jku.isse.ecco.gui.view.artifacts;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.composition.LazyCompositionRootNode;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.gui.CategoricalColorPalette;
import at.jku.isse.ecco.gui.ExceptionAlert;
import at.jku.isse.ecco.gui.MinimizationResults;
import at.jku.isse.ecco.gui.io.ConfigurationPickerDialog;
import at.jku.isse.ecco.gui.io.DeleteDirectoryContentsDialog;
import at.jku.isse.ecco.gui.io.Directory;
import at.jku.isse.ecco.gui.io.FeatureTogglePanel;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.service.listener.EccoListener;
import javafx.application.Platform;
import javafx.beans.binding.When;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
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
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.util.Callback;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class ArtifactsView extends BorderPane implements EccoListener {

    private static final double BAR_WIDTH = 60;
    private static final double BAR_HEIGHT = 10;

    private final EccoService service;
    private final MinimizationResults minimizationResults;

    private final ObservableList<AssociationInfoImpl> associationsData = FXCollections.observableArrayList();
    private int maxNumArtifacts = 1;

    private final ToolBar toolBar;
    private final ArtifactTreeView artifactTreeView;

    private FeatureTogglePanel featureTogglePanel;
    private final AtomicLong liveConfigurationGeneration = new AtomicLong();
    private final AtomicLong refreshGeneration = new AtomicLong();


    public ArtifactsView(final EccoService service, MinimizationResults minimizationResults) {
        this.service = service;
        this.minimizationResults = minimizationResults;

        // this view only ever displays minimization results -- triggering a run happens in the
        // Feature Model tab, right after reviewing/accepting suggestions; see MinimizationResults.
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
        Button liveFeaturesButton = new Button("Live Features...");

        CheckBox showEmptyAssociationsCheckBox = new CheckBox("Show Associations Without Artifacts");
        CheckBox useSimplifiedLabelsCheckBox = new CheckBox("Use Simplified Labels");

        CheckBox showBelowAtomicCheckBox = new CheckBox("Show Artifacts Below Atomic"); // TODO
        showBelowAtomicCheckBox.setDisable(true);
        CheckBox showBelowFilesCheckBox = new CheckBox("Show Artifacts Below File Level"); // TODO
        showBelowFilesCheckBox.setDisable(true);

        toolBar.getItems().addAll(refreshButton, new Separator(),
                selectionMenuButton, checkoutSelectedButton, composeSelectedButton, liveFeaturesButton, new Separator(),
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
        // "then" is a live property (see MinimizationResults/AssociationInfoImpl), not a one-shot
        // computed string, so this column updates on its own as a shared "Minimize Presence
        // Conditions" run fills it in -- unlike the old getSimpleModuleRevisionConditionString(),
        // which was a truncation to the lowest-order module(s), not a real minimization, and was
        // only ever computed once per row build.
        conditionAssociationsCol.setCellValueFactory((TableColumn.CellDataFeatures<AssociationInfoImpl, String> param) -> new When(useSimplifiedLabelsCheckBox.selectedProperty()).then(param.getValue().minimizedConditionProperty()).otherwise(param.getValue().getAssociation().computeCondition().getModuleRevisionConditionString()));
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


        artifactTreeView = new ArtifactTreeView(service);

        // split panes
        SplitPane horizontalSplitPane = new SplitPane();
        horizontalSplitPane.setOrientation(Orientation.VERTICAL);
        horizontalSplitPane.getItems().addAll(associationsTable, artifactTreeView);

        this.setCenter(horizontalSplitPane);


        refreshButton.setOnAction(e -> refresh());

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

        liveFeaturesButton.setOnAction(e -> {
            if (featureTogglePanel == null) {
                featureTogglePanel = new FeatureTogglePanel(service, this::applyLiveConfiguration);
            }
            featureTogglePanel.show();
            featureTogglePanel.toFront();
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

                ConfigurationPickerDialog dialog = new ConfigurationPickerDialog(service);
                Optional<Configuration> result = dialog.showAndWait();

                if (result.isEmpty()) {
                    toolBar.setDisable(false);
                    return;
                }
                Configuration config = result.get();

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

                    // default to the directory the repository lives in, same as the full Checkout
                    // dialog -- there's no directory-picker step here, so this IS the effective
                    // default rather than just a pre-filled suggestion.
                    service.setBaseDir(service.getRepositoryHomeDir());

                    try {
                        if (!Directory.isEmpty(service.getBaseDir())) {
                            new DeleteDirectoryContentsDialog(service.getBaseDir()).showBlocked();
                        }
                    }catch (IOException e) {
                        ExceptionAlert alert = new ExceptionAlert(e);
                        alert.setTitle("Checkout Error");
                        alert.setHeaderText("Checkout Error");

                        alert.showAndWait();
                    }

                    Task<Void> checkoutTask = new CheckoutTask(service, rootNode);
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

    /**
     * Called by the {@link FeatureTogglePanel} on every checkbox toggle - the plug-and-play
     * alternative to Select by Configuration -> Compose Selected. Selection itself is cheap
     * (association.computeCertainCondition().holds(configuration) is a synchronous, in-memory
     * check already used elsewhere, e.g. AssociationsView's condition column, directly on the FX
     * thread - unlike EccoService.getAssociations(), which runs a full compose pipeline and is why
     * Select by Configuration needs a background Task) so it runs immediately here. Only the tree
     * rebuild stays on a background Task, same as Compose Selected. A generation counter discards
     * a stale rebuild's result if the user toggles another checkbox before the first rebuild
     * finishes, rather than letting an out-of-order compose silently overwrite a newer one.
     */
    private void applyLiveConfiguration(Configuration configuration) {
        long generation = liveConfigurationGeneration.incrementAndGet();

        Collection<Association> selectedAssociations = new ArrayList<>();
        for (AssociationInfoImpl associationInfo : ArtifactsView.this.associationsData) {
            // computeCertainCondition() lives on Association.Op, not the plain Association
            // AssociationInfoImpl exposes - every real association is an Op at runtime though
            Association.Op association = (Association.Op) associationInfo.getAssociation();
            boolean matches = association.computeCertainCondition().holds(configuration);
            associationInfo.setSelected(matches);
            if (matches) {
                selectedAssociations.add(associationInfo.getAssociation());
            }
        }

        Task<Void> composeTask = new Task<>() {
            @Override
            public Void call() {
                LazyCompositionRootNode rootNode = new LazyCompositionRootNode();
                for (Association association : selectedAssociations) {
                    rootNode.addOrigNode(association.getRootNode());
                }
                Platform.runLater(() -> {
                    if (liveConfigurationGeneration.get() == generation) {
                        artifactTreeView.setRootNode(rootNode);
                    }
                });
                return null;
            }
        };
        new Thread(composeTask).start();
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

        RefreshResult(List<? extends Association> associations, int maxNumArtifacts) {
            this.associations = associations;
            this.maxNumArtifacts = maxNumArtifacts;
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
                for (Association a : associations) {
                    max = Math.max(max, a.getRootNode().countArtifacts());
                }
                return new RefreshResult(associations, max);
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
                // seed from the shared model's current state (see MinimizationResults), rather than
                // resetting to empty on every refresh; kept in sync afterward by the
                // MapChangeListener registered in the constructor
                AssociationInfoImpl associationInfo = new AssociationInfoImpl(a, this.minimizationResults.getMinimizedByAssociationId().get(a.getId()));
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

            toolBar.setDisable(false);
        });
        refreshTask.setOnFailed(event -> {
            if (refreshGeneration.get() == generation) {
                toolBar.setDisable(false);
            }
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
                if (this.featureTogglePanel != null) {
                    this.featureTogglePanel.close();
                    this.featureTogglePanel = null;
                }
            });
        }
    }
}
