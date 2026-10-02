package at.jku.isse.ecco.storage.ser.maintree;

import at.jku.isse.ecco.maintree.retroactive.condition.setter.RetroactiveConditionSetterVisitor;
import at.jku.isse.ecco.featuretrace.ProactiveTraceCheck;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.dao.Persistable;
import at.jku.isse.ecco.maintree.building.BoostConditionVisitor;
import at.jku.isse.ecco.maintree.building.BoostVisitor;
import at.jku.isse.ecco.maintree.building.BoostedAssociationMerger;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.util.Trees;

import java.util.Collection;
import java.util.Map;
import java.util.logging.Logger;

public class SerBoostedAssociationMerger implements BoostedAssociationMerger, Persistable {

    private static final long serialVersionUID = 7423817316956145102L;

    private static final Logger LOGGER = Logger.getLogger(SerBoostedAssociationMerger.class.getName());

    @Override
    public Node.Op buildMainTree(Collection<Association.Op> associations) {
        return this.buildMainTree(associations, null);
    }

    @Override
    public Node.Op buildMainTree(Collection<Association.Op> associations, ProactiveTraceCheck traceCheck) {
        return this.buildMainTree(associations, traceCheck, Map.of());
    }

    @Override
    public Node.Op buildMainTree(Collection<Association.Op> associations, ProactiveTraceCheck traceCheck, Map<String, String> retroactiveConditions) {
        Node.Op mergedTree = null;
        for (Association association : associations){
            Node.Op boostedAssociationTree = this.createBoostedAssociationTree(association, traceCheck, retroactiveConditions.get(association.getId()));
            mergedTree = Trees.treeFusion(mergedTree, boostedAssociationTree);
        }
        return mergedTree;
    }

    private Node.Op createBoostedAssociationTree(Association association, ProactiveTraceCheck traceCheck, String retroactiveCondition){
        Node.Op associationTree = (Node.Op) association.getRootNode();
        Node.Op associationTreeCopy = associationTree.copyTree(true);

        // checking out with a minimized condition: on the copy only, like the boost below
        if (retroactiveCondition != null){
            associationTreeCopy.traverse(new RetroactiveConditionSetterVisitor(retroactiveCondition));
        }

        // traces that contradict the commit history are dropped before they can be boosted
        if (traceCheck != null){
            traceCheck.check(association, associationTreeCopy);
        }

        BoostConditionVisitor boostConditionVisitor = new BoostConditionVisitor();
        associationTreeCopy.traverse(boostConditionVisitor);

        boolean boostPossible = boostConditionVisitor.isBoostPossible();
        LOGGER.fine("Association " + association + " can be boosted: " + boostPossible);

        if (boostPossible){
            BoostVisitor boostVisitor = new BoostVisitor(boostConditionVisitor.getBoostCondition());
            associationTreeCopy.traverse(boostVisitor);
        }

        return associationTreeCopy;
    }
}

