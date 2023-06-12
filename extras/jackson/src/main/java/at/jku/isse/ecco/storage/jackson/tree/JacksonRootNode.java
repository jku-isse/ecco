package at.jku.isse.ecco.storage.jackson.tree;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.storage.jackson.core.JacksonAssociation;
import at.jku.isse.ecco.tree.RootNode;
import com.fasterxml.jackson.annotation.JsonBackReference;

public class JacksonRootNode extends JacksonNode implements RootNode, RootNode.Op {

	public static final long serialVersionUID = 1L;


	@JsonBackReference
	private JacksonAssociation containingAssociation;


	public JacksonRootNode() {
		//super(new MemArtifact<>(new StringArtifactData("ROOT")));
		super();
	}


	@Override
	public boolean isUnique() {
		return true;
	}

	@Override
	public boolean isAtomic() {
		return false;
	}


	@Override
	public RootNode.Op createNode(Artifact.Op<?> artifact) {
		return new JacksonRootNode();
	}


	@Override
	public void setContainingAssociation(Association.Op containingAssociation) {
		if (!(containingAssociation instanceof JacksonAssociation))
			throw new EccoException("Only Jackson storage types can be used.");
		this.containingAssociation = (JacksonAssociation) containingAssociation;
	}

	@Override
	public Association.Op getContainingAssociation() {
		return this.containingAssociation;
	}

}
