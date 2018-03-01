package at.jku.isse.ecco.storage.mem.core;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.counter.AssociationCounter;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.storage.mem.counter.MemAssociationCounter;
import at.jku.isse.ecco.storage.mem.dao.MemEntityFactory;
import at.jku.isse.ecco.tree.RootNode;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Memory implementation of {@link Association}.
 */
public class MemAssociation implements Association, Association.Op {

	private String id;
	private String name;
	private RootNode.Op artifactTreeRoot;
	private AssociationCounter associationCounter;


	public MemAssociation() {
		this.id = "";
		this.name = "";
		this.artifactTreeRoot = null;
		this.associationCounter = new MemAssociationCounter();
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
	public String getName() {
		return name;
	}

	@Override
	public void setName(final String name) {
		checkNotNull(name);

		this.name = name;
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
	public EntityFactory getEntityFactory() {
		return new MemEntityFactory();
	}


	@Override
	public String toString() {
		return this.getAssociationString();
	}

}
