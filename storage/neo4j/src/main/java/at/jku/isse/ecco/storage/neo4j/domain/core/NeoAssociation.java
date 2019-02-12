package at.jku.isse.ecco.storage.neo4j.domain.core;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.counter.AssociationCounter;
import at.jku.isse.ecco.module.Condition;
import at.jku.isse.ecco.storage.neo4j.domain.NeoCondition;
import at.jku.isse.ecco.storage.neo4j.domain.NeoEntity;
import at.jku.isse.ecco.tree.RootNode;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;

@NodeEntity
public class NeoAssociation extends NeoEntity implements Association, Association.Op {

    @Property("associationId")
	private String id;

    @Property("artifactTreeRoot")
	private RootNode.Op artifactTreeRoot;

    @Property("associationCounter")
	private AssociationCounter associationCounter;


	public NeoAssociation() {
		this.id = "";
		this.artifactTreeRoot = null;
		this.associationCounter = new NeoAssociationCounter(this);
	}


	@Override
	public String getId() {
		return this.id;
	}

	@Override
	public void setId(final String id) {
		this.id = id;
	}

	@Override
	public RootNode.Op getRootNode() {
		return artifactTreeRoot;
	}

	@Override
	public void setRootNode(final RootNode.Op root) {
		this.artifactTreeRoot = root;
		root.setContainingAssociation(this);
	}

	@Override
	public AssociationCounter getCounter() {
		return this.associationCounter;
	}

	@Override
	public Condition createCondition() {
		return new NeoCondition();
	}


	@Override
	public String toString() {
		return this.getAssociationString();
	}

}
