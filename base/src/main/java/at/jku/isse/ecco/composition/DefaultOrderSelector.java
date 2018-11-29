package at.jku.isse.ecco.composition;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.pog.PartialOrderGraph;

import java.util.*;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * An order selector that selects the first order of artifacts it encounters.
 */
public class DefaultOrderSelector implements OrderSelector {

	private Collection<Artifact<?>> uncertainOrder = new ArrayList<>();

	/**
	 * Returns a collection of ordered artifacts for which multiple possible orders of children existed and an arbitrary one was selected.
	 *
	 * @return The ordered artifacts with ambiguous order of children.
	 */
	@Override
	public Collection<Artifact<?>> getUncertainOrders() {
		return this.uncertainOrder;
	}


	/**
	 * Selects and sets the first valid order that is found.
	 *
	 * @param node The node for which to select an order.
	 */
	@Override
	public List<at.jku.isse.ecco.tree.Node> select(at.jku.isse.ecco.tree.Node node) {
		checkArgument(node.getArtifact() != null, "Cannot select order for node without artifact.");
		checkArgument(node.getArtifact().isOrdered(), "Cannot select order for node with unordered artifact.");
		checkArgument(node.getArtifact().isSequenced(), "Cannot select order for node with ordered artifact that has not been sequenced yet.");
		checkArgument(node.getArtifact().getSequenceGraph() != null, "Cannot select order for node with ordered artifact that has no sequence graph.");
//		if (node.getArtifact() == null || !node.getArtifact().isOrdered() || !node.getArtifact().isSequenced() || node.getArtifact().getSequenceGraph() == null)
//			return null;

		List<at.jku.isse.ecco.tree.Node> orderedChildren = new ArrayList<>();
		PartialOrderGraph graph = node.getArtifact().getSequenceGraph();
		boolean uncertainOrder = false;

		Map<PartialOrderGraph.Node, Integer> pogNodes = new HashMap<>();
		pogNodes.put(graph.getHead(), 0);

		// for every node in start match state ...
		while (!pogNodes.isEmpty()) {
			Map.Entry<PartialOrderGraph.Node, Integer> entry = pogNodes.entrySet().iterator().next();
			PartialOrderGraph.Node pogNode = entry.getKey();

			// check if all parents of the node have been processed
			if (pogNodes.get(pogNode) >= pogNode.getPrevious().size()) {
				// ... process the node ...

				// check if order is ambiguous
				if (pogNode.getNext().size() > 1)
					uncertainOrder = true;

				// check if node is in input
				for (at.jku.isse.ecco.tree.Node childNode : node.getChildren()) {
					if (childNode.getArtifact().equals(pogNode.getArtifact())) {
						// add node to order
						orderedChildren.add(childNode);
						break;
					}
				}

				// remove current node and all its parent nodes from match state
				pogNodes.remove(pogNode);
				// add children of current node to match state
				for (PartialOrderGraph.Node child : pogNode.getNext()) {
					pogNodes.putIfAbsent(child, 0);
					pogNodes.computeIfPresent(child, (op, integer) -> integer + 1);
				}
			}
		}

		if (uncertainOrder)
			this.uncertainOrder.add(node.getArtifact());

		return orderedChildren;
	}

}
