package at.jku.isse.ecco.tree;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.JpaAssociation;

import javax.persistence.Entity;
import javax.persistence.OneToOne;
import java.io.Serializable;

@Entity
public class JpaRootNode extends JpaNode implements RootNode, Serializable {

	@OneToOne(targetEntity = JpaAssociation.class)
	private Association containingAssociation;

	@Override
	public boolean isUnique() {
		return true;
	}

	@Override
	public boolean isAtomic() {
		return false;
	}


	@Override
	public Node createNode() {
		return new JpaRootNode();
	}


	@Override
	public void setContainingAssociation(Association containingAssociation) {
		this.containingAssociation = containingAssociation;
	}

	@Override
	public Association getContainingAssociation() {
		return this.containingAssociation;
	}

	@Override
	public String toString() {
		return "root";
	}

}
