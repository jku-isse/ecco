package at.jku.isse.ecco.core;

import at.jku.isse.ecco.dao.Persistable;
import at.jku.isse.ecco.module.PresenceCondition;
import at.jku.isse.ecco.tree.RootNode;

import java.util.List;

/**
 * Represents a trace between a presence condition and an artifact tree. An association can have a set of parents.
 */
public interface Association extends Persistable {

	public PresenceCondition getPresenceCondition();

	public void setPresenceCondition(PresenceCondition presenceCondition);


	public List<Association> getParents();

	public void addParent(Association parent);

	public void removeParent(Association parent);


	/**
	 * Returns the id of the association or the empty string if it does not have an id yet.
	 *
	 * @return The id of the association.
	 */
	int getId();

	/**
	 * Sets the id of the association.
	 *
	 * @param id The id of the association.
	 */
	void setId(int id);

	/**
	 * Returns the name of the association or the empty string if it does not have a name yet.
	 *
	 * @return The name of the association.
	 */
	String getName();

	/**
	 * Sets the name of the association.
	 *
	 * @param name The name of the association.
	 */
	void setName(String name);

	/**
	 * Returns the root node of the artifact tree or null if no artifacts are stored.
	 *
	 * @return The root of the artifact tree.
	 */
	RootNode getRootNode();

	/**
	 * Sets the root node of the artifact tree.
	 *
	 * @param root The root of the artifact tree (may be null).
	 */
	void setRootNode(RootNode root);

}
