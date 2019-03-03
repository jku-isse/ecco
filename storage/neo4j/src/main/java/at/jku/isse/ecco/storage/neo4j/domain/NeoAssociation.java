package at.jku.isse.ecco.storage.neo4j.domain;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.counter.AssociationCounter;
import at.jku.isse.ecco.module.Condition;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.tree.RootNode;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;
import org.neo4j.ogm.annotation.Relationship;

@NodeEntity
public class NeoAssociation extends NeoEntity implements Association, Association.Op {

    @Property("associationId")
	private String id;

    @Relationship("artifactTreeRoot")
	private RootNode.Op artifactTreeRoot;

    @Relationship("hasAssociationCounter")
	private AssociationCounter associationCounter;

	@Relationship(type = "containingRepo")
	private Repository.Op containingRepository;

	public NeoAssociation() {
		this.id = "";
		this.artifactTreeRoot = null;
		this.associationCounter = new NeoAssociationCounter(this);
		this.containingRepository = null;
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
	public Repository.Op getContainingRepository() {
		return this.containingRepository;
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
