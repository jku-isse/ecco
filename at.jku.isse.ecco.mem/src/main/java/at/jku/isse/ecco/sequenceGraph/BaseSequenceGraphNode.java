package at.jku.isse.ecco.sequenceGraph;

import at.jku.isse.ecco.artifact.Artifact;

import java.util.HashMap;
import java.util.Map;

public class BaseSequenceGraphNode implements SequenceGraphNode {

	private HashMap<Artifact<?>, SequenceGraphNode> children = new HashMap<>(); // maybe use linked hash map?

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
	public Map<Artifact<?>, SequenceGraphNode> getChildren() {
		return this.children;
	}

}
