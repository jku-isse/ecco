package at.jku.isse.ecco.sequenceGraph;

import at.jku.isse.ecco.tree.Node;

import java.util.HashMap;
import java.util.Map;

public class BaseSequenceGraphNode implements SequenceGraphNode {

	private HashMap<Node, SequenceGraphNode> children = new HashMap<>(); // maybe use linked hash map?

	private boolean pol;

	public BaseSequenceGraphNode(boolean pol) {
		this.pol = pol;
	}

	@Override
	public boolean getPol() {
		return this.pol;
	}

	@Override
	public void setPol(boolean pol) {
		this.pol = pol;
	}

	@Override
	public Map<Node, SequenceGraphNode> getChildren() {
		return this.children;
	}

}
