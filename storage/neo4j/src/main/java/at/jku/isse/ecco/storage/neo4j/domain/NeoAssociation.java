package at.jku.isse.ecco.storage.neo4j.domain;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.counter.AssociationCounter;
import at.jku.isse.ecco.module.Condition;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.storage.neo4j.dao.NeoTransactionStrategy;
import at.jku.isse.ecco.tree.RootNode;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;
import org.neo4j.ogm.annotation.Relationship;
import org.neo4j.ogm.annotation.Transient;

@NodeEntity
public class NeoAssociation extends NeoEntity implements Association, Association.Op {

    @Property("associationId")
	private String associationId;

    // incoming from NeoRootNode
    @Relationship(type = "artifactTreeRootAs", direction = Relationship.INCOMING)
	public RootNode.Op artifactTreeRoot;

    // incoming from NAC
    @Relationship(type = "hasAssociationCounterAs", direction = Relationship.INCOMING)
	public NeoAssociationCounter associationCounter;

    // backref
	@Relationship("hasAssociationRp")
	private NeoRepository containingRepository;

	public NeoAssociation() {
		this.associationId = "";
		this.artifactTreeRoot = null;
		this.associationCounter = new NeoAssociationCounter(this);
		this.containingRepository = null;
	}


	@Override
	public String getId() {
		return this.associationId;
	}

	@Override
	public void setId(final String id) {
		this.associationId = id;
	}

	@Override
	public RootNode.Op getRootNode() {
//		NeoTransactionStrategy transactionStrategy = containingRepository.getTransactionStrategy();
//		NeoRootNode neoTreeRoot = (NeoRootNode) this.artifactTreeRoot;
//		transactionStrategy.getNeoSession().load(NeoRootNode.class, neoTreeRoot.getNeoId(), -1);
		return artifactTreeRoot;
	}

	@Override
	public void setRootNode(final RootNode.Op root) {
		this.artifactTreeRoot = root;
		root.setContainingAssociation(this);
	}

	@Override
	public NeoRepository.Op getContainingRepository() {
		return this.containingRepository;
	}

	@Override
	public AssociationCounter getCounter() {
//		NeoTransactionStrategy transactionStrategy = containingRepository.getTransactionStrategy();

		/** where does this wrong containing repository come from? */
		//transactionStrategy.getNeoSession().loadAll(NeoModuleCounter.class, this.associationCounter.getChildren());
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
