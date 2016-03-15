package at.jku.isse.ecco.tree;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.core.Association;
import org.garret.perst.Persistent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A perst implementation of the node. When recursive loading for this node is disabled it will be loaded on demand as soon as any of its object members is accessed.
 *
 * @author Hannes Thaller
 * @version 1.0
 */
public class PerstNode extends Persistent implements Node {

	private transient NodeOperator operator = new NodeOperator(this);


	private boolean unique = true;

	private final List<Node> children = new ArrayList<>();

	private Artifact artifact = null;

	private Node parent = null;


	public PerstNode() {
	}

	public PerstNode(Artifact artifact) {
		this.artifact = artifact;
	}


	@Override
	public Node createNode() {
		return new PerstNode();
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
		return this.operator.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		return this.operator.equals(o);
	}

	@Override
	public String toString() {
		return this.operator.toString();
	}


	@Override
	public void slice(Node node) {
		this.operator.slice(node);
	}

	@Override
	public void merge(Node node) {
		this.operator.merge(node);
	}

	@Override
	public void sequence() {
		this.operator.sequence();
	}

	@Override
	public void updateArtifactReferences() {
		this.operator.updateArtifactReferences();
	}

	@Override
	public Node extractMarked() {
		return this.operator.extractMarked();
	}

	@Override
	public int countArtifacts() {
		return this.operator.countArtifacts();
	}

	@Override
	public Map<Integer, Integer> countArtifactsPerDepth() {
		return this.operator.countArtifactsPerDepth();
	}

	@Override
	public void print() {
		this.operator.print();
	}

	@Override
	public void checkConsistency() {
		this.operator.checkConsistency();
	}

}
