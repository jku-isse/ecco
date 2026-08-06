package at.jku.isse.ecco.storage.jackson.core;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.counter.AssociationCounter;
import at.jku.isse.ecco.module.Condition;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.storage.jackson.counter.JacksonAssociationCounter;
import at.jku.isse.ecco.storage.jackson.module.JacksonCondition;
import at.jku.isse.ecco.storage.jackson.repository.JacksonRepository;
import at.jku.isse.ecco.storage.jackson.tree.JacksonRootNode;
import at.jku.isse.ecco.tree.RootNode;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;

/**
 * Memory implementation of {@link Association}.
 */
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
public class JacksonAssociation implements Association, Association.Op {

	public static final long serialVersionUID = 1L;


	private String id;
	@JsonManagedReference
	private JacksonRootNode artifactTreeRoot;
	@JsonManagedReference
	private JacksonAssociationCounter associationCounter;
	@JsonBackReference
	private JacksonRepository containingRepository;
	private transient boolean visible;


	public JacksonAssociation() {
		this.id = "";
		this.artifactTreeRoot = null;
		this.associationCounter = new JacksonAssociationCounter(this);
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
		if (!(root instanceof JacksonRootNode))
			throw new EccoException("Only Jackson storage types can be used.");
		this.artifactTreeRoot = (JacksonRootNode) root;
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
		return new JacksonCondition();
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
