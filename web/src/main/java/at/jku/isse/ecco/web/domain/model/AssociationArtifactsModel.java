package at.jku.isse.ecco.web.domain.model;

public class AssociationArtifactsModel {

    private String associationID;
    private int numberOfArtifacts;

    public AssociationArtifactsModel() {

    }

    public AssociationArtifactsModel(String associationID, int numberOfArtifacts) {
        this.associationID = associationID;
        this.numberOfArtifacts = numberOfArtifacts;
    }

    public String getAssociationID() {
        return associationID;
    }

    public void setAssociationID(String associationID) {
        this.associationID = associationID;
    }

    public int getNumberOfArtifacts() {
        return numberOfArtifacts;
    }

    public void setNumberOfArtifacts(int numberOfArtifacts) {
        this.numberOfArtifacts = numberOfArtifacts;
    }
}
