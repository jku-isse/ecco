package at.jku.isse.ecco.storage.mem.sg;

import at.jku.isse.ecco.sg.SequenceGraph;
import at.jku.isse.ecco.storage.mem.artifact.MemArtifact;

public class MemSequenceGraphTransition implements SequenceGraph.Transition, SequenceGraph.Transition.Op {

	public static final long serialVersionUID = 1L;


	private MemArtifact<?> key;
	private MemSequenceGraphNode value;

	public MemSequenceGraphTransition(MemArtifact<?> key, MemSequenceGraphNode value) {
		this.key = key;
		this.value = value;
	}

	@Override
	public MemArtifact<?> getKey() {
		return this.key;
	}

	@Override
	public MemSequenceGraphNode getValue() {
		return this.value;
	}

}
