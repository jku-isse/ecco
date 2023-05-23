package at.jku.isse.ecco.gui.view.operation.checkout;

import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.adapter.ArtifactWriter;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Checkout;
import at.jku.isse.ecco.gui.ExceptionTextArea;
import at.jku.isse.ecco.gui.io.DeleteDirectoryContentsDialog;
import at.jku.isse.ecco.gui.io.Directory;
import at.jku.isse.ecco.gui.view.detail.CheckoutDetailView;
import at.jku.isse.ecco.gui.view.operation.OperationView;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.service.listener.EccoListener;
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
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class CheckoutView extends OperationView implements EccoListener {

    private final EccoService service;

    private final ObservableList<FileInfo> logData = FXCollections.observableArrayList();


    private final SplitPane splitPane;
    private final CheckoutDetailView checkoutDetailView;
    private final TableView<FileInfo> logTable;


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
        logTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<FileInfo, String> actionCol = new TableColumn<>("Action");
        TableColumn<FileInfo, String> pathCol = new TableColumn<>("Path");
        TableColumn<FileInfo, String> pluginCol = new TableColumn<>("Plugin");

        logTable.getColumns().setAll(actionCol, pathCol, pluginCol);

        actionCol.setCellValueFactory(new PropertyValueFactory<>("action"));
        pathCol.setCellValueFactory(new PropertyValueFactory<>("path"));
        pluginCol.setCellValueFactory(new PropertyValueFactory<>("plugin"));

        logTable.setItems(this.logData);

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

        TextField baseDirTextField = new TextField(service.getBaseDir().toString());
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


            this.service.setBaseDir(baseDir);
            this.logData.clear();
            this.service.addListener(this);

            Task<Checkout> checkoutTask = new CheckoutTask(this, service, configurationString);
            new Thread(checkoutTask).start();
        });


        this.fit();

        Platform.runLater(configurationStringTextField::requestFocus);
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
        this.checkoutDetailView.showCheckout(checkout);
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
