package at.jku.isse.ecco.storage.ser.tree;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.featuretrace.FeatureTrace;
import at.jku.isse.ecco.storage.ser.artifact.SerArtifact;
import at.jku.isse.ecco.storage.ser.featuretrace.SerFeatureTrace;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.util.Location;
import org.eclipse.collections.impl.factory.Maps;

import java.util.*;

import static com.google.common.base.Preconditions.checkNotNull;

public class SerNode implements Node, Node.Op {

	public static final long serialVersionUID = 1L;

	// stable identity, independent of artifact content - lets a foreign reference to this exact
	// node (e.g. SerArtifact.containingNode, when it points outside the node's own association's
	// tree) be resolved back to a real, properly-reconstructed instance after separately-persisted
	// associations are loaded, instead of dangling. See SerTransactionStrategy's post-load
	// resolution pass.
	private String id = UUID.randomUUID().toString();

	public String getStorageId() {
		return this.id;
	}

	private boolean unique = true;

	private transient List<Op> children = new ArrayList<>();

	// transient: artifacts are now their own independently-persisted, deduplicated entities (see
	// SerRepository.artifactsById) rather than being embedded once per referencing node - a node's
	// own artifact field used to be a "safe" direct reference (every node genuinely owns exactly
	// one artifact, a clean forward link), but that stopped being true once a NON-UNIQUE, shared
	// artifact (e.g. an ordered node with a PartialOrderGraph, reachable from multiple associations'
	// trees) got redundantly, independently re-serialized once per association file that reached it
	// - each copy a distinct Java object claiming the same storageId, silently clobbering each
	// other's resolution. artifactId is the serialized surrogate; the transient field is populated
	// by SerTransactionStrategy's post-load resolution pass against the global artifact store.
	private transient Artifact.Op<?> artifact = null;
	private String artifactId;

	private transient Op parent = null;

	private FeatureTrace featureTrace;

	private Map<String, Object> properties = null;

	private Integer numberOfChildren = 0;

	private Location location;

	@Override
	public Op copySingleNode(boolean copyFeatureTrace){
		if (copyFeatureTrace){
			return copySingleNodeCompletely();
		}

		Node.Op newNode = new SerNode(this.artifact);
		newNode.setUnique(this.unique);
		newNode.putProperties(this.getProperties());
		return newNode;
	}

	public Op copySingleNodeCompletely() {
		SerNode.Op newNode = new SerNode(this.artifact);
		newNode.putProperties(this.getProperties());
		if (this.featureTrace != null) {
			newNode.getFeatureTrace().setProactiveCondition(this.featureTrace.getProactiveConditionString());
			newNode.getFeatureTrace().setRetroactiveCondition(this.featureTrace.getRetroactiveConditionString());
		}
		return newNode;
	}

	@Override
	public Op getEqualChild(Op template) {
		Collection<Node.Op> children = this.getChildren();
		for (Node.Op child : children){
			if (child.getArtifact().equals(template.getArtifact())){
				return child;
			}
		}
		return null;
	}

	@Override
	public void updateNumberOfChildren(){
		this.numberOfChildren = this.children.size();
	}

	@Override
	public FeatureTrace getFeatureTrace() {
		return this.featureTrace;
	}

	@Override
	public void setFeatureTrace(FeatureTrace featureTrace) {
		this.featureTrace = featureTrace;
	}

	@Override
	public void removeProactiveTrace() {
		if (this.featureTrace == null){
			return;
		}
		this.featureTrace.removeProactiveCondition();
	}

	@Override
	public void combineProactiveTrace(Node.Op other){
		if (this.featureTrace == null || other.getFeatureTrace() == null) {
			return;
		}
		this.featureTrace.addProactiveCondition(other.getFeatureTrace().getProactiveConditionString());
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

	/** No artifact (and, unlike {@link #SerNode(Artifact.Op)}, no feature trace) - used for a root node, which never has an artifact, and by {@link #createNode()} for building a tree bottom-up. */
	public SerNode() {
	}

	public SerNode(Artifact.Op<?> artifact) {
		this.setArtifact(artifact);
		this.featureTrace = new SerFeatureTrace(this);
	}

	@Override
	public Op createNode(Artifact.Op<?> artifact) {
		Node.Op node = new SerNode(artifact);
		node.putProperties(this.getProperties());
		return node;
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
		this.artifactId = (artifact instanceof SerArtifact<?> serArtifact) ? serArtifact.getStorageId() : null;
	}

	public String getArtifactId() {
		return this.artifactId;
	}

	/** Used only by SerTransactionStrategy's post-load resolution pass - sets the live reference without touching artifactId (already correct, just loaded from the stream). */
	public void resolveArtifact(Artifact.Op<?> artifact) {
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
	public void removeChild(Op child) {
		checkNotNull(child);

		if (this.children.remove(child)) {
			child.setParent(null);
			this.numberOfChildren = this.children.size();
		} else {
			throw new EccoException("Attempted to remove child that does not exist.");
		}
	}

	@Override
	public List<Op> getChildren() {
		return this.children;
	}

	@Override
	public void setChildren(List<Op> children) {
		this.children = children;
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
	public Map<String, Object> getProperties() {
		if (this.properties == null)
			this.properties = Maps.mutable.empty();
		return this.properties;
	}

	@Override
	public String toString() {
		return this.getNodeString();
	}
}
