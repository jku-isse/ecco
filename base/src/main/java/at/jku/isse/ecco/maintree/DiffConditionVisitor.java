package at.jku.isse.ecco.maintree;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.tree.RootNode;

public class DiffConditionVisitor implements Node.Op.NodeVisitor{

    String diffConditionString;

    public DiffConditionVisitor(String diffConditionString){
        this.diffConditionString = diffConditionString;
    }

    public DiffConditionVisitor(Association association){
        this.diffConditionString = association.computeCondition().toLogicString();
    }

    @Override
    public void visit(Node.Op node) {
        if (node instanceof RootNode){ return; }
        if (node.isUnique()){
            node.getFeatureTrace().setDiffCondition(diffConditionString);
        }
    }
}
