package at.jku.isse.ecco.adapter.lilypond.view;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.module.Condition;
import javafx.scene.control.TextArea;
import javafx.scene.text.Text;

public class ArtifactDataTextNode extends Text {

    private static TextArea taInfo;
    private Association association;

    public Association getAssociation() {
        return association;
    }

    public void setAssociation(Association association) {
        this.association = association;
    }

    public static void setInfoArea(TextArea ta) {
        taInfo = ta;
    }

    public ArtifactDataTextNode(String text) {
        super(text);

        setOnMouseEntered(e -> showAssociationHandler());
    }

    private void showAssociationHandler() {
        if (taInfo == null || association == null) return;

        Condition c = association.computeCondition();
        taInfo.setText(association.getId().concat(" (").concat(c.getSimpleModuleRevisionConditionString()).concat(")\n").concat(c.getModuleRevisionConditionString()));
    }
}
