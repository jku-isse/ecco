package at.jku.isse.ecco.web.domain.model;

public class ArtefactTreeModel {

    private ArtefactTreeNodeModel rootNode;

    public ArtefactTreeModel() {

    }

    public ArtefactTreeModel(ArtefactTreeNodeModel givenRootNode) {
        this.rootNode = givenRootNode;
    }

    public ArtefactTreeNodeModel getRootNode() {
        return rootNode;
    }

    public void setRootNode(ArtefactTreeNodeModel rootNode) {
        this.rootNode = rootNode;
    }
}
