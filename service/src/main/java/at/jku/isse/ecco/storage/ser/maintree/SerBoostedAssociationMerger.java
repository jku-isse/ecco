package at.jku.isse.ecco.storage.ser.maintree;

import at.jku.isse.ecco.featuretrace.ProactiveTraceCheck;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.dao.Persistable;
import at.jku.isse.ecco.maintree.building.BoostConditionVisitor;
import at.jku.isse.ecco.maintree.building.BoostVisitor;
import at.jku.isse.ecco.maintree.building.BoostedAssociationMerger;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.util.Trees;

import java.util.Collection;
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
        Node.Op mergedTree = null;
        for (Association association : associations){
            Node.Op boostedAssociationTree = this.createBoostedAssociationTree(association, traceCheck);
            mergedTree = Trees.treeFusion(mergedTree, boostedAssociationTree);
        }
        return mergedTree;
    }

    private Node.Op createBoostedAssociationTree(Association association, ProactiveTraceCheck traceCheck){
        Node.Op associationTree = (Node.Op) association.getRootNode();
        Node.Op associationTreeCopy = associationTree.copyTree(true);

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

