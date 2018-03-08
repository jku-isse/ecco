package at.jku.isse.ecco.composition;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.sg.SequenceGraph;

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

		boolean uncertainOrder = this.traverseSequenceGraph(node.getArtifact().getSequenceGraph().getRoot(), node.getChildren(), orderedChildren);

		if (uncertainOrder)
			this.uncertainOrder.add(node.getArtifact());

		return orderedChildren;
	}


	/**
	 * Traverses the sequence graph of the ordered node to retrieve the first valid order it can find.
	 *
	 * @param sgn               The sequence graph to traverse.
	 * @param unorderedChildren The list of children of the ordered node without specific order (i.e. not yet ordered according to the sequence graph).
	 * @param orderedChildren   The same children, but now put in valid order.
	 * @return True if the order was ambiguous, false otherwise.
	 */
	private boolean traverseSequenceGraph(SequenceGraph.Node sgn, List<? extends at.jku.isse.ecco.tree.Node> unorderedChildren, List<at.jku.isse.ecco.tree.Node> orderedChildren) {
		boolean uncertainOrder = false;

		if (sgn.getChildren().isEmpty())
			return false;

		Map.Entry<? extends Artifact.Op<?>, ? extends SequenceGraph.Node> entry = sgn.getChildren().entrySet().iterator().next();

		at.jku.isse.ecco.tree.Node match = null;
		Iterator<? extends at.jku.isse.ecco.tree.Node> iterator = unorderedChildren.iterator();
		while (iterator.hasNext()) {
			at.jku.isse.ecco.tree.Node next = iterator.next();
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
