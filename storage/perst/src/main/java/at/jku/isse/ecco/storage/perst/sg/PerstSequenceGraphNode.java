package at.jku.isse.ecco.storage.perst.sg;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.sg.SequenceGraph;
import at.jku.isse.ecco.storage.perst.artifact.PerstArtifact;
import org.garret.perst.Persistent;

import java.util.ArrayList;
import java.util.Collection;

public class PerstSequenceGraphNode extends Persistent implements SequenceGraph.Node, SequenceGraph.Node.Op {

	public PerstSequenceGraphNode() {
		this(false);
	}

	public PerstSequenceGraphNode(boolean pol) {
		this.pol = pol;
		this.children = new ArrayList<>();
	}


	private Collection<SequenceGraph.Transition.Op> children;

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
	public SequenceGraph.Transition.Op addTransition(Artifact.Op<?> key, Op value) {
		if (!(key instanceof PerstArtifact) || !(value instanceof PerstSequenceGraphNode))
			throw new EccoException("Only perst types can be added to perst types.");

		PerstSequenceGraphTransition perstSequenceGraphTransition = new PerstSequenceGraphTransition((PerstArtifact) key, (PerstSequenceGraphNode) value);
		this.children.add(perstSequenceGraphTransition);
		return perstSequenceGraphTransition;
	}

	@Override
	public Collection<SequenceGraph.Transition.Op> getChildren() {
		return this.children;
	}

}
