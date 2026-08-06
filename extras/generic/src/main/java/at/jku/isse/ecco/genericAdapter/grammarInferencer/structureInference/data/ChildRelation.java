package at.jku.isse.ecco.genericAdapter.grammarInferencer.structureInference.data;

import java.util.Set;

/**
 * @author Michael Jahn
 */
public class ChildRelation {

    public void setChildNode(Node childNode) {
        this.childNode = childNode;
    }

    public boolean isHasFixedOrder() {
        return hasFixedOrder;
    }

    private Node childNode;

    // additional fields, required for base structure inference
    boolean hasFixedOrder = true;   // true if this node is a fixed order child of its parent
    boolean exactlyOnce = true;    // true if this node is allowed exactly once as a child of its parent


    public ChildRelation(Node childNode) {
        this.childNode = childNode;
    }

    public ChildRelation(Node childNode, boolean hasFixedOrder, boolean exactlyOnce) {
        this(childNode);
        this.hasFixedOrder = hasFixedOrder;
        this.exactlyOnce = exactlyOnce;
    }

    public boolean hasFixedOrder() {
        return hasFixedOrder;
    }

    public void setHasFixedOrder(boolean hasFixedOrder) {
        this.hasFixedOrder = hasFixedOrder;
    }

    public boolean isExactlyOnce() {
        return exactlyOnce;
    }

    public void setExactlyOnce(boolean exactlyOnce) {
        this.exactlyOnce = exactlyOnce;
    }

    public Node getChildNode() {
        return childNode;
    }

    public String subTreeToString(String indention, Set<ChildRelation> printedRelations) {
        if(!printedRelations.contains(this)) {
            printedRelations.add(this);
            StringBuilder childrenString = new StringBuilder();
            for (ChildRelation childRelation : childNode.getChildren()) {
                childrenString.append(childRelation.subTreeToString(indention + "  ", printedRelations));
            }

            return indention + "- " + childNode. getLabel() + "(Once: " + exactlyOnce + " Order: " + hasFixedOrder + ")\n" + childrenString;
        } else {
            return "";
        }
    }

    protected String subTreeToString(String indention, int maxDepth) {
        StringBuilder childrenString = new StringBuilder("");
        if (maxDepth > 0) {
            maxDepth--;
            for (ChildRelation childRelation : childNode.getChildren()) {
                childrenString.append(childRelation.subTreeToString(indention + "  ", maxDepth));
            }
        }
        return indention + "- " + childNode.getLabel() + "(C: " + exactlyOnce + " O: " + hasFixedOrder + ")\n" + childrenString;

    }

    public String getLabel() {
        return childNode.getLabel();
    }
}
