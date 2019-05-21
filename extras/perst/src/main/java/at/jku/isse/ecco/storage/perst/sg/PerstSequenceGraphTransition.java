package at.jku.isse.ecco.storage.perst.sg;

import at.jku.isse.ecco.sg.SequenceGraph;
import at.jku.isse.ecco.storage.perst.artifact.PerstArtifact;
import org.garret.perst.Persistent;

public class PerstSequenceGraphTransition extends Persistent implements SequenceGraph.Transition, SequenceGraph.Transition.Op {

	private PerstArtifact<?> key;
	private PerstSequenceGraphNode value;

	public PerstSequenceGraphTransition(PerstArtifact<?> key, PerstSequenceGraphNode value) {
		this.key = key;
		this.value = value;
	}

	@Override
	public PerstArtifact<?> getKey() {
		return this.key;
	}

	@Override
	public PerstSequenceGraphNode getValue() {
		return this.value;
	}

}
