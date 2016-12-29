package at.jku.isse.ecco.composition;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.sg.SequenceGraph;

import java.util.*;

/**
 * An order selector that selects an arbitrary order of artifacts.
 */
public class DefaultOrderSelector implements OrderSelector {

	private Collection<Artifact<?>> uncertainOrder = new ArrayList<>();

	@Override
	public Collection<Artifact<?>> getUncertainOrders() {
		return this.uncertainOrder;
	}


	/**
	 * Selects and sets the first valid order that was found.
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
