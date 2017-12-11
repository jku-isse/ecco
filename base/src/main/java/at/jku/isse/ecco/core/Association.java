package at.jku.isse.ecco.core;

import at.jku.isse.ecco.dao.Persistable;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.module.ModuleFeature;
import at.jku.isse.ecco.module.PresenceCondition;
import at.jku.isse.ecco.tree.RootNode;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Represents a trace between a presence condition and an artifact tree. An association can have a set of parents.
 */
public interface Association extends Persistable {

	/**
	 * A simple set of modules for this association.
	 *
	 * @return The set of modules.
	 */
	public Set<Module> getModules();


	// # PRESENCE TABLE #####################################################################

	/**
	 * Maps module features (i.e. set of feature versions with sign) to the number of commits/variants with at least one of the feature versions in which the artifacts were contained.
	 *
	 * @return The presence table.
	 */
	public Map<ModuleFeature, Integer> getPresenceTable();

	/**
	 * Returns the number of commits/variants that contained the artifacts in this association.
	 *
	 * @return The number of commits/variants in which the contained artifacts were present.
	 */
	public int getPresenceCount();

	public int incPresenceCount();

	public int incPresenceCount(int val);


	// # PARENTS AND CHILDREN #####################################################################

	public Collection<Association> getParents();

	public void addParent(Association parent);

	public void removeParent(Association parent);

	public Collection<Association> getChildren();

	public void addChild(Association child);

	public void removeChild(Association child);


	// # PRESENCE CONDITION #####################################################################

	/**
	 * Returns the presence condition of the association.
	 *
	 * @return The presence condition.
	 */
	public PresenceCondition getPresenceCondition();

	/**
	 * Sets the presence condition of the association.
	 *
	 * @param presenceCondition The presence condition.
	 */
	public void setPresenceCondition(PresenceCondition presenceCondition);


	// ######################################################################

	/**
	 * Returns the id of the association or the empty string if it does not have an id yet.
	 *
	 * @return The id of the association.
	 */
	public String getId();

	/**
	 * Sets the id of the association.
	 *
	 * @param id The id of the association.
	 */
	public void setId(String id);

	/**
	 * Returns the name of the association or the empty string if it does not have a name yet.
	 *
	 * @return The name of the association.
	 */
	public String getName();

	/**
	 * Sets the name of the association.
	 *
	 * @param name The name of the association.
	 */
	public void setName(String name);

	/**
	 * Returns the root node of the artifact tree or null if no artifacts are stored.
	 *
	 * @return The root of the artifact tree.
	 */
	public RootNode getRootNode();


	/**
	 * Private association interface.
	 */
	public interface Op extends Association {
		/**
		 * Returns the root node operand.
		 *
		 * @return The root node operand.
		 */
		public RootNode.Op getRootNode();

		/**
		 * Sets the root node of the artifact tree.
		 *
		 * @param root The root of the artifact tree (may be null).
		 */
		public void setRootNode(RootNode.Op root);
	}

}
