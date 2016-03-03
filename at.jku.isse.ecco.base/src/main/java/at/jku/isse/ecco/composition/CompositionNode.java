package at.jku.isse.ecco.composition;

import at.jku.isse.ecco.tree.BaseNode;
import at.jku.isse.ecco.tree.Node;

import java.util.ArrayList;
import java.util.List;

/**
 * A lazy composition node.
 */
public class CompositionNode extends BaseNode implements Node {

	private OrderSelector orderSelector;

	private boolean activated = false;

	private List<Node> origNodes;

	public CompositionNode() {
		this(null);
	}

	public CompositionNode(OrderSelector orderSelector) {
		this.origNodes = new ArrayList<>();
		this.activated = false;
		this.orderSelector = orderSelector;
	}

	public void addOrigNode(Node origNode) {
		this.origNodes.add(origNode);
	}


	private void activate() {
		if (this.activated)
			return;

		// compute the children of this node, but do not activate them!

		List<CompositionNode> allChildren = new ArrayList<>();

		for (Node origNode : this.origNodes) {
			for (Node origChildNode : origNode.getChildren()) {
				CompositionNode newChildNode = null;
				if (!allChildren.contains(origChildNode)) {
					newChildNode = new CompositionNode(this.orderSelector);

					newChildNode.setParent(this);
					newChildNode.setArtifact(origChildNode.getArtifact());
					newChildNode.setUnique(origChildNode.isUnique());

					newChildNode.addOrigNode(origChildNode);

					allChildren.add(newChildNode);
				} else {
					newChildNode = allChildren.get(allChildren.indexOf(origChildNode));
					newChildNode.addOrigNode(origChildNode);
				}
				if (origChildNode.isUnique()) {
					newChildNode.setUnique(true);
				}
			}
		}

		super.getChildren().addAll(allChildren);

		this.activated = true;

		if (this.orderSelector != null && this.getArtifact() != null && this.getArtifact().isOrdered() && this.getArtifact().isSequenced() && this.getArtifact().getSequenceGraph() != null) {
			this.orderSelector.select(this);
		}
	}


	@Override
	public List<Node> getChildren() {
		this.activate();

		return super.getChildren();
	}


	@Override
	public Node createNode() {
		return new CompositionNode();
	}

}
