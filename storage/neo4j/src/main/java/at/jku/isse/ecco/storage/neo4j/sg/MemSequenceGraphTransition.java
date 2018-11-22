package at.jku.isse.ecco.storage.neo4j.sg;

import at.jku.isse.ecco.sg.SequenceGraph;
import at.jku.isse.ecco.storage.neo4j.artifact.NeoArtifact;

public class MemSequenceGraphTransition implements SequenceGraph.Transition, SequenceGraph.Transition.Op {

	public static final long serialVersionUID = 1L;


	private NeoArtifact<?> key;
	private MemSequenceGraphNode value;

	public MemSequenceGraphTransition(NeoArtifact<?> key, MemSequenceGraphNode value) {
		this.key = key;
		this.value = value;
	}

	@Override
	public NeoArtifact<?> getKey() {
		return this.key;
	}

	@Override
	public MemSequenceGraphNode getValue() {
		return this.value;
	}

}
