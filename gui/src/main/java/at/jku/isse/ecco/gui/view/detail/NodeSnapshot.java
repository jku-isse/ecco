package at.jku.isse.ecco.gui.view.detail;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.artifact.ArtifactReference;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.pog.PartialOrderGraph;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.tree.RootNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * A copy of an artifact tree, made holding the service's lock, for what walks a tree on the FX
 * thread - the adapters' code viewers, the snippet tree of an association: the live tree is the
 * repository's, which a commit, pull or import changes while it holds the lock (NFR-C1b), and the FX
 * thread must not wait for that (NFR-C3). Each node keeps its artifact's data (shared: it is not
 * changed), flags, sequence number, properties and association - what the viewers read; not the
 * order graph, which is read from the original, holding the lock.
 */
public final class NodeSnapshot {

	private final Map<Node, Node> copies = new IdentityHashMap<>();
	private final Map<Artifact<?>, Copied<?>> artifacts = new IdentityHashMap<>();
	private final CopiedRootNode root;

	private NodeSnapshot(Node root, Predicate<Node> descend) {
		this.root = new CopiedRootNode(root, this.artifactOf(root));
		this.copies.put(root, this.root);
		this.copyChildren(root, this.root, descend);
	}

	/** A copy of the whole tree of [root]; call holding the service's lock. */
	public static NodeSnapshot of(Node root) {
		return of(root, node -> true);
	}

	/**
	 * A copy of the tree of [root], the children of a node only where [descend] says so - the others
	 * have none in the copy; call holding the service's lock.
	 */
	public static NodeSnapshot of(Node root, Predicate<Node> descend) {
		return new NodeSnapshot(root.getNode(), descend);
	}

	public RootNode root() {
		return this.root;
	}

	/** The copy of [original] (a node of the tree copied, or a wrapper of one), or null if it is not in the copy. */
	public Node copyOf(Node original) {
		return original == null ? null : this.copies.get(original.getNode());
	}

	/**
	 * Puts [children] - copies of the children of [copy], a node of this copy - in this order, to
	 * show an order not recorded yet: the copy is its maker's, nothing of the repository changes.
	 */
	public void reorder(Node copy, List<? extends Node> children) {
		CopiedNode parent = (CopiedNode) copy;
		parent.children.clear();
		parent.children.addAll(children);
	}

	private void copyChildren(Node original, CopiedNode copy, Predicate<Node> descend) {
		if (!descend.test(original))
			return;
		for (Node child : original.getChildren()) {
			CopiedNode childCopy = new CopiedNode(child, this.artifactOf(child));
			childCopy.parent = copy;
			copy.children.add(childCopy);
			this.copies.put(child, childCopy);
			this.copyChildren(child, childCopy, descend);
		}
	}

	private Copied<?> artifactOf(Node node) {
		Artifact<?> artifact = node.getArtifact();
		return artifact == null ? null : this.artifacts.computeIfAbsent(artifact, NodeSnapshot::copy);
	}

	private static <T extends ArtifactData> Copied<T> copy(Artifact<T> original) {
		return new Copied<>(original);
	}


	private static class CopiedNode implements Node {

		private static final long serialVersionUID = 1L;

		private final Copied<?> artifact;
		private final boolean unique;
		private final boolean atomic;
		private final Association association;
		/** What the original said it is, for a node without an artifact (a composition's root says "root"). */
		private final String label;
		private final Map<String, Object> properties;
		private final List<Node> children = new ArrayList<>();
		private Node parent;

		CopiedNode(Node original, Copied<?> artifact) {
			this.artifact = artifact;
			this.unique = original.isUnique();
			this.atomic = original.isAtomic();
			this.association = original.getContainingAssociation();
			this.label = artifact == null ? original.toString() : null;
			this.properties = new HashMap<>(original.getProperties());
		}

		@Override
		public boolean isAtomic() {
			return this.atomic;
		}

		@Override
		public Association getContainingAssociation() {
			return this.association;
		}

		@Override
		public Artifact<?> getArtifact() {
			return this.artifact;
		}

		@Override
		public Node getParent() {
			return this.parent;
		}

		@Override
		public boolean isUnique() {
			return this.unique;
		}

		@Override
		public List<? extends Node> getChildren() {
			return Collections.unmodifiableList(this.children);
		}

		@Override
		public Map<String, Object> getProperties() {
			return this.properties;
		}

		@Override
		public int hashCode() {
			return this.artifact != null ? this.artifact.hashCode() : 0;
		}

		@Override
		public boolean equals(Object other) {
			if (this == other)
				return true;
			if (!(other instanceof Node otherNode))
				return false;
			if (this.artifact == null)
				return otherNode.getArtifact() == null;
			return this.artifact.equals(otherNode.getArtifact());
		}

		@Override
		public String toString() {
			return this.artifact != null ? this.getNodeString() : this.label;
		}
	}

	private static final class CopiedRootNode extends CopiedNode implements RootNode {

		private static final long serialVersionUID = 1L;

		CopiedRootNode(Node original, Copied<?> artifact) {
			super(original, artifact);
		}
	}

	/** An artifact's copy: equal to another copy as the repository's artifacts are to each other. */
	private static final class Copied<T extends ArtifactData> implements Artifact<T> {

		private static final long serialVersionUID = 1L;

		private final T data;
		private final boolean atomic;
		private final boolean ordered;
		private final boolean sequenced;
		private final int sequenceNumber;
		private final Map<String, Object> properties;
		/** Stands for the node that holds the artifact in its association: the viewers colour by that association. */
		private final Node containingNode;

		Copied(Artifact<T> original) {
			this.data = original.getData();
			this.atomic = original.isAtomic();
			this.ordered = original.isOrdered();
			this.sequenced = original.isSequenced();
			this.sequenceNumber = original.getSequenceNumber();
			this.properties = new HashMap<>(original.getProperties());
			Node holder = original.getContainingNode();
			this.containingNode = holder == null ? null : new ContainingNode(this, holder.getContainingAssociation());
		}

		@Override
		public T getData() {
			return this.data;
		}

		@Override
		public boolean isAtomic() {
			return this.atomic;
		}

		@Override
		public boolean isOrdered() {
			return this.ordered;
		}

		@Override
		public boolean isSequenced() {
			return this.sequenced;
		}

		/** Not copied: read the original's, holding the service's lock. */
		@Override
		public PartialOrderGraph getPartialOrderGraph() {
			return null;
		}

		@Override
		public int getSequenceNumber() {
			return this.sequenceNumber;
		}

		@Override
		public Node getContainingNode() {
			return this.containingNode;
		}

		@Override
		public void setContainingNode(Node.Op node) {
			throw new UnsupportedOperationException("a copy of an artifact for display");
		}

		@Override
		public Collection<? extends ArtifactReference> getUses() {
			return List.of();
		}

		@Override
		public Collection<? extends ArtifactReference> getUsedBy() {
			return List.of();
		}

		@Override
		public Map<String, Object> getProperties() {
			return this.properties;
		}

		@Override
		public int hashCode() {
			return 31 * this.data.hashCode() + (this.ordered ? 1 : 0);
		}

		@Override
		public boolean equals(Object other) {
			if (this == other)
				return true;
			if (!(other instanceof Copied<?> that))
				return false;
			if (this.ordered != that.ordered)
				return false;
			if (this.sequenceNumber != PartialOrderGraph.UNASSIGNED_SEQUENCE_NUMBER && that.sequenceNumber != PartialOrderGraph.UNASSIGNED_SEQUENCE_NUMBER
					&& this.sequenceNumber != that.sequenceNumber)
				return false;
			return this.data.equals(that.data);
		}

		@Override
		public boolean equalsIgnoreSequenceNumber(Object other) {
			return other instanceof Copied<?> that && this.ordered == that.ordered && this.data.equals(that.data);
		}

		@Override
		public String toString() {
			return this.data.toString();
		}
	}

	/** The node an artifact's copy names as its holder: only its association and the artifact. */
	private static final class ContainingNode implements Node {

		private static final long serialVersionUID = 1L;

		private final Artifact<?> artifact;
		private final Association association;

		ContainingNode(Artifact<?> artifact, Association association) {
			this.artifact = artifact;
			this.association = association;
		}

		@Override
		public boolean isAtomic() {
			return this.artifact.isAtomic();
		}

		@Override
		public Association getContainingAssociation() {
			return this.association;
		}

		@Override
		public Artifact<?> getArtifact() {
			return this.artifact;
		}

		@Override
		public Node getParent() {
			return null;
		}

		@Override
		public boolean isUnique() {
			return true;
		}

		@Override
		public List<? extends Node> getChildren() {
			return List.of();
		}

		@Override
		public Map<String, Object> getProperties() {
			return Map.of();
		}

		@Override
		public String toString() {
			return this.getNodeString();
		}
	}
}
