package at.jku.isse.ecco.tree;

import at.jku.isse.ecco.sequenceGraph.SequenceGraph;

import java.util.List;

/**
 * A ordered node in the artifact tree.
 * <p>
 * This interface is part of the {@link at.jku.isse.ecco.plugin.CorePlugin#EXTENSION_POINT_DAL}.
 *
 * @author JKU, ISSE
 * @author Hannes Thaller
 * @version 1.0
 */
public interface OrderedNode extends Node {

	SequenceGraph createSequenceGraph();

	/**
	 * Returns the ordered children of the node.
	 *
	 * @return the ordered children
	 */
	List<Node> getOrderedChildren();

	/**
	 * Returns the sequence graph of the ordered children.
	 *
	 * @return The sequence graph.
	 */
	SequenceGraph getSequenceGraph();

	/**
	 * Returns whether the node is aligned.
	 *
	 * @return True if the node is aligned, false otherwise.
	 */
	boolean isAligned();

	/**
	 * Returns whether the stored sequence graph is sequenced.
	 *
	 * @return True if the sequence graph is sequenced, false otherwise.
	 */
	boolean isSequenced();

	/**
	 * Sequences the sequence graph.
	 */
	void sequence();

	/**
	 * Sets whether the node is aligned or not.
	 *
	 * @param aligned true if the node is aligned, false otherwise
	 */
	void setAligned(boolean aligned);

	/**
	 * Sets the ordered children of the node.
	 *
	 * @param children to set
	 */
	void setOrderedChildren(List<Node> children);

	/**
	 * Sets the sequence graph.
	 *
	 * @param sequenceGraph of the node
	 */
	void setSequenceGraph(SequenceGraph sequenceGraph);

	/**
	 * Sets whether the sequence graph is sequenced or not.
	 *
	 * @param sequenced true if the sequence graph is sequnced, false otherwise
	 */
	void setSequenced(boolean sequenced);

}
