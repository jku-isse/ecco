package at.jku.isse.ecco.maintree.retroactive.condition.setter;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.tree.RootNode;

public class RetroactiveConditionSetterVisitor implements Node.Op.NodeVisitor{

    String retroactiveConditionString;

    public RetroactiveConditionSetterVisitor(String retroactiveConditionString){
        this.retroactiveConditionString = retroactiveConditionString;
    }

    public RetroactiveConditionSetterVisitor(Association association){
        this.retroactiveConditionString = association.computeCondition().toLogicString();
    }

    @Override
    public void visit(Node.Op node) {
        if (node instanceof RootNode){ return; }
        if (node.isUnique()){
            node.getFeatureTrace().setRetroactiveCondition(retroactiveConditionString);
        }
    }
}
