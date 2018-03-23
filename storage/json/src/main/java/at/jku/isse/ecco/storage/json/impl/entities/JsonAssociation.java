package at.jku.isse.ecco.storage.json.impl.entities;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.counter.AssociationCounter;
import at.jku.isse.ecco.module.Condition;
import at.jku.isse.ecco.tree.RootNode;

import java.io.Serializable;

public class JsonAssociation implements Association.Op, Serializable {


    private String id;
    private RootNode.Op artifactTreeRoot;
    private AssociationCounter associationCounter;


    public JsonAssociation() {
        this.id = "";
        this.artifactTreeRoot = null;
        this.associationCounter = new JsonAssociationCounter(this);
    }


    @Override
    public String getId() {
        return this.id;
    }

    @Override
    public void setId(final String id) {
        this.id = id;
    }

    @Override
    public RootNode.Op getRootNode() {
        return artifactTreeRoot;
    }

    @Override
    public void setRootNode(final RootNode.Op root) {
        this.artifactTreeRoot = root;
        root.setContainingAssociation(this);
    }

    @Override
    public AssociationCounter getCounter() {
        return this.associationCounter;
    }

    @Override
    public Condition createCondition() {
        return new JsonCondition();
    }


    @Override
    public String toString() {
        return this.getAssociationString();
    }
}
