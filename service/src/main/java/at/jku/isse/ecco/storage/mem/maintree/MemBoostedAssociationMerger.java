package at.jku.isse.ecco.storage.mem.maintree;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.dao.Persistable;
import at.jku.isse.ecco.maintree.BoostConditionVisitor;
import at.jku.isse.ecco.maintree.BoostVisitor;
import at.jku.isse.ecco.maintree.BoostedAssociationMerger;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.util.Trees;

import java.util.Collection;

public class MemBoostedAssociationMerger implements BoostedAssociationMerger, Persistable {

    @Override
    public Node.Op buildMainTree(Collection<Association.Op> associations) {
        Node.Op mergedTree = null;
        for (Association association : associations){
            Node.Op boostedAssociationTree = this.createBoostedAssociationTree(association);
            mergedTree = Trees.treeFusion(mergedTree, boostedAssociationTree);
        }
        return mergedTree;
    }

    private Node.Op createBoostedAssociationTree(Association association){
        Node.Op associationTree = (Node.Op) association.getRootNode();
        Node.Op associationTreeCopy = associationTree.copyTree(true);

        BoostConditionVisitor boostConditionVisitor = new BoostConditionVisitor();
        associationTreeCopy.traverse(boostConditionVisitor);
        if (boostConditionVisitor.isBoostPossible()){
            BoostVisitor boostVisitor = new BoostVisitor(boostConditionVisitor.getBoostCondition());
            associationTreeCopy.traverse(boostVisitor);
        }

        return associationTreeCopy;
    }
}
