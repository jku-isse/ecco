package at.jku.isse.ecco.util;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.module.PresenceCondition;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.tree.RootNode;

/**
 * This static class provides a collection of association utility functions.
 */
public class Associations {

	private Associations() {
	}


	public static Association copy(Association parentAssociation, EntityFactory entityFactory) {
		Association childAssociation = entityFactory.createAssociation();

		// set presence condition
		PresenceCondition childPresenceCondition = entityFactory.createPresenceCondition();
		childAssociation.setPresenceCondition(childPresenceCondition);
		// TODO: clone pc
		PresenceConditions.copy(parentAssociation.getPresenceCondition(), childPresenceCondition);


		// set artifact tree
		RootNode childRootNode = entityFactory.createRootNode();
		childAssociation.setRootNode(childRootNode);
		// clone tree
		for (Node parentChildNode : parentAssociation.getRootNode().getChildren()) {
			Node childChildNode = Trees.copy(parentChildNode, entityFactory);
			childRootNode.addChild(childChildNode);
			childChildNode.setParent(childRootNode);
		}
		Trees.checkConsistency(childRootNode);

		return childAssociation;
	}

}
