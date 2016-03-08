package at.jku.isse.ecco.composition;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.tree.Node;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A lazy composition node.
 */
public class LazyCompositionNode implements Node {

	private OrderSelector orderSelector;

	private boolean activated = false;

	private List<Node> origNodes;

	public LazyCompositionNode() {
		this(null);
	}

	public LazyCompositionNode(OrderSelector orderSelector) {
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

		List<LazyCompositionNode> allChildren = new ArrayList<>();

		for (Node origNode : this.origNodes) {
			for (Node origChildNode : origNode.getChildren()) {
				LazyCompositionNode newChildNode = null;
				if (!allChildren.contains(origChildNode)) {
					newChildNode = new LazyCompositionNode(this.orderSelector);

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

		this.children.addAll(allChildren);

		this.activated = true;

		if (this.orderSelector != null && this.getArtifact() != null && this.getArtifact().isOrdered() && this.getArtifact().isSequenced() && this.getArtifact().getSequenceGraph() != null) {
			this.orderSelector.select(this);
		}
	}


	@Override
	public List<Node> getChildren() {
		this.activate();

		return this.children;
	}


	@Override
	public Node createNode() {
		return new LazyCompositionNode();
	}


	// base


	private boolean unique = true;

	private final List<Node> children = new ArrayList<>();

	private Artifact artifact = null;

	private Node parent = null;


	@Override
	public boolean isAtomic() {
		if (this.artifact != null)
			return this.artifact.isAtomic();
		else
			return false;
	}


	@Override
	public Association getContainingAssociation() {
		if (this.parent == null)
			return null;
		else
			return this.parent.getContainingAssociation();
	}


	@Override
	public Artifact getArtifact() {
		return artifact;
	}

	@Override
	public void setArtifact(Artifact artifact) {
		this.artifact = artifact;
	}

	@Override
	public Node getParent() {
		return parent;
	}

	@Override
	public void setParent(Node parent) {
		this.parent = parent;
	}

	@Override
	public boolean isUnique() {
		return this.unique;
	}

	@Override
	public void setUnique(boolean unique) {
		this.unique = unique;
	}


	@Override
	public void addChild(Node child) {
		checkNotNull(child);

		this.children.add(child);
		child.setParent(this);
	}

	@Override
	public void removeChild(Node child) {
		checkNotNull(child);

		this.children.remove(child);
	}


	@Override
	public int hashCode() {
		return artifact != null ? artifact.hashCode() : 0;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null) return false;
		if (!(o instanceof Node)) return false;

		Node baseNode = (Node) o;

		if (artifact == null)
			return baseNode.getArtifact() == null;

		return artifact.equals(baseNode.getArtifact());
	}

	@Override
	public String toString() {
		return this.getArtifact().toString();
	}


}
