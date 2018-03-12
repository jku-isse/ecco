package at.jku.isse.ecco.storage.perst.sg;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.sg.SequenceGraph;
import org.garret.perst.Persistent;

import java.util.HashMap;
import java.util.Map;

public class PerstSequenceGraphNode extends Persistent implements SequenceGraph.Node, SequenceGraph.Node.Op {

	public PerstSequenceGraphNode() {
		this(false);
	}

	public PerstSequenceGraphNode(boolean pol) {
		this.pol = pol;
	}


	private HashMap<Artifact.Op<?>, SequenceGraph.Node.Op> children = new HashMap<>(); // maybe use linked hash map?

	private boolean pol;

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
