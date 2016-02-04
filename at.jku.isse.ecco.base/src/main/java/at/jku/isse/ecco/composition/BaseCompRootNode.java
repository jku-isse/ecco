package at.jku.isse.ecco.composition;

import at.jku.isse.ecco.tree.BaseRootNode;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.tree.RootNode;

import java.util.ArrayList;
import java.util.List;

public class BaseCompRootNode extends BaseRootNode implements RootNode, CompNode {

	private boolean activated = false;

	private List<Node> origNodes;

	public BaseCompRootNode() {
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

		List<CompNode> allChildren = new ArrayList<>();

		for (Node origNode : this.origNodes) {
			for (Node origChildNode : origNode.getChildren()) {
				CompNode newChildNode = null;
				if (!allChildren.contains(origChildNode)) {
					newChildNode = new BaseCompNode();

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
	}


	@Override
	public List<Node> getChildren() {
		this.activate();

		return super.getChildren();
	}

}
