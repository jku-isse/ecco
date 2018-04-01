package at.jku.isse.ecco.composition;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.tree.Node;
import org.eclipse.collections.impl.factory.Maps;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A lazy composition node.
 */
public class LazyCompositionNode implements Node {

	private boolean activated = false;

	private List<Node> origNodes;

	private OrderSelector orderSelector;


	private boolean unique = true;

	private Artifact<?> artifact = null;

	private Node parent = null;

	private final List<Node> children = new ArrayList<>();


	private transient Map<String, Object> properties;


	public LazyCompositionNode() {
		this(null);
	}

	public LazyCompositionNode(OrderSelector orderSelector) {
		checkNotNull(orderSelector);
		this.activated = false;
		this.origNodes = new ArrayList<>();
		this.orderSelector = orderSelector;
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

					//newChildNode.setParent(this);
					newChildNode.parent = this;
					//newChildNode.setArtifact(origChildNode.getArtifact());
					newChildNode.artifact = origChildNode.getArtifact();
					//newChildNode.setUnique(origChildNode.isUnique());
					newChildNode.unique = origChildNode.isUnique();

					newChildNode.addOrigNode(origChildNode);

					allChildren.add(newChildNode);
				} else {
					newChildNode = allChildren.get(allChildren.indexOf(origChildNode));
					newChildNode.addOrigNode(origChildNode);
				}
				if (origChildNode.isUnique()) {
					//newChildNode.setUnique(true);
					newChildNode.unique = true;
				}
			}
		}

		this.children.addAll(allChildren);

		this.activated = true;

		// finally set the order of the children
		if (this.orderSelector != null && this.getArtifact() != null && this.getArtifact().isOrdered() && this.getArtifact().isSequenced() && this.getArtifact().getSequenceGraph() != null) {
			List<Node> orderedChildren = this.orderSelector.select(this);
			this.children.clear();
			this.children.addAll(orderedChildren);
		}
	}


	public void addOrigNode(Node origNode) {
		this.origNodes.add(origNode);
	}


	public OrderSelector getOrderSelector() {
		return orderSelector;
	}

	public void setOrderSelector(OrderSelector orderSelector) {
		this.orderSelector = orderSelector;
	}


	@Override
	public boolean isAtomic() {
		if (this.artifact != null)
			return this.artifact.isAtomic();
		else
			return false;
	}

	@Override
	public boolean isUnique() {
		return this.unique;
	}

	@Override
	public Artifact<?> getArtifact() {
		return artifact;
	}

	@Override
	public Node getParent() {
		return parent;
	}

	@Override
	public List<? extends Node> getChildren() {
		this.activate();

		//return Collections.unmodifiableList(this.children);
		return this.children;
	}

	@Override
	public Association getContainingAssociation() {
		if (this.parent == null)
			return null;
		else
			return this.parent.getContainingAssociation();
	}


	@Override
	public Map<String, Object> getProperties() {
		if (this.properties == null)
			this.properties = Maps.mutable.empty();
		return this.properties;
	}


	@Override
	public int hashCode() {
		return this.getArtifact() != null ? this.getArtifact().hashCode() : 0;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) return true;
		if (other == null) return false;
		if (!(other instanceof Node)) return false;

		Node otherNode = (Node) other;

		if (this.getArtifact() == null)
			return otherNode.getArtifact() == null;

		return this.getArtifact().equals(otherNode.getArtifact());
	}


	@Override
	public String toString() {
		return this.getNodeString();
	}

}
