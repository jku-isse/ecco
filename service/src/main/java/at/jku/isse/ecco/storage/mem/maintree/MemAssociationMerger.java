package at.jku.isse.ecco.storage.mem.maintree;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.dao.Persistable;
import at.jku.isse.ecco.maintree.building.AssociationMerger;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.util.Trees;

import java.util.Collection;

public class MemAssociationMerger implements AssociationMerger, Persistable {

    @Override
    public Node.Op buildMainTree(Collection<Association.Op> associations) {
        Node.Op mergedTree = null;
        for (Association association : associations){
            mergedTree = Trees.treeFusion(mergedTree, (Node.Op) association.getRootNode());
        }
        return mergedTree;
    }
}
