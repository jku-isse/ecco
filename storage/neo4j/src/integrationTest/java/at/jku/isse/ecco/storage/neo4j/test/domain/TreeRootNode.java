package at.jku.isse.ecco.storage.neo4j.test.domain;

import org.neo4j.ogm.annotation.Relationship;

public class TreeRootNode extends TreeNode {


    public TreeRootNode() {}

    public TreeRootNode(BaseContainer containingRepo) {
        this.containingRepo = containingRepo;
    }

    @Relationship(type = "containingRepo")
    public BaseContainer containingRepo;

    public BaseContainer getContainingRepo() {
        return containingRepo;
    }

    public void setContainingRepo(BaseContainer containingRepo) {
        this.containingRepo = containingRepo;
    }
}
