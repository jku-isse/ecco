package at.jku.isse.ecco.web.domain.model;

public class AssociationModel {

    public AssociationModel() {

    }

    public AssociationModel(String associationID, String association, String simpleModuleCondition, String simpleModuleRevisionCondition) {
        this.associationID = associationID;
        this.association = association;
        this.simpleModuleCondition = simpleModuleCondition;
        this.simpleModuleRevisionCondition = simpleModuleRevisionCondition;
    }

    private String associationID;
    private String association;
    private String simpleModuleCondition;
    private String simpleModuleRevisionCondition;

    public String getAssociationID() {
        return associationID;
    }

    public void setAssociationID(String associationID) {
        this.associationID = associationID;
    }

    public String getAssociation() {
        return association;
    }

    public void setAssociation(String association) {
        this.association = association;
    }

    public String getSimpleModuleCondition() {
        return simpleModuleCondition;
    }

    public void setSimpleModuleCondition(String simpleModuleCondition) {
        this.simpleModuleCondition = simpleModuleCondition;
    }

    public String getSimpleModuleRevisionCondition() {
        return simpleModuleRevisionCondition;
    }

    public void setSimpleModuleRevisionCondition(String simpleModuleRevisionCondition) {
        this.simpleModuleRevisionCondition = simpleModuleRevisionCondition;
    }
    //attributes, sowie getter und setter hinzufügen...

}
