package at.jku.isse.ecco.composition;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.tree.BaseOrderedNode;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.tree.OrderedNode;

import java.util.ArrayList;
import java.util.List;

// TODO: order resolve strategy! default strategy simply picks a random / the first order and stores it in the ordered children.

public class BaseCompOrderedNode extends BaseOrderedNode implements OrderedNode, CompNode {

	private boolean activated = false;

	private List<Node> origNodes;

	public BaseCompOrderedNode() {
		this.origNodes = new ArrayList<>();
		this.activated = false;
	}

	@Override
	public void addOrigNode(Node origNode) {
		this.origNodes.add(origNode);
	}


	private void activate() {
		if (this.activated)
			return;

		// compute the children of this node, but do not activate them!

		List<CompNode> uniqueChildren = new ArrayList<>();
		List<CompNode> allChildren = new ArrayList<>();

		for (Node origNode : this.origNodes) {
			for (Node origChildNode : origNode.getAllChildren()) {
				if (!allChildren.contains(origChildNode)) {
					CompNode newChildNode = null;
					if (origChildNode instanceof OrderedNode)
						newChildNode = new BaseCompOrderedNode();
					else if (origChildNode instanceof Node)
						newChildNode = new BaseCompNode();

					newChildNode.setParent(this);
					newChildNode.setSequenceNumber(origChildNode.getSequenceNumber());
					newChildNode.setAtomic(origChildNode.isAtomic());
					newChildNode.setArtifact(origChildNode.getArtifact());

					newChildNode.addOrigNode(origChildNode);

					allChildren.add(newChildNode);
				} else {
					CompNode newChildNode = allChildren.get(allChildren.indexOf(origChildNode));
					newChildNode.addOrigNode(origChildNode);
				}
				if (origChildNode.isUnique() && !uniqueChildren.contains(origChildNode)) {
					uniqueChildren.add(allChildren.get(allChildren.indexOf(origChildNode)));
				}
			}
		}

		super.getAllChildren().addAll(allChildren);
		super.getUniqueChildren().addAll(uniqueChildren);

		this.activated = true;

		// now that we have the children, pick an order from the sequence graph and store them in the ordered children

		// TODO: this is a dummy implementation. use a strategy pattern for this!
		super.getOrderedChildren().addAll(allChildren);
		super.setSequenced(true);
	}


	@Override
	public Node slice(Node other) throws EccoException {
		throw new EccoException("Not supported!"); // TODO: another reason to move the slice operation out of the tree nodes
	}


	@Override
	public List<Node> getAllChildren() {
		this.activate();

		return super.getAllChildren();
	}

	@Override
	public List<Node> getUniqueChildren() {
		this.activate();

		return super.getUniqueChildren();
	}

	@Override
	public List<Node> getOrderedChildren() {
		this.activate();

		return super.getOrderedChildren();
	}

}
