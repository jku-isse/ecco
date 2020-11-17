package at.jku.isse.ecco.storage.mem.core;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.counter.AssociationCounter;
import at.jku.isse.ecco.module.Condition;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.storage.mem.counter.MemAssociationCounter;
import at.jku.isse.ecco.storage.mem.module.MemCondition;
import at.jku.isse.ecco.tree.RootNode;

/**
 * Memory implementation of {@link Association}.
 */
public class MemAssociation implements Association, Association.Op {

	public static final long serialVersionUID = 1L;


	private String id;
	private RootNode.Op artifactTreeRoot;
	private AssociationCounter associationCounter;
	private Repository.Op containingRepository;

	private transient boolean visible;


	public MemAssociation() {
		this.id = "";
		this.artifactTreeRoot = null;
		this.associationCounter = new MemAssociationCounter(this);
		this.containingRepository = null;

		this.visible = true;
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
		return new MemCondition();
	}

	@Override
	public boolean isVisible() {
		return this.visible;
	}

	@Override
	public void setVisible(boolean visible) {
		this.visible = visible;
	}


	@Override
	public String toString() {
		return this.getAssociationString();
	}

}
