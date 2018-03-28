package at.jku.isse.ecco.storage.mem.sg;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.sg.SequenceGraph;
import at.jku.isse.ecco.storage.mem.artifact.MemArtifact;

import java.util.ArrayList;
import java.util.Collection;

public class MemSequenceGraphNode implements SequenceGraph.Node, SequenceGraph.Node.Op {

	//private Map<Artifact.Op<?>, SequenceGraph.Node.Op> children;
	private Collection<SequenceGraph.Transition.Op> children;

	private boolean pol;


	public MemSequenceGraphNode(boolean pol) {
		this.pol = pol;
		//this.children = Maps.mutable.empty();
		this.children = new ArrayList<>();
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
	public MemSequenceGraphTransition addTransition(Artifact.Op<?> key, Op value) {
		if (!(key instanceof MemArtifact) || !(value instanceof MemSequenceGraphNode))
			throw new EccoException("Only mem types can be added to mem types.");

		MemSequenceGraphTransition memSequenceGraphTransition = new MemSequenceGraphTransition((MemArtifact) key, (MemSequenceGraphNode) value);
		this.children.add(memSequenceGraphTransition);
		return memSequenceGraphTransition;
	}

//	@Override
//	public Map<Artifact.Op<?>, SequenceGraph.Node.Op> getChildren() {
//		return this.children;
//	}

	@Override
	public Collection<SequenceGraph.Transition.Op> getChildren() {
		return this.children;
	}

}
