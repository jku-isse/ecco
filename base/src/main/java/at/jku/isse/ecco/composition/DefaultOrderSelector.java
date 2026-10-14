package at.jku.isse.ecco.composition;

import at.jku.isse.ecco.pog.PartialOrderGraph;
import at.jku.isse.ecco.tree.Node;

import java.util.*;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * An order selector that selects the first order of artifacts it encounters.
 */
public class DefaultOrderSelector implements OrderSelector {

	private Collection<Node> uncertainOrder = new ArrayList<>();

	/**
	 * Returns a collection of nodes whose ordered artifact had multiple possible orders of children and an arbitrary one was selected.
	 *
	 * @return The nodes with ambiguous order of children.
	 */
	@Override
	public Collection<Node> getUncertainOrders() {
		return this.uncertainOrder;
	}


	/**
	 * Selects and sets the first valid order that is found.
	 *
	 * @param node The node for which to select an order.
	 */
	@Override
	public List<Node> select(Node node) {
		checkArgument(node.getArtifact() != null, "Cannot select order for node without artifact.");
		checkArgument(node.getArtifact().isOrdered(), "Cannot select order for node with unordered artifact.");
		checkArgument(node.getArtifact().isSequenced(), "Cannot select order for node with ordered artifact that has not been sequenced yet.");
		checkArgument(node.getArtifact().getPartialOrderGraph() != null, "Cannot select order for node with ordered artifact that has no sequence graph.");
//		if (node.getArtifact() == null || !node.getArtifact().isOrdered() || !node.getArtifact().isSequenced() || node.getArtifact().getPartialOrderGraph() == null)
//			return null;

		List<Node> orderedChildren = new ArrayList<>();
		PartialOrderGraph graph = node.getArtifact().getPartialOrderGraph();
		boolean uncertainOrder = false;

		// the children by their artifact's hash, in order: an order graph node's child is the first
		// child whose artifact equals the node's, and equal artifacts have equal hashes. Scanning all
		// children for every node was quadratic in a file's lines - a checkout of a 340,000-line C
		// file took 19 minutes. Within a group the comparison is the same as before: the artifacts'
		// equals() (which also tells apart equal lines by their sequence numbers) decides.
		Map<Integer, List<Node>> childrenByHash = new HashMap<>();
		for (Node childNode : node.getChildren())
			childrenByHash.computeIfAbsent(childNode.getArtifact().hashCode(), hash -> new ArrayList<>()).add(childNode);

		Map<PartialOrderGraph.Node, Integer> pogNodesCounter = new HashMap<>();
		Stack<PartialOrderGraph.Node> stack = new Stack<>();
		stack.push(graph.getHead());

		// for every node in start match state ...
		while (!stack.isEmpty()) {
			PartialOrderGraph.Node pogNode = stack.pop();

			// check if order is ambiguous
			if (pogNode.getNext().size() > 1)
				uncertainOrder = true;

			// check if node is in input
			for (Node childNode : childrenByHash.getOrDefault(pogNode.getArtifact() == null ? 0 : pogNode.getArtifact().hashCode(), List.of())) {
				if (childNode.getArtifact().equals(pogNode.getArtifact())) {
					// add node to order
					orderedChildren.add(childNode);
					break;
				}
			}

			// add children of current node to match state
			for (PartialOrderGraph.Node child : pogNode.getNext()) {
				pogNodesCounter.putIfAbsent(child, 0);
				int counter = pogNodesCounter.computeIfPresent(child, (op, integer) -> integer + 1);
				// check if all parents of the node have been processed
				if (counter >= child.getPrevious().size()) {
					// remove node from counters
					pogNodesCounter.remove(child);
					// push node onto stack
					stack.push(child);
				}
			}
		}

		if (uncertainOrder)
			this.uncertainOrder.add(node);

		return orderedChildren;
	}

}
