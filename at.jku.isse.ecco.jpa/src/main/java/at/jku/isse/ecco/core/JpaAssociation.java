package at.jku.isse.ecco.core;

import at.jku.isse.ecco.module.JpaPresenceCondition;
import at.jku.isse.ecco.module.PresenceCondition;
import at.jku.isse.ecco.tree.JpaRootNode;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.tree.RootNode;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

@Entity
public class JpaAssociation implements Association {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private int id;

	private String name = "";

	@OneToOne(targetEntity = JpaRootNode.class, mappedBy = "containingAssociation")
	private RootNode artifactTreeRoot;

	@OneToOne(targetEntity = JpaPresenceCondition.class)
	private PresenceCondition presenceCondition;

	@OneToMany(targetEntity = JpaAssociation.class)
	private List<Association> parents = new ArrayList<Association>();

	/**
	 * Constructs a new association.
	 */
	public JpaAssociation() {

	}


	@Override
	public int countArtifacts() {
		return this.countRecursively(this.getArtifactTreeRoot(), 0);
	}

	private int countRecursively(Node node, int currentCount) {
		if (node.getArtifact() != null && node.isUnique()) {
			currentCount++;
		}
		for (Node child : node.getChildren()) {
			currentCount = this.countRecursively(child, currentCount);
		}
		return currentCount;
	}


	@Override
	public PresenceCondition getPresenceCondition() {
		return this.presenceCondition;
	}

	@Override
	public void setPresenceCondition(PresenceCondition presenceCondition) {
		this.presenceCondition = presenceCondition;
	}

	@Override
	public List<Association> getParents() {
		return this.parents;
	}

	@Override
	public void addParent(Association parent) {
		this.parents.add(parent);
	}

	@Override
	public void removeParent(Association parent) {
		this.parents.remove(parent);
	}

	@Override
	public int getId() {
		return this.id;
	}

	@Override
	public void setId(final int id) {
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
	public RootNode getArtifactTreeRoot() {
		return artifactTreeRoot;
	}

	@Override
	public void setArtifactRoot(final RootNode root) {
		this.artifactTreeRoot = root;
		root.setContainingAssociation(this);
	}

	@Override
	public String toString() {
		return String.format("Id: %d, Name: %s, Artifact Tree: %s", this.id, this.name, artifactTreeRoot.toString());
	}

}
