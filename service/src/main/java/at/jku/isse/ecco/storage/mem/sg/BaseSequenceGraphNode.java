package at.jku.isse.ecco.storage.mem.sg;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.sg.SequenceGraph;

import java.util.HashMap;
import java.util.Map;

public class BaseSequenceGraphNode implements SequenceGraph.Node, SequenceGraph.Node.Op {

	private HashMap<Artifact.Op<?>, SequenceGraph.Node.Op> children = new HashMap<>(); // maybe use linked hash map?

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
	public Map<Artifact.Op<?>, SequenceGraph.Node.Op> getChildren() {
		return this.children;
	}

}
