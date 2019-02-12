package at.jku.isse.ecco.storage.neo4j.domain.sg;

import at.jku.isse.ecco.sg.SequenceGraph;
import at.jku.isse.ecco.storage.neo4j.domain.NeoEntity;
import at.jku.isse.ecco.storage.neo4j.domain.artifact.NeoArtifact;
import at.jku.isse.ecco.storage.neo4j.domain.sg.NeoSequenceGraphNode;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Relationship;

@NodeEntity
public class NeoSequenceGraphTransition extends NeoEntity implements SequenceGraph.Transition, SequenceGraph.Transition.Op {

    @Relationship("HAS")
	private NeoArtifact<?> key;

    @Relationship("HAS")
	private NeoSequenceGraphNode value;

	public NeoSequenceGraphTransition(NeoArtifact<?> key, NeoSequenceGraphNode value) {
		this.key = key;
		this.value = value;
	}

	@Override
	public NeoArtifact<?> getKey() {
		return this.key;
	}

	@Override
	public NeoSequenceGraphNode getValue() {
		return this.value;
	}

}
