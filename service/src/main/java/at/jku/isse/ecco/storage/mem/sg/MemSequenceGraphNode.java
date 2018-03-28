package at.jku.isse.ecco.storage.mem.sg;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.sg.SequenceGraph;
import org.eclipse.collections.impl.factory.Maps;

import java.util.Map;

public class MemSequenceGraphNode implements SequenceGraph.Node, SequenceGraph.Node.Op {

	private Map<Artifact.Op<?>, SequenceGraph.Node.Op> children;

	private boolean pol;


	public MemSequenceGraphNode(boolean pol) {
		this.pol = pol;
		this.children = Maps.mutable.empty();
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
