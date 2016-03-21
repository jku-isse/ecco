package at.jku.isse.ecco.tree;

import at.jku.isse.ecco.util.Trees;

import java.util.Map;

public class NodeOperator {

	private Node node;

	public NodeOperator(Node node) {
		this.node = node;
	}


	@Override
	public String toString() {
		if (this.node instanceof RootNode)
			return "root";
		else if (this.node.getArtifact() != null)
			return this.node.getArtifact().toString();
		else
			return "null";
	}

	@Override
	public int hashCode() {
		return this.node.getArtifact() != null ? this.node.getArtifact().hashCode() : 0;
	}

	@Override
	public boolean equals(Object other) {
		if (this.node == other) return true;
		if (other == null) return false;
		if (!(other instanceof Node)) return false;

		Node otherNode = (Node) other;

		if (this.node.getArtifact() == null)
			return otherNode.getArtifact() == null;

		return this.node.getArtifact().equals(otherNode.getArtifact());
	}


	// # OPERATIONS #################################################################

	/**
	 * See {@link at.jku.isse.ecco.util.Trees#slice(Node, Node)}
	 */
	public void slice(Node node) {
		Trees.slice(this.node, node);
	}

	/**
	 * See {@link at.jku.isse.ecco.util.Trees#merge(Node, Node)}
	 */
	public void merge(Node node) {
		Trees.merge(this.node, node);
	}

	/**
	 * See {@link at.jku.isse.ecco.util.Trees#sequence(Node)}
	 */
	public void sequence() {
		Trees.sequence(this.node);
	}

	/**
	 * See {@link at.jku.isse.ecco.util.Trees#updateArtifactReferences(Node)}
	 */
	public void updateArtifactReferences() {
		Trees.updateArtifactReferences(this.node);
	}

	/**
	 * See {@link at.jku.isse.ecco.util.Trees#extractMarked(Node)}
	 */
	public Node extractMarked() {
		return Trees.extractMarked(this.node);
	}

	/**
	 * See {@link at.jku.isse.ecco.util.Trees#countArtifacts(Node)}
	 */
	public int countArtifacts() {
		return Trees.countArtifacts(this.node);
	}

	/**
	 * See {@link at.jku.isse.ecco.util.Trees#countArtifactsPerDepth(Node)}
	 */
	public Map<Integer, Integer> countArtifactsPerDepth() {
		return Trees.countArtifactsPerDepth(this.node);
	}

	/**
	 * See {@link at.jku.isse.ecco.util.Trees#print(Node)}
	 */
	public void print() {
		Trees.print(this.node);
	}

	/**
	 * See {@link at.jku.isse.ecco.util.Trees#checkConsistency(Node)}
	 */
	public void checkConsistency() {
		Trees.checkConsistency(this.node);
	}

}
