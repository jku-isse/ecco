package at.jku.isse.ecco.storage.ser.maintree;

import at.jku.isse.ecco.featuretrace.ProactiveTraceCheck;
import at.jku.isse.ecco.maintree.retroactive.condition.setter.RetroactiveConditionSetterVisitor;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.dao.Persistable;
import at.jku.isse.ecco.maintree.building.AssociationMerger;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.util.Trees;

import java.util.Collection;
import java.util.Map;

public class SerAssociationMerger implements AssociationMerger, Persistable {

    private static final long serialVersionUID = -7935307738586109318L;

    @Override
    public Node.Op buildMainTree(Collection<Association.Op> associations) {
        Node.Op mergedTree = null;
        for (Association association : associations){
            mergedTree = Trees.treeFusion(mergedTree, (Node.Op) association.getRootNode());
        }
        return mergedTree;
    }

    @Override
    public Node.Op buildMainTree(Collection<Association.Op> associations, ProactiveTraceCheck traceCheck, Map<String, String> retroactiveConditions) {
        if (retroactiveConditions.isEmpty())
            return this.buildMainTree(associations, traceCheck);
        Node.Op mergedTree = null;
        for (Association association : associations){
            Node.Op tree = (Node.Op) association.getRootNode();
            String retroactiveCondition = retroactiveConditions.get(association.getId());
            if (retroactiveCondition != null) {
                // on a copy: the association itself keeps its own conditions
                tree = tree.copyTree(true);
                tree.traverse(new RetroactiveConditionSetterVisitor(retroactiveCondition));
            }
            mergedTree = Trees.treeFusion(mergedTree, tree);
        }
        return mergedTree;
    }
}
