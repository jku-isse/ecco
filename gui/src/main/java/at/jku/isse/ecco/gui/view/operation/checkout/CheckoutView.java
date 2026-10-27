package at.jku.isse.ecco.gui.view.operation.checkout;

import at.jku.isse.ecco.gui.ConstraintWarnings;
import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.adapter.ArtifactWriter;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Checkout;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.gui.ExceptionTextArea;
import at.jku.isse.ecco.gui.RepositoryAccess;
import at.jku.isse.ecco.gui.TableColumns;
import at.jku.isse.ecco.gui.io.DeleteDirectoryContentsDialog;
import at.jku.isse.ecco.gui.io.Directory;
import at.jku.isse.ecco.gui.view.detail.CheckoutDetailView;
import at.jku.isse.ecco.gui.view.operation.OperationView;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.service.listener.EccoListener;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Color;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

public class CheckoutView extends OperationView implements EccoListener {
    // SplitPane splits evenly when the result joins the log, and the window was sized for the log
    // alone (step2) - so both were squeezed into a strip, the log down to one clipped row. An even
    // share and growing the window keep the log and the checkout's details below it readable.
    private static final double LOG_DIVIDER_POSITION = 0.5;

    private final EccoService service;
    private final ObservableList<FileInfo> logData = FXCollections.observableArrayList();
    private final SplitPane splitPane;
    private final CheckoutDetailView checkoutDetailView;
    private final TableView<FileInfo> logTable;
    private Path currentBaseDir;

    public CheckoutView(EccoService service) {
        super();
        this.service = service;


        // split pane
        this.splitPane = new SplitPane();
        this.splitPane.setOrientation(Orientation.VERTICAL);

        // checkout detail view
        this.checkoutDetailView = new CheckoutDetailView(service);

        // log table
        this.logTable = new TableView<>();
        logTable.setEditable(false);

        TableColumn<FileInfo, String> actionCol = new TableColumn<>("Action");
        // a read or write row names a file and its plugin, a select row an association and its condition
        TableColumn<FileInfo, String> pathCol = new TableColumn<>("Path / Association");
        TableColumn<FileInfo, String> pluginCol = new TableColumn<>("Plugin / Condition");

        logTable.getColumns().setAll(actionCol, pathCol, pluginCol);

        actionCol.setCellValueFactory(new PropertyValueFactory<>("action"));
        pathCol.setCellValueFactory(new PropertyValueFactory<>("path"));
        pluginCol.setCellValueFactory(new PropertyValueFactory<>("plugin"));
        // Plugin mixes short plugin-class-name content (read/write rows) with a condition (select
        // rows) - wrap rather than clip either kind.
        pluginCol.setCellFactory(TableColumns.wrappingCellFactory());

        logTable.setItems(this.logData);

        TableColumns.defaultWidth(actionCol, 90);
        TableColumns.fitToContent(pluginCol, this.logData);
        TableColumns.growToFill(logTable, pathCol);

        splitPane.getItems().add(logTable);


        // no folder to begin with: it was the repository's own one - with the user's files in it -
        // and service.getBaseDir() before that, wherever a previous operation had left it
        this.step1("", "");
    }


    /**
     * Base directory and configuration string, initially {@code initialBaseDir} and
     * {@code initialConfiguration} (what was entered before when coming back to this step).
     */
    private void step1(String initialBaseDir, String initialConfiguration) {
        Button cancelButton = new Button("Cancel");
        cancelButton.setOnAction(event -> ((Stage) this.getScene().getWindow()).close());
        this.leftButtons.getChildren().setAll(cancelButton);

        this.headerLabel.setText("Directory and Configuration");

        Button checkoutButton = new Button("Checkout");
        this.rightButtons.getChildren().setAll(checkoutButton);


        // main content
        GridPane gridPane = new GridPane();
        gridPane.setHgap(10);
        gridPane.setVgap(10);
        gridPane.setPadding(new Insets(10, 10, 10, 10));

        ColumnConstraints col1constraint = new ColumnConstraints();
        col1constraint.setMinWidth(GridPane.USE_PREF_SIZE);
        ColumnConstraints col2constraint = new ColumnConstraints();
        col2constraint.setFillWidth(true);
        col2constraint.setHgrow(Priority.ALWAYS);
        gridPane.getColumnConstraints().addAll(col1constraint, col2constraint);

        this.setCenter(gridPane);

        int row = 0;


        Label baseDirLabel = new Label("Checkout Folder: ");
        gridPane.add(baseDirLabel, 0, row, 1, 1);

        TextField baseDirTextField = new TextField(initialBaseDir);
        baseDirTextField.setDisable(false);
        // empty, not the repository's folder: that held the user's own files, and a checkout into
        // it deleted them after one Enter (see DeleteDirectoryContentsDialog)
        baseDirTextField.setPromptText("The folder to write the variant into - best an empty one");
        baseDirLabel.setLabelFor(baseDirTextField);
        gridPane.add(baseDirTextField, 1, row, 1, 1);
        checkoutButton.disableProperty().bind(baseDirTextField.textProperty().isEmpty());

        Button selectBaseDirectoryButton = new Button("Browse...");
        gridPane.add(selectBaseDirectoryButton, 2, row, 1, 1);
        row++;


        Label configurationStringLabel = new Label("Configuration: ");
        gridPane.add(configurationStringLabel, 0, row, 1, 1);

        TextField configurationStringTextField = new TextField(initialConfiguration);
        configurationStringTextField.setDisable(false);
        configurationStringTextField.setPromptText(at.jku.isse.ecco.gui.view.operation.OperationView.CONFIGURATION_HINT);
        configurationStringLabel.setLabelFor(configurationStringTextField);
        gridPane.add(at.jku.isse.ecco.gui.io.ConfigurationPickerDialog.withPicker(configurationStringTextField, this.service), 1, row, 2, 1);
        row++;

        // known-variants picker -- speeds up checkout by prefilling Configuration from an
        // already-saved variant (see the Variants tab) instead of retyping it; flags variants that
        // currently violate an accepted constraint the same way the rest of this feature does.
        Label knownVariantsLabel = new Label("Known Variants: ");
        gridPane.add(knownVariantsLabel, 0, row, 1, 1);

        ComboBox<at.jku.isse.ecco.core.Variant> knownVariantsComboBox = new ComboBox<>();
        knownVariantsComboBox.setPromptText("(select to prefill Configuration)");
        knownVariantsComboBox.setMaxWidth(Double.MAX_VALUE);
        gridPane.add(knownVariantsComboBox, 1, row, 2, 1);
        row++;

        // each variant's label, configuration and warning, read with the variants (holding the
        // service's lock): the cells and the action do not read the variants themselves
        java.util.Map<at.jku.isse.ecco.core.Variant, String> knownVariantWarnings = new java.util.IdentityHashMap<>();
        java.util.Map<at.jku.isse.ecco.core.Variant, String> knownVariantLabels = new java.util.IdentityHashMap<>();
        java.util.Map<at.jku.isse.ecco.core.Variant, String> knownVariantConfigurations = new java.util.IdentityHashMap<>();
        javafx.util.Callback<ListView<at.jku.isse.ecco.core.Variant>, ListCell<at.jku.isse.ecco.core.Variant>> variantCellFactory = lv -> new ListCell<at.jku.isse.ecco.core.Variant>() {
            @Override
            protected void updateItem(at.jku.isse.ecco.core.Variant variant, boolean empty) {
                super.updateItem(variant, empty);
                if (empty || variant == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                String label = knownVariantLabels.getOrDefault(variant, "");
                String warning = knownVariantWarnings.get(variant);
                if (warning != null && !warning.isEmpty()) {
                    setText("⚠ " + label);
                    setStyle("-fx-text-fill: firebrick;");
                } else {
                    setText(label);
                    setStyle("");
                }
            }
        };
        knownVariantsComboBox.setCellFactory(variantCellFactory);
        knownVariantsComboBox.setButtonCell(variantCellFactory.call(null));
        knownVariantsComboBox.setOnAction(event -> {
            at.jku.isse.ecco.core.Variant selected = knownVariantsComboBox.getValue();
            if (selected != null && knownVariantConfigurations.containsKey(selected)) {
                configurationStringTextField.setText(knownVariantConfigurations.get(selected));
            }
        });

        if (this.service.isInitialized() && !this.service.isWriteInProgress()) {
            new Thread(() -> {
                java.util.List<at.jku.isse.ecco.core.Variant> variants;
                java.util.Map<at.jku.isse.ecco.core.Variant, String> warnings = new java.util.IdentityHashMap<>();
                java.util.Map<at.jku.isse.ecco.core.Variant, String> labels = new java.util.IdentityHashMap<>();
                java.util.Map<at.jku.isse.ecco.core.Variant, String> configurations = new java.util.IdentityHashMap<>();
                // holding the service's lock: a commit or pull running meanwhile changes the variants
                // (iterated without it: a ConcurrentModificationException)
                synchronized (this.service) {
                    if (!this.service.isInitialized())
                        return;
                    variants = new java.util.ArrayList<>(this.service.getRepository().getVariants());
                    for (at.jku.isse.ecco.core.Variant variant : variants) {
                        labels.put(variant, variant.getName() == null || variant.getName().isEmpty() ? variant.getId() : variant.getName());
                        configurations.put(variant, variant.getConfiguration().toString());
                        String warning = ConstraintWarnings.describe(this.service, variant.getConfiguration());
                        if (!warning.isEmpty()) warnings.put(variant, warning);
                    }
                }
                Platform.runLater(() -> {
                    knownVariantWarnings.putAll(warnings);
                    knownVariantLabels.putAll(labels);
                    knownVariantConfigurations.putAll(configurations);
                    knownVariantsComboBox.getItems().setAll(variants);
                    knownVariantsComboBox.setButtonCell(variantCellFactory.call(null));
                });
            }).start();
        }

        // live constraint-violation feedback as the user enters a configuration -- see
        // EccoService.checkConstraintViolations; debounced so it doesn't re-check on every keystroke.
        Label constraintWarningLabel = new Label();
        constraintWarningLabel.setTextFill(Color.FIREBRICK);
        constraintWarningLabel.setWrapText(true);
        gridPane.add(constraintWarningLabel, 1, row, 2, 1);
        row++;

        PauseTransition constraintCheckDebounce = new PauseTransition(Duration.millis(400));
        constraintCheckDebounce.setOnFinished(event -> {
            String configurationString = configurationStringTextField.getText();
            if (configurationString == null || configurationString.isBlank() || !this.service.isInitialized()
                    || this.service.isWriteInProgress()) {
                Platform.runLater(() -> constraintWarningLabel.setText(""));
                return;
            }
            new Thread(() -> {
                String text = ConstraintWarnings.describe(this.service, configurationString);
                Platform.runLater(() -> constraintWarningLabel.setText(text));
            }).start();
        });
        configurationStringTextField.textProperty().addListener((obs, oldV, newV) -> {
            constraintCheckDebounce.stop();
            constraintCheckDebounce.playFromStart();
        });
        // coming back with a configuration entered: warn about it again without waiting for an edit
        if (!initialConfiguration.isBlank()) {
            constraintCheckDebounce.playFromStart();
        }


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


        checkoutButton.setOnAction(event -> {
            this.step2();

            Path baseDir = Paths.get(baseDirTextField.getText());
            String configurationString = configurationStringTextField.getText();

            // the configuration is checked - and its constraint violations asked about - before the
            // folder's contents are deleted: deleted first, a typo ("A, Z") was refused afterwards
            // and an emptied folder was all that was left (FR-K13)
            RepositoryAccess.read(this.service, "checkout check", s -> {
                s.checkCheckoutConfiguration(configurationString);
                return configurationString.isBlank() ? "" : ConstraintWarnings.describe(s, configurationString);
            }, violations -> this.checkout(baseDir, baseDirTextField.getText(), configurationString, violations), this::checkoutFailed);
        });


        this.fit();

        Platform.runLater(configurationStringTextField::requestFocus);
    }

    /**
     * Checks out the configuration, checked to name committed features and revisions only, into
     * {@code baseDir} - asking about its constraint {@code violations}, if any, and then about
     * deleting the folder's contents.
     */
    private void checkout(Path baseDir, String baseDirText, String configurationString, String violations) {
        if (!violations.isEmpty() && !this.askToProceedAnyway(violations, "check out")) {
            this.step1(baseDirText, configurationString);
            return;
        }

        try {
            // the user is free to type/pick a directory that doesn't exist yet (a brand-new
            // checkout target) - Directory.isEmpty() (and the writer after it) both require it
            // to already exist, so create it here rather than surfacing a raw NoSuchFileException.
            if (!Files.exists(baseDir)) {
                Files.createDirectories(baseDir);
            }
            if (!Directory.isEmpty(baseDir) && !this.confirmClearDirectory(baseDir)) {
                this.step1(baseDirText, configurationString);
                return;
            }
        } catch (IOException e) {
            this.checkoutFailed(e);
            return;
        }

        this.service.setBaseDir(baseDir);
        this.currentBaseDir = baseDir;
        this.logData.clear();
        this.service.addListener(this);

        Task<Checkout> checkoutTask = new CheckoutTask(this, service, configurationString);
        this.track(checkoutTask, "The checkout");
        new Thread(checkoutTask).start();
    }

    /**
     * Asks whether to {@code actionVerb} despite the constraint violations in {@code description} --
     * constraint violations are advisory (see {@code EccoService#checkConstraintViolations}), never a
     * hard block.
     * Package-visible for testing.
     *
     * @return true if the user confirmed.
     */
    boolean askToProceedAnyway(String description, String actionVerb) {
        Alert alert = new at.jku.isse.ecco.gui.OwnedAlert(Alert.AlertType.CONFIRMATION,
                description + "\n\nDo you want to " + actionVerb + " anyway?");
        alert.setHeaderText("Constraint violation");
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    /**
     * Asks whether to delete the contents of the non-empty {@code baseDir} before checking out into
     * it, and deletes them if so. Package-visible for testing.
     *
     * @return true if the contents were deleted, false to go back to the first step.
     */
    boolean confirmClearDirectory(Path baseDir) throws IOException {
        return new DeleteDirectoryContentsDialog(baseDir).showBlocked();
    }

    protected void checkoutSucceeded(Checkout checkout) {
        this.handleCheckoutResult(checkout, this.checkoutDetailView);
        this.showSuccessHeader();
    }

    protected void checkoutFailed(Throwable exception) {
        this.handleCheckoutResult(null, new at.jku.isse.ecco.gui.ErrorDetails(exception));
        this.showErrorHeader();
    }

    protected void handleCheckoutResult(Checkout checkout, Node supportNode) {
        this.service.removeListener(this);
        this.checkoutDetailView.showCheckout(checkout, this.currentBaseDir);
        this.splitPane.getItems().setAll(this.logTable, supportNode);
        this.splitPane.setDividerPositions(LOG_DIVIDER_POSITION);
        this.growToFit();
    }

    /**
     * Log table and success or error.
     */
    private void step2() {
        // nothing to cancel with: a checkout cannot be stopped - its "Cancel" button did nothing
        this.leftButtons.getChildren().clear();

        this.headerLabel.setText("Checking out ...");

        this.rightButtons.getChildren().clear();


        this.setCenter(splitPane);


        this.fit();
    }


    private static final String READ_ACTION_STRING = "READ";
    private static final String WRITE_ACTION_STRING = "WRITE";
    private static final String ASSOCIATION_SELECTION_STRING = "SELECT";

    @Override
    public void fileReadEvent(Path file, ArtifactReader reader) {
        Platform.runLater(() -> this.logData.add(new FileInfo(READ_ACTION_STRING, file.toString(), reader.getPluginId())));
    }

    @Override
    public void fileWriteEvent(Path file, ArtifactWriter writer) {
        Platform.runLater(() -> this.logData.add(new FileInfo(WRITE_ACTION_STRING, file.toString(), writer.getPluginId())));
    }

    @Override
    public void associationSelectedEvent(EccoService service, Association association) {
        // the absorbed condition (X | X & Y = X), not toString(): that lists computeCondition()'s
        // whole lattice of module revisions, thousands of terms in a repository with a few dozen
        // commits, which wrapped into one row taller than the table. Computed here, off the FX thread.
        String condition = association.computeCondition().toLogicString();
        Platform.runLater(() -> this.logData.add(new FileInfo(ASSOCIATION_SELECTION_STRING, String.valueOf(association.getId()), condition)));
    }


    public static class FileInfo {
        private final SimpleStringProperty action;
        private final SimpleStringProperty path;
        private final SimpleStringProperty plugin;

        private FileInfo(String action, String path, String plugin) {
            this.action = new SimpleStringProperty(action);
            this.path = new SimpleStringProperty(path);
            this.plugin = new SimpleStringProperty(plugin);
        }

        public String getAction() {
            return this.action.get();
        }

        public void setAction(String action) {
            this.action.set(action);
        }

        public String getPath() {
            return this.path.get();
        }

        public void setPath(String path) {
            this.path.set(path);
        }

        public String getPlugin() {
            return this.plugin.get();
        }

        public void setPlugin(String plugin) {
            this.plugin.set(plugin);
        }
    }
}
