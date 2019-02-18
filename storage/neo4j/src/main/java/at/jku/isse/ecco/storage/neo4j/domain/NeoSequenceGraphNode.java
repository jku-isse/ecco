package at.jku.isse.ecco.storage.neo4j.domain;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.sg.SequenceGraph;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;
import org.neo4j.ogm.annotation.Relationship;

import java.util.ArrayList;
import java.util.Collection;

@NodeEntity
public class NeoSequenceGraphNode extends NeoEntity implements SequenceGraph.Node, SequenceGraph.Node.Op {

	@Relationship("HAS")
	private Collection<SequenceGraph.Transition.Op> children;

	@Property("pol")
	private boolean pol;


	public NeoSequenceGraphNode(boolean pol) {
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
	public NeoSequenceGraphTransition addTransition(Artifact.Op<?> key, Op value) {
		if (!(key instanceof NeoArtifact) || !(value instanceof NeoSequenceGraphNode))
			throw new EccoException("Only mem types can be added to mem types.");

		NeoSequenceGraphTransition neoSequenceGraphTransition = new NeoSequenceGraphTransition((NeoArtifact) key, (NeoSequenceGraphNode) value);
		this.children.add(neoSequenceGraphTransition);
		return neoSequenceGraphTransition;
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
