package at.jku.isse.ecco.sequenceGraph;

import at.jku.isse.ecco.artifact.Artifact;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

@Entity
public class JpaSequenceGraphNode implements SequenceGraphNode, Serializable {

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
