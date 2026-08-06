package at.jku.isse.ecco.storage.neo4j.test.domain;

import org.neo4j.ogm.annotation.Relationship;

import java.util.ArrayList;

public class TreeNode extends NeoEntity{

    public TreeNode() {}

    @Relationship(type = "hasChildrenNd", direction = Relationship.INCOMING)
    private ArrayList<TreeNode> children = new ArrayList<>();


    @Relationship(type = "hasChildrenNd")
    private TreeNode parent;


    public TreeNode getParent() {
        return parent;
    }

    public void setParent(TreeNode parent) {
        this.parent = parent;
    }


    public void addChild(TreeNode child) {
        this.children.add(child);
        child.setParent(this);
    }

    public ArrayList<TreeNode> getChildren() {
        return children;
    }

    public void setChildren(ArrayList<TreeNode> children) {
        this.children = children;
    }
}
