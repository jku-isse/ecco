package at.jku.isse.ecco.storage.mem.tree;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.tree.Node;
import org.eclipse.collections.impl.factory.Maps;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

public class MemNode implements Node, Node.Op {

	public static final long serialVersionUID = 1L;


	private boolean unique = true;

	private transient List<Op> children = new ArrayList<>();

	private Artifact.Op<?> artifact = null;

	private transient Op parent = null;

	private Integer numberOfChildren = 0;


	@Deprecated
	public MemNode() {
	}

	@Override
	public void updateNumberOfChildren(){
		this.numberOfChildren = this.children.size();
	}

	public MemNode(Artifact.Op<?> artifact) {
		this.artifact = artifact;
	}


	@Override
	public Op createNode(Artifact.Op<?> artifact) {
		return new MemNode(artifact);
	}


	@Override
	public boolean isAtomic() {
		if (this.artifact != null)
			return this.artifact.isAtomic();
		else
			return false;
	}


	@Override
	public Association.Op getContainingAssociation() {
		if (this.parent == null)
			return null;
		else
			return this.parent.getContainingAssociation();
	}


	@Override
	public Artifact.Op<?> getArtifact() {
		return artifact;
	}

	@Override
	public void setArtifact(Artifact.Op<?> artifact) {
		this.artifact = artifact;
	}

	@Override
	public Op getParent() {
		return parent;
	}

	@Override
	public void setParent(Op parent) {
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
	public void addChild(Op child) {
		this.addChildWithoutNumberUpdate(child);
		this.numberOfChildren = this.children.size();
	}

	public void addChildWithoutNumberUpdate(Op child){
		checkNotNull(child);
		if (this.children == null){ this.children = new ArrayList<>(); }

		if (this.getArtifact() != null && !this.getArtifact().isOrdered() && this.children.contains(child))
			throw new EccoException("An equivalent child is already contained. If multiple equivalent children are allowed use an ordered node.");

		this.children.add(child);
		child.setParent(this);
	}

	@Override
	public void addChildren(Op... children) {
		if (this.children == null) {
			this.children = new ArrayList<>();
		}

		// check the whole batch (plus already-present children) for duplicates in one pass instead
		// of once per child against the (growing) existing list - O(n) instead of O(n^2) for a
		// large batch. Note this makes the check atomic: on a duplicate, nothing from this batch is
		// added (the one-at-a-time loop this replaced would have already added the children before
		// the duplicate). No existing caller relies on that partial-application behavior.
		if (this.getArtifact() != null && !this.getArtifact().isOrdered()) {
			Set<Op> seen = new HashSet<>(this.children);
			for (Op child : children) {
				checkNotNull(child);
				if (!seen.add(child)) {
					throw new EccoException("An equivalent child is already contained. If multiple equivalent children are allowed use an ordered node.");
				}
			}
		} else {
			for (Op child : children) {
				checkNotNull(child);
			}
		}

		for (Op child : children) {
			this.children.add(child);
			child.setParent(this);
		}
		this.numberOfChildren = this.children.size();
	}

	@Override
	public void setChildren(List<Op> children) {
		this.children = children;
	}

	@Override
	public void removeChild(Op child) {
		checkNotNull(child);

		if (this.children.remove(child))
			child.setParent(null);
		else
			throw new EccoException("Attempted to remove child that does not exist.");
	}


	@Override
	public List<Op> getChildren() {
		return this.children;
	}

	@Override
	public int getNumberOfChildren() {
		if (this.children == null && this.numberOfChildren == null){
			return 0;
		} else if (this.numberOfChildren == null){
			return 0;
		}
		return this.numberOfChildren;
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

	private transient Map<String, Object> properties = null;

	@Override
	public Map<String, Object> getProperties() {
		if (this.properties == null)
			this.properties = Maps.mutable.empty();
		return this.properties;
	}

}
