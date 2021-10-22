package at.jku.isse.ecco.adapter.lilypond.view;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.module.Condition;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;

public class ArtifactDataLabelNode extends Region {

    private static TextArea taInfo;
    private Association association;
    private Label label;

    public Association getAssociation() {
        return association;
    }

    public void setAssociation(Association association) {
        this.association = association;
    }

    public static void setInfoArea(TextArea ta) {
        taInfo = ta;
    }

    private ObjectProperty<Color> backgroundColorProperty = new SimpleObjectProperty<>();
    public ObjectProperty<Color> getBackgroundColorProperty() { return backgroundColorProperty; }

    public ArtifactDataLabelNode(String text) {
        if (text.isEmpty()) { return; }

        setPadding(new Insets(0, 1, 0, 1));
        label = new Label(text);
        getChildren().add(label);
        backgroundColorProperty.addListener((observableValue, oldColor, newColor) -> {
            if (newColor == null || newColor == Color.TRANSPARENT) {
                newColor = Color.WHITE;
            }
            label.setBackground(new Background(new BackgroundFill(newColor, null, null)));
        });
        backgroundColorProperty.set(Color.WHITE);
        setOnMouseEntered(e -> showAssociationInfo());
    }

    public ArtifactDataLabelNode(String text, Association a) {
        this(text);

        setAssociation(a);
    }

    private void showAssociationInfo() {
        if (taInfo == null || association == null) return;

        Condition c = association.computeCondition();
        taInfo.setText(association.getId().concat(" (").concat(c.getSimpleModuleRevisionConditionString()).concat(")\n").concat(c.getModuleRevisionConditionString()));
    }
}
