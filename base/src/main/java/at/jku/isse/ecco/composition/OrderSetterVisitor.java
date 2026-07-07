package at.jku.isse.ecco.composition;

import at.jku.isse.ecco.tree.Node;

import java.util.List;

public class OrderSetterVisitor implements Node.Op.NodeVisitor{

    private final OrderSelector orderSelector;

    public OrderSetterVisitor(OrderSelector orderSelector){
        this.orderSelector = orderSelector;
    }

    @Override
    public void visit(Node.Op node) {
        if (node.getArtifact() != null && node.getArtifact().isOrdered() && node.getArtifact().isSequenced() && node.getArtifact().getPartialOrderGraph() != null) {
            List<Node.Op> orderedChildren = this.orderSelector.select(node).stream().map(n -> (Node.Op) n).toList();
            node.setChildren(orderedChildren);
        }
    }
}
