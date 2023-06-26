package at.jku.isse.ecco.gui.view.detail;

import at.jku.isse.ecco.core.Variant;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.*;

public class AddVariantView extends BorderPane {
    private final Pane centerPane;
    private final ToolBar toolBar;
    private final TextField variantConfiguration;
    private Variant currentVariant;

    public AddVariantView() {

        this.currentVariant = null;

        // toolbar
        this.toolBar = new ToolBar();
        this.setTop(toolBar);

        // details
        GridPane detailsPane = new GridPane();
        this.centerPane = detailsPane;
        detailsPane.setHgap(10);
        detailsPane.setVgap(10);
        detailsPane.setPadding(new Insets(10, 10, 10, 10));
        this.setCenter(this.centerPane);

        ColumnConstraints col1constraint = new ColumnConstraints();
        ColumnConstraints col2constraint = new ColumnConstraints();
        col2constraint.setFillWidth(true);
        col2constraint.setHgrow(Priority.ALWAYS);
        detailsPane.getColumnConstraints().addAll(col1constraint, col2constraint);


        TextField variantName = new TextField();
        variantName.setEditable(true);
        this.variantConfiguration = new TextField();
        this.variantConfiguration.setEditable(true);


        int row = 0;
        detailsPane.add(new Label("Name: "), 1, row, 1, 1);
        row++;
        detailsPane.add(variantName, 1, row, 1, 1);
        row++;

        detailsPane.add(new Label("Configuration: "), 1, row, 1, 1);
        row++;
        detailsPane.add(this.variantConfiguration, 1, row, 1, 1);
        row++;

        // show nothing initially
        this.showVariant(null);
    }

    public void showVariant(Variant variant) {
        this.currentVariant = variant;

        if (variant != null) {
            this.setCenter(this.centerPane);
            this.toolBar.setDisable(false);

            if (variant.getConfiguration() != null)
                this.variantConfiguration.setText(String.valueOf(variant.getConfiguration().toString()));
            else
                this.variantConfiguration.setText("");

        } else {
            this.setCenter(null);
            this.toolBar.setDisable(true);

            this.variantConfiguration.setText("");
        }
    }

}
