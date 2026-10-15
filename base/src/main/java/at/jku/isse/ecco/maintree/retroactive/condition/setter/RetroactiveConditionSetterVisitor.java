package at.jku.isse.ecco.maintree.retroactive.condition.setter;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.tree.RootNode;

public class RetroactiveConditionSetterVisitor implements Node.Op.NodeVisitor{

    String retroactiveConditionString;
    private boolean changed;

    public RetroactiveConditionSetterVisitor(String retroactiveConditionString){
        this.retroactiveConditionString = retroactiveConditionString;
    }

    public RetroactiveConditionSetterVisitor(Association association){
        this.retroactiveConditionString = association.computeCondition().toLogicString();
    }

    @Override
    public void visit(Node.Op node) {
        if (node instanceof RootNode){ return; }
        // a node without a feature trace has nothing to set - skipped like in SerNode's own trace
        // methods and BoostVisitor (this used to NPE and fail the commit)
        if (node.isUnique() && node.getFeatureTrace() != null){
            if (!retroactiveConditionString.equals(node.getFeatureTrace().getRetroactiveConditionString()))
                changed = true;
            node.getFeatureTrace().setRetroactiveCondition(retroactiveConditionString);
        }
    }

    /** Whether a node got a condition other than the one it had - its association must be written. */
    public boolean changedAny() {
        return changed;
    }
}
