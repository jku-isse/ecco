package at.jku.isse.ecco.tree;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.core.Association;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

public class BaseNode implements Node {

	private boolean unique = true;

	private final List<Node> children = new ArrayList<>();

	private Artifact artifact = null;

	private Node parent = null;


	@Override
	public Node createNode() {
		return new BaseNode();
	}


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
	public List<Node> getChildren() {
		return this.children;
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

		BaseNode baseNode = (BaseNode) o;

		if (artifact == null)
			return baseNode.artifact == null;

		return artifact.equals(baseNode.artifact);
	}

	@Override
	public String toString() {
		return this.getArtifact().toString();
	}

}
