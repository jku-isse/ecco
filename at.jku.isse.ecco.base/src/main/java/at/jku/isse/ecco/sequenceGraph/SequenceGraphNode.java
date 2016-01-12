package at.jku.isse.ecco.sequenceGraph;

import at.jku.isse.ecco.tree.Node;

import java.util.Map;

public interface SequenceGraphNode {

	public boolean getPol();

	public void setPol(boolean pol);

	public Map<Node, SequenceGraphNode> getChildren();

}
