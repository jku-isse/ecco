package at.jku.isse.ecco.composition;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.tree.BaseNode;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.tree.OrderedNode;

import java.util.ArrayList;
import java.util.List;

public class BaseCompNode extends BaseNode implements Node, CompNode {

	private boolean activated = false;

	private List<Node> origNodes;

	public BaseCompNode() {
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
					if (origChildNode instanceof OrderedNode) {
						newChildNode = new BaseCompOrderedNode();
						((OrderedNode) newChildNode).setSequenceGraph(((OrderedNode) origChildNode).getSequenceGraph());
					} else if (origChildNode instanceof Node) {
						newChildNode = new BaseCompNode();
					}

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

}
