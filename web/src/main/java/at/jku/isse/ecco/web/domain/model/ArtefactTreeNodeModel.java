package at.jku.isse.ecco.web.domain.model;

import java.util.ArrayList;
import java.util.List;

public class ArtefactTreeNodeModel {

    private List<ArtefactTreeNodeModel> childNodes = new ArrayList<>();
    private boolean isOrdered;
    private boolean isAtomic;
    private boolean isUnique;
    private int sequenceNumber;
    private String correspondingAssociation;
    private String artefactData;

    public ArtefactTreeNodeModel(List<ArtefactTreeNodeModel> childNodes, boolean isOrdered, boolean isAtomic, boolean isUnique, int sequenceNumber, String correspondingAssociation, String artefactData) {
        this.childNodes = childNodes;
        this.isOrdered = isOrdered;
        this.isAtomic = isAtomic;
        this.isUnique = isUnique;
        this.sequenceNumber = sequenceNumber;
        this.correspondingAssociation = correspondingAssociation;
        this.artefactData = artefactData;
    }

    public ArtefactTreeNodeModel() {

    }

    public void addChildNode(ArtefactTreeNodeModel givenNode) {
        this.childNodes.add(givenNode);
    }

    public List<ArtefactTreeNodeModel> getChildNodes() {
        return childNodes;
    }

    public void setChildNodes(List<ArtefactTreeNodeModel> childNodes) {
        this.childNodes = childNodes;
    }

    public boolean isOrdered() {
        return isOrdered;
    }

    public void setOrdered(boolean ordered) {
        isOrdered = ordered;
    }

    public boolean isAtomic() {
        return isAtomic;
    }

    public void setAtomic(boolean atomic) {
        isAtomic = atomic;
    }

    public boolean isUnique() {
        return isUnique;
    }

    public void setUnique(boolean unique) {
        isUnique = unique;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(int sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public String getCorrespondingAssociation() {
        return correspondingAssociation;
    }

    public void setCorrespondingAssociation(String correspondingAssociation) {
        this.correspondingAssociation = correspondingAssociation;
    }

    public String getArtefactData() {
        return artefactData;
    }

    public void setArtefactData(String artefactData) {
        this.artefactData = artefactData;
    }

}
