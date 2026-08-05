package at.jku.isse.ecco.gui.view.operation.checkout;

import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.adapter.ArtifactWriter;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Checkout;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.gui.ExceptionTextArea;
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
        TableColumn<FileInfo, String> pathCol = new TableColumn<>("Path");
        TableColumn<FileInfo, String> pluginCol = new TableColumn<>("Plugin");

        logTable.getColumns().setAll(actionCol, pathCol, pluginCol);

        actionCol.setCellValueFactory(new PropertyValueFactory<>("action"));
        pathCol.setCellValueFactory(new PropertyValueFactory<>("path"));
        pluginCol.setCellValueFactory(new PropertyValueFactory<>("plugin"));
        // Plugin mixes short plugin-class-name content (read/write rows) with a potentially long
        // computeCondition().toString() (select rows) - wrap rather than clip either kind.
        pluginCol.setCellFactory(TableColumns.wrappingCellFactory());

        logTable.setItems(this.logData);

        TableColumns.defaultWidth(actionCol, 90);
        TableColumns.fitToContent(pluginCol, this.logData);
        TableColumns.growToFill(logTable, pathCol);

        splitPane.getItems().add(logTable);


        this.step1();
    }


    /**
     * Base directory and configuration string.
     */
    private void step1() {
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


        Label baseDirLabel = new Label("Base Directory: ");
        gridPane.add(baseDirLabel, 0, row, 1, 1);

        // default to the directory the repository lives in, not whatever service.getBaseDir()
        // happens to still be set to from a previous, possibly unrelated operation (e.g. a Commit
        // or Fork done from a different folder) -- still freely editable below before the actual
        // checkout.
        TextField baseDirTextField = new TextField(service.getRepositoryHomeDir().toString());
        baseDirTextField.setDisable(false);
        baseDirLabel.setLabelFor(baseDirTextField);
        gridPane.add(baseDirTextField, 1, row, 1, 1);

        Button selectBaseDirectoryButton = new Button("...");
        gridPane.add(selectBaseDirectoryButton, 2, row, 1, 1);
        row++;


        Label configurationStringLabel = new Label("Configuration: ");
        gridPane.add(configurationStringLabel, 0, row, 1, 1);

        TextField configurationStringTextField = new TextField();
        configurationStringTextField.setDisable(false);
        configurationStringLabel.setLabelFor(configurationStringTextField);
        gridPane.add(configurationStringTextField, 1, row, 2, 1);
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

        java.util.Map<at.jku.isse.ecco.core.Variant, String> knownVariantWarnings = new java.util.HashMap<>();
        javafx.util.Callback<ListView<at.jku.isse.ecco.core.Variant>, ListCell<at.jku.isse.ecco.core.Variant>> variantCellFactory = lv -> new ListCell<at.jku.isse.ecco.core.Variant>() {
            @Override
            protected void updateItem(at.jku.isse.ecco.core.Variant variant, boolean empty) {
                super.updateItem(variant, empty);
                if (empty || variant == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                String label = variant.getName() == null || variant.getName().isEmpty() ? variant.getId() : variant.getName();
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
            if (selected != null) {
                configurationStringTextField.setText(selected.getConfiguration().toString());
            }
        });

        if (this.service.isInitialized() && !this.service.isWriteInProgress()) {
            new Thread(() -> {
                java.util.List<at.jku.isse.ecco.core.Variant> variants =
                        new java.util.ArrayList<>(this.service.getRepository().getVariants());
                for (at.jku.isse.ecco.core.Variant variant : variants) {
                    String warning = describeConstraintViolations(variant.getConfiguration());
                    if (!warning.isEmpty()) knownVariantWarnings.put(variant, warning);
                }
                Platform.runLater(() -> {
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
                String text = describeConstraintViolations(configurationString);
                Platform.runLater(() -> constraintWarningLabel.setText(text));
            }).start();
        });
        configurationStringTextField.textProperty().addListener((obs, oldV, newV) -> {
            constraintCheckDebounce.stop();
            constraintCheckDebounce.playFromStart();
        });


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

            try{
                if (!Directory.isEmpty(baseDir) && !(new DeleteDirectoryContentsDialog(baseDir).showBlocked())) {
                    this.step1();
                    return;
                }
            } catch (IOException e) {
                this.checkoutFailed(e);
                return;
            }

            if (!confirmProceedDespiteViolations(configurationString, "check out")) {
                this.step1();
                return;
            }

            this.service.setBaseDir(baseDir);
            this.currentBaseDir = baseDir;
            this.logData.clear();
            this.service.addListener(this);

            Task<Checkout> checkoutTask = new CheckoutTask(this, service, configurationString);
            new Thread(checkoutTask).start();
        });


        this.fit();

        Platform.runLater(configurationStringTextField::requestFocus);
    }

    /** Empty string if no violations (or the configuration can't be parsed yet, e.g. mid-typing). */
    private String describeConstraintViolations(String configurationString) {
        try {
            return describeConstraintViolations(this.service.parseConfigurationString(configurationString));
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** Empty string if no violations. */
    private String describeConstraintViolations(Configuration configuration) {
        try {
            List<String> violations = this.service.checkConstraintViolations(configuration);
            return violations.isEmpty() ? "" : "Violates accepted constraint(s): " + String.join("; ", violations);
        } catch (RuntimeException e) {
            return "";
        }
    }

    /**
     * Checks the configuration against accepted constraints and, if it violates any, asks the user
     * to confirm before proceeding -- constraint violations are advisory (see
     * {@code EccoService#checkConstraintViolations}), never a hard block, so the user can still choose
     * to {@code actionVerb} anyway.
     *
     * @return true if there were no violations, or the user confirmed anyway; false to abort.
     */
    private boolean confirmProceedDespiteViolations(String configurationString, String actionVerb) {
        if (configurationString == null || configurationString.isBlank() || !this.service.isInitialized()) return true;
        String description = describeConstraintViolations(configurationString);
        if (description.isEmpty()) return true;
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                description + "\n\nDo you want to " + actionVerb + " anyway?");
        alert.setHeaderText("Constraint violation");
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    protected void checkoutSucceeded(Checkout checkout) {
        this.handleCheckoutResult(checkout, this.checkoutDetailView);
        this.showSuccessHeader();
    }

    protected void checkoutFailed(Throwable exception) {
        this.handleCheckoutResult(null, new ExceptionTextArea(exception));
        this.showErrorHeader();
    }

    protected void handleCheckoutResult(Checkout checkout, Node supportNode) {
        this.service.removeListener(this);
        this.checkoutDetailView.showCheckout(checkout, this.currentBaseDir);
        this.splitPane.getItems().setAll(this.logTable, supportNode);
    }

    /**
     * Log table and success or error.
     */
    private void step2() {
        Button cancelButton = new Button("Cancel");
        this.leftButtons.getChildren().setAll(cancelButton);

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
        Platform.runLater(() -> this.logData.add(new FileInfo(ASSOCIATION_SELECTION_STRING, String.valueOf(association.getId()), association.computeCondition().toString())));
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
