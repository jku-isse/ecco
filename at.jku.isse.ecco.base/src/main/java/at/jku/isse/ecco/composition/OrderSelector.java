package at.jku.isse.ecco.composition;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.tree.Node;

import java.util.Collection;

public interface OrderSelector {

	public Collection<Artifact<?>> getUncertainOrders();

	/**
	 * If the node contains an ordered artifact with a sequence graph, the sequence graph is used to select and set the order of the node's children.
	 *
	 * @param node The node for which to select an order.
	 */
	public void select(Node node);

}
