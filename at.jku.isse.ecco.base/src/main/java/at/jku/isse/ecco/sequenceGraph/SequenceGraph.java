package at.jku.isse.ecco.sequenceGraph;

import at.jku.isse.ecco.tree.OrderedNode;

public interface SequenceGraph {

	public int[] align(OrderedNode node);

	public int getCurSeqNumber();

	public SequenceGraphNode getRoot();

	public void sequence(OrderedNode node);

}
