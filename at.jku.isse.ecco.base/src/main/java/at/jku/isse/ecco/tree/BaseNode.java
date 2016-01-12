package at.jku.isse.ecco.tree;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.core.Association;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import static com.google.common.base.Preconditions.checkNotNull;

public class BaseNode implements Node {

	private final List<Node> uniqueChildren = new ArrayList<>();
	private final List<Node> allChildren = new ArrayList<>();

	private Artifact artifact = null;
	private Node parent = null;
	private int sequenceNumber = -1;
	private boolean atomic = false;

//	@Override
//	public <T extends Node> T slice(T other) throws EccoException {
//		// TODO!
//		Node temp = null;
//		try {
//			temp = other.getClass().newInstance();
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
//		return (T) temp;
//	}


	@Override
	public Node slice(Node other) throws EccoException {
		if (!this.equals(other))
			throw new EccoException("Intersection of non-equal nodes is not allowed!");

		if (this.getArtifact() != null && this.getArtifact() != other.getArtifact()) {
			other.getArtifact().putProperty(Artifact.PROPERTY_REPLACING_ARTIFACT, this.getArtifact());
			other.setArtifact(this.getArtifact());
		}

		if (this.isAtomic()) {
			return this;
		}

		Node intersection = this.createNode();
		intersection.setArtifact(this.getArtifact());
		intersection.setAtomic(this.isAtomic());
		intersection.setSequenceNumber(this.getSequenceNumber());

		Iterator<Node> leftChildrenIterator = this.getAllChildren().iterator();
		while (leftChildrenIterator.hasNext()) {
			Node leftChild = leftChildrenIterator.next();

			int ri = other.getAllChildren().indexOf(leftChild);
			if (ri == -1)
				continue;

			Node rightChild = other.getAllChildren().get(ri);

			Node intersectionChild = leftChild.slice(rightChild);

			if (this.getUniqueChildren().contains(intersectionChild) && other.getUniqueChildren().contains(intersectionChild)) {
				intersection.getAllChildren().add(intersectionChild);
				intersection.getUniqueChildren().add(intersectionChild);
				intersectionChild.setParent(intersection);
				if (intersectionChild.getArtifact() != null) {
					intersectionChild.getArtifact().setContainingNode(intersectionChild);
				}
			} else if (intersectionChild != null && (intersectionChild.getAllChildren().size() > 0 || intersectionChild.isAtomic())) {
				intersection.getAllChildren().add(intersectionChild);
				intersectionChild.setParent(intersection);
			}

			if ((leftChild.getAllChildren().size() == 0 || leftChild.isAtomic())) {
				if (!leftChild.isAtomic())
					leftChild.setParent(null);
				leftChildrenIterator.remove();
			}

			if ((intersection.getUniqueChildren().contains(rightChild) || !other.getUniqueChildren().contains(rightChild))
					&& (rightChild.getAllChildren().size() == 0 || rightChild.isAtomic())) {
				if (!rightChild.isAtomic())
					rightChild.setParent(null);
				other.getAllChildren().remove(rightChild);
			}
		}

		this.getUniqueChildren().removeAll(intersection.getUniqueChildren());
		other.getUniqueChildren().removeAll(intersection.getUniqueChildren());

		return intersection;
	}

	protected Node createNode() {
		return new BaseNode();
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
	public int getSequenceNumber() {
		return sequenceNumber;
	}

	@Override
	public void setSequenceNumber(int sequenceNumber) {
		this.sequenceNumber = sequenceNumber;
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
	public boolean isAtomic() {
		return atomic;
	}

	@Override
	public void setAtomic(boolean atomic) {
		this.atomic = atomic;
	}

	@Override
	public boolean isUnique() {
		return withParentUniquessPredicate() || withoutParentUniquenessPredicate() || orderedParentUniquenessPredicate();
	}

	/**
	 * Whether the parent is an unsequenced {@link OrderedNode} that contains
	 * this node.
	 * <p>
	 * FIXME: Is not in the new version -> the reason for adding this is unknown, check it.
	 *
	 * @return True if the parent is unsequenced and contains this node.
	 */
	private boolean orderedParentUniquenessPredicate() {
		assert parent != null : "Expected parent to be non-null";
		if (!(parent instanceof OrderedNode)) return false;

		OrderedNode parent = (OrderedNode) this.parent;
		return !parent.isSequenced() && parent.getOrderedChildren().contains(this);
	}

	/**
	 * Whether the parent is null or not which indicates the uniqueness.
	 *
	 * @return True if the parent is null.
	 */
	private boolean withoutParentUniquenessPredicate() {
		return parent == null;
	}

	/**
	 * Whether the parent contains this node as child in the unique children.
	 *
	 * @return True if parent has this node in his unique children.
	 */
	private boolean withParentUniquessPredicate() {
		return parent != null && parent.getUniqueChildren().contains(this);
	}

	@Override
	public void addChild(Node child) {
		checkNotNull(child);

		allChildren.add(child);
		uniqueChildren.add(child);
		child.setParent(this);
	}

	@Override
	public void removeChild(Node child) {
		checkNotNull(child);

		allChildren.remove(child);
		uniqueChildren.remove(child);
	}

	@Override
	public List<Node> getAllChildren() {
		return allChildren;
	}

	@Override
	public List<Node> getUniqueChildren() {
		return uniqueChildren;
	}

	@Override
	public int hashCode() {
		// if we have a sequence number we use it as hash
		if (this.sequenceNumber != -1)
			return this.sequenceNumber;

		return Objects.hash(artifact);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (!(obj instanceof Node)) return false;
		Node other = (Node) obj;
		if (artifact == null) {
			if (other.getArtifact() != null) return false;
		} else if (!artifact.equals(other.getArtifact())) return false;
		return sequenceNumber == other.getSequenceNumber();
	}

//	@Override
//	public String toString() {
//		return String.format("AllChildren: %d, UniqueChildren: %d, Artifact:%s", getAllChildren().size(), getUniqueChildren().size(), getArtifact().toString());
//	}

	@Override
	public String toString() {
		return this.getArtifact().toString();
	}

}
