package at.jku.isse.ecco.composition;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.tree.Node;

import java.util.Collection;
import java.util.List;

/**
 * Interface for order selection during the composition of artifact trees with ordered artifacts where the order of their children is ambiguous.
 */
public interface OrderSelector {

	/**
	 * Returns a collection of ordered artifacts for which multiple possible orders of children existed.
	 *
	 * @return The ordered artifacts with ambiguous order of children.
	 */
	public Collection<Artifact<?>> getUncertainOrders();

	/**
	 * If the node contains an ordered artifact with a sequence graph, the sequence graph is used to select and set the order of the node's children.
	 *
	 * @param node The node for which to select an order.
	 * @return The ordered list of children.
	 */
	public List<Node> select(Node node);

}
