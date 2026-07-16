package at.jku.isse.ecco.storage.ser.core;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.counter.AssociationCounter;
import at.jku.isse.ecco.module.Condition;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.storage.ser.counter.SerAssociationCounter;
import at.jku.isse.ecco.storage.ser.module.SerCondition;
import at.jku.isse.ecco.tree.RootNode;

/**
 * Memory implementation of {@link Association}.
 */
public class SerAssociation implements Association, Association.Op {

	public static final long serialVersionUID = 1L;


	private String id;
	private RootNode.Op artifactTreeRoot;
	private AssociationCounter associationCounter;
	private Repository.Op containingRepository;

	private transient boolean visible;

	// nullable, may be null/absent on an association serialized before this field existed --
	// serialVersionUID deliberately left unchanged for exactly that reason, same pattern as
	// SerRepository.constraints. Null means "never minimized" (or a prior minimization was cleared),
	// not "minimizes to nothing" (which PresenceConditionMinimizer.format() itself renders as the
	// literal string "FALSE").
	private String minimizedCondition;


	public SerAssociation() {
		this.id = "";
		this.artifactTreeRoot = null;
		this.associationCounter = new SerAssociationCounter(this);
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
		return new SerCondition();
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
	public String getMinimizedCondition() {
		return this.minimizedCondition;
	}

	@Override
	public void setMinimizedCondition(String condition) {
		this.minimizedCondition = condition;
	}

	@Override
	public String toString() {
		return this.getAssociationString();
	}

}
