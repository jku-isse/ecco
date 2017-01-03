package at.jku.isse.ecco.composition;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.sg.SequenceGraph;

import java.util.*;

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
	public void select(at.jku.isse.ecco.tree.Node.Op node) {
		if (node.getArtifact() == null || !node.getArtifact().isOrdered() || !node.getArtifact().isSequenced() || node.getArtifact().getSequenceGraph() == null)
			return;

		List<at.jku.isse.ecco.tree.Node.Op> orderedChildren = new ArrayList<>();

		boolean uncertainOrder = this.traverseSequenceGraph(node.getArtifact().getSequenceGraph().getRoot(), node.getChildren(), orderedChildren);

		node.getChildren().clear();
		node.getChildren().addAll(orderedChildren);

		if (uncertainOrder)
			this.uncertainOrder.add(node.getArtifact());
	}


	/**
	 * Traverses the sequence graph of the ordered node to retrieve the first valid order it can find.
	 *
	 * @param sgn               The sequence graph to traverse.
	 * @param unorderedChildren The list of children of the ordered node without specific order (i.e. not yet ordered according to the sequence graph).
	 * @param orderedChildren   The same children, but now put in valid order.
	 * @return True if the order was ambiguous, false otherwise.
	 */
	private boolean traverseSequenceGraph(SequenceGraph.Node.Op sgn, List<at.jku.isse.ecco.tree.Node.Op> unorderedChildren, List<at.jku.isse.ecco.tree.Node.Op> orderedChildren) {
		boolean uncertainOrder = false;

		if (sgn.getChildren().isEmpty())
			return uncertainOrder;

		Map.Entry<Artifact.Op<?>, SequenceGraph.Node.Op> entry = sgn.getChildren().entrySet().iterator().next();

		at.jku.isse.ecco.tree.Node.Op match = null;
		Iterator<at.jku.isse.ecco.tree.Node.Op> iterator = unorderedChildren.iterator();
		while (iterator.hasNext()) {
			at.jku.isse.ecco.tree.Node.Op next = iterator.next();
			if (next.getArtifact().equals(entry.getKey())) {
				match = next;
				iterator.remove();
				break;
			}
		}
		if (match != null) {
			orderedChildren.add(match);

			// check if we would have other order options
			for (Artifact<?> key : sgn.getChildren().keySet()) {
				for (at.jku.isse.ecco.tree.Node node : unorderedChildren) {
					if (node.getArtifact() != null && node.getArtifact().equals(key)) {
						if (match != node)
							uncertainOrder = true;
						break;
					}
				}
				if (uncertainOrder)
					break;
			}
		}

		boolean childUncertainOrder = this.traverseSequenceGraph(entry.getValue(), unorderedChildren, orderedChildren);
		uncertainOrder = uncertainOrder || childUncertainOrder;

		return uncertainOrder;
	}


}
