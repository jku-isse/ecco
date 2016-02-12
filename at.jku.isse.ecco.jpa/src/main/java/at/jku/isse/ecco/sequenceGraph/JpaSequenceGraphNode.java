package at.jku.isse.ecco.sequenceGraph;

import at.jku.isse.ecco.artifact.Artifact;

import javax.persistence.*;
import java.util.HashMap;
import java.util.Map;

@Entity
public class JpaSequenceGraphNode implements SequenceGraphNode {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private int id;


	private HashMap<Artifact<?>, SequenceGraphNode> children = new HashMap<>(); // maybe use linked hash map?

	private boolean pol;

	public JpaSequenceGraphNode(boolean pol) {
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
	public Map<Artifact<?>, SequenceGraphNode> getChildren() {
		return this.children;
	}

}
