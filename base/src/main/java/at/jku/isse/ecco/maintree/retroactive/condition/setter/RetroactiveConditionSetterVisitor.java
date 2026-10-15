package at.jku.isse.ecco.maintree.retroactive.condition.setter;

import at.jku.isse.ecco.logic.LogicCondition;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.tree.RootNode;

public class RetroactiveConditionSetterVisitor implements Node.Op.NodeVisitor{

    private final LogicCondition retroactiveCondition;
    private boolean changed;

    public RetroactiveConditionSetterVisitor(LogicCondition retroactiveCondition){
        this.retroactiveCondition = retroactiveCondition;
    }

    public RetroactiveConditionSetterVisitor(Association association){
        this.retroactiveCondition = association.computeCondition().toLogicCondition();
    }

    @Override
    public void visit(Node.Op node) {
        if (node instanceof RootNode){ return; }
        // a node without a feature trace has nothing to set - skipped like in SerNode's own trace
        // methods and BoostVisitor (this used to NPE and fail the commit)
        if (node.isUnique() && node.getFeatureTrace() != null){
            if (!retroactiveCondition.equals(node.getFeatureTrace().getRetroactiveCondition()))
                changed = true;
            node.getFeatureTrace().setRetroactiveCondition(retroactiveCondition);
        }
    }

    /** Whether a node got a condition other than the one it had - its association must be written. */
    public boolean changedAny() {
        return changed;
    }
}
