package at.jku.isse.ecco.storage.jackson.tree;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.storage.jackson.artifact.JacksonArtifact;
import at.jku.isse.ecco.tree.Node;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import org.eclipse.collections.impl.factory.Maps;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

@JsonIdentityInfo(generator = ObjectIdGenerators.IntSequenceGenerator.class, property = "@id")
public class JacksonNode implements Node, Node.Op {

	public static final long serialVersionUID = 1L;


	private boolean unique = true;

	@JsonManagedReference(value = "children")
	private final List<JacksonNode> children = new ArrayList<>();

	@JsonManagedReference
	private JacksonArtifact<?> artifact = null;

	@JsonBackReference(value = "children")
	private JacksonNode parent = null;


	@Deprecated
	public JacksonNode() {
	}

	public JacksonNode(Artifact.Op<?> artifact) {
		if (!(artifact instanceof JacksonArtifact))
			throw new EccoException("Only Jackson storage types can be used.");
		this.artifact = (JacksonArtifact) artifact;
	}


	@Override
	public Op createNode(Artifact.Op<?> artifact) {
		return new JacksonNode(artifact);
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
		if (!(artifact instanceof JacksonArtifact))
			throw new EccoException("Only Jackson storage types can be used.");
		this.artifact = (JacksonArtifact) artifact;
	}

	@Override
	public Op getParent() {
		return parent;
	}

	@Override
	public void setParent(Op parent) {
		if (parent != null && !(parent instanceof JacksonNode))
			throw new EccoException("Only Jackson storage types can be used. Instead: " + parent.getClass());
		this.parent = (JacksonNode) parent;
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
		checkNotNull(child);

		if (!(child instanceof JacksonNode))
			throw new EccoException("Only Jackson storage types can be used.");

		if (this.getArtifact() != null && !this.getArtifact().isOrdered() && this.children.contains(child))
			throw new EccoException("An equivalent child is already contained. If multiple equivalent children are allowed use an ordered node.");

		this.children.add((JacksonNode) child);
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

		if (this.children.remove(child))
			child.setParent(null);
		else
			throw new EccoException("Attempted to remove child that does not exist.");
	}


	@Override
	public List<? extends Op> getChildren() {
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

	private transient Map<String, Object> properties = null;

	@Override
	public Map<String, Object> getProperties() {
		if (this.properties == null)
			this.properties = Maps.mutable.empty();
		return this.properties;
	}

}
