package at.jku.isse.ecco.storage.perst.tree;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.tree.Node;
import org.garret.perst.Persistent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A perst implementation of the node. When recursive loading for this node is disabled it will be loaded on demand as soon as any of its object members is accessed.
 */
public class PerstNode extends Persistent implements Node, Node.Op {

	private boolean unique = true;

	private final List<Op> children = new ArrayList<>();

	private Artifact.Op<?> artifact = null;

	private Op parent = null;


	public PerstNode() {
	}

	public PerstNode(Artifact.Op<?> artifact) {
		this.artifact = artifact;
	}


	@Override
	public Op createNode() {
		return new PerstNode();
	}


	@Override
	public boolean isAtomic() {
		this.load();
		if (this.artifact != null)
			return this.artifact.isAtomic();
		else
			return false;
	}


	@Override
	public Association getContainingAssociation() {
		this.load();
		if (this.parent == null)
			return null;
		else
			return this.parent.getContainingAssociation();
	}


	@Override
	public Artifact.Op<?> getArtifact() {
		this.load();
		return artifact;
	}

	@Override
	public void setArtifact(Artifact.Op<?> artifact) {
		this.load();
		this.artifact = artifact;
	}

	@Override
	public Op getParent() {
		this.load();
		return parent;
	}

	@Override
	public void setParent(Op parent) {
		this.load();
		this.parent = parent;
	}

	@Override
	public boolean isUnique() {
		this.load();
		return this.unique;
	}

	@Override
	public void setUnique(boolean unique) {
		this.load();
		this.unique = unique;
	}


	@Override
	public void addChild(Op child) {
		checkNotNull(child);

		this.load();

		if (this.getArtifact() != null && !this.getArtifact().isOrdered() && this.children.contains(child))
			throw new EccoException("An equivalent child is already contained. If multiple equivalent children are allowed use an ordered node.");

		this.children.add(child);
		child.setParent(this);
	}

	@Override
	public void addChildren(Op... children) {
		for (Op child : children)
			this.addChild(child);
	}

	@Override
	public void removeChild(Op child) {
		checkNotNull(child);

		this.load();

		this.children.remove(child);
		child.setParent(null);
	}


	@Override
	public List<Op> getChildren() {
		this.load();
		return this.children;
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


	// properties

	private transient Map<String, Object> properties = new HashMap<>();

	@Override
	public Map<String, Object> getProperties() {
		return this.properties;
	}

}
