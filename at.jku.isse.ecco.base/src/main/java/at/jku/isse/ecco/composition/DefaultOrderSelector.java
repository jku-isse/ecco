package at.jku.isse.ecco.composition;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.sequenceGraph.SequenceGraphNode;
import at.jku.isse.ecco.tree.Node;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class DefaultOrderSelector implements OrderSelector {

	/**
	 * Selects and sets the first valid order that was found.
	 *
	 * @param node The node for which to select an order.
	 */
	@Override
	public void select(Node node) {
		if (node.getArtifact() == null || !node.getArtifact().isOrdered() || !node.getArtifact().isSequenced() || node.getArtifact().getSequenceGraph() == null)
			return;

		List<Node> orderedChildren = new ArrayList<Node>();

		this.traverseSequenceGraph(node.getArtifact().getSequenceGraph().getRoot(), node.getChildren(), orderedChildren);

		node.getChildren().clear();
		node.getChildren().addAll(orderedChildren);
	}


	private void traverseSequenceGraph(SequenceGraphNode sgn, List<Node> unorderedChildren, List<Node> orderedChildren) {
		if (sgn.getChildren().isEmpty())
			return;

		Map.Entry<Artifact<?>, SequenceGraphNode> entry = sgn.getChildren().entrySet().iterator().next();

		Node match = null;
		Iterator<Node> iterator = unorderedChildren.iterator();
		while (iterator.hasNext()) {
			Node next = iterator.next();
			if (next.getArtifact().equals(entry.getKey())) {
				match = next;
				iterator.remove();
				break;
			}
		}
		if (match != null) {
			orderedChildren.add(match);
		}

		this.traverseSequenceGraph(entry.getValue(), unorderedChildren, orderedChildren);
	}


}
