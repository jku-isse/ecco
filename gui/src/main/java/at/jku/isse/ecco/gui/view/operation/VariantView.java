package at.jku.isse.ecco.gui.view.operation;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.gui.view.detail.AddVariantView;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.service.listener.EccoListener;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;

public class VariantView extends OperationView implements EccoListener {
    private final EccoService service;
    private final SplitPane splitPane;
    private final AddVariantView addVariantView;

    public VariantView(EccoService service) {
        super();
        this.service = service;
        this.addVariantView = new AddVariantView();

        // split pane
        this.splitPane = new SplitPane();
        this.splitPane.setOrientation(Orientation.VERTICAL);
        this.addVariant();
    }

    public void addVariant(){
        Button cancelButton = new Button("Cancel");
        cancelButton.setOnAction(event -> ((Stage) this.getScene().getWindow()).close());
        this.leftButtons.getChildren().add(cancelButton);

        this.headerLabel.setText("Configuration");

        Button addButton = new Button("Add");
        this.rightButtons.getChildren().setAll(addButton);

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

        Label configLabel = new Label("Configuration: ");
        gridPane.add(configLabel, 0, row, 1, 1);

        TextField configField = new TextField();
        configField.setDisable(false);
        configLabel.setLabelFor(configField);
        gridPane.add(configField, 1, row, 1, 1);

        Label nameLabel = new Label("Name: ");
        gridPane.add(nameLabel, 2, row, 1, 1);

        TextField nameField = new TextField();
        nameField.setDisable(false);
        nameLabel.setLabelFor(nameField);
        gridPane.add(nameField, 3, row, 1, 1);
        row++;


        addButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent e) {
                toolBar.setDisable(true);

                Task variantsUpdateTask = new Task<Void>() {
                    @Override
                    public Void call() throws EccoException {
                        Platform.runLater(() -> {
                            String configuration = configField.getText();
                            String name = nameField.getText();
                            if(!configuration.equals("")) {
                                Configuration config = service.parseConfigurationString(configuration);
                                service.addVariant(config, name);
                            }else{
                                failed();
                            }
                        });
                        Platform.runLater(() -> toolBar.setDisable(false));
                        return null;
                    }

                    @Override
                    public void succeeded() {
                        super.succeeded();
                        VariantView.this.showSuccessHeader();
                    }

                    @Override
                    public void failed() {
                        super.failed();
                        VariantView.this.showErrorHeader();
                    }

                };

                new Thread(variantsUpdateTask).start();
            }
        });
    }
}
