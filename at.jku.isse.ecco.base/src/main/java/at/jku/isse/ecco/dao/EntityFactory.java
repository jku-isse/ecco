package at.jku.isse.ecco.dao;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.core.Remote;
import at.jku.isse.ecco.core.Variant;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureVersion;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.module.ModuleFeature;
import at.jku.isse.ecco.module.PresenceCondition;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.tree.RootNode;

import java.util.Collection;
import java.util.Set;

/**
 * Creates entities depending on the used data implementation.
 *
 * @author JKU, ISSE
 * @version 1.0
 */
public interface EntityFactory {

	public Repository.Op createRepository();

	/**
	 * Creates a remote with given name, address and type.
	 *
	 * @param name    The name of the remote.
	 * @param address The address of the remote.
	 * @param type    The type of the remote, either LOCAL or REMOTE.
	 * @return The created remote.
	 */
	public Remote createRemote(String name, String address, Remote.Type type);

	/**
	 * Creates an empty configuration.
	 *
	 * @return
	 */
	public Configuration createConfiguration();

	/**
	 * Creates an empty variant without a name.
	 *
	 * @return
	 */
	public Variant createVariant();

	/**
	 * Creates an empty commit.
	 *
	 * @return
	 */
	public Commit createCommit();

	/**
	 * Creates an empty presence condition.
	 *
	 * @return
	 */
	public PresenceCondition createPresenceCondition();

	/**
	 * Creates a presence condition that is initialized with the given configuration.
	 *
	 * @param configuration The configuration to compute the presence condition from.
	 * @return
	 */
	public PresenceCondition createPresenceCondition(Configuration configuration, int maxOrder);

	/**
	 * Creates a clone/copy of the given presence condition.
	 *
	 * @param pc The presence condition that is to be cloned.
	 * @return
	 */
	public PresenceCondition createPresenceCondition(PresenceCondition pc);


	// # ARTIFACTS ################################################################

	/**
	 * Creates an artifact containing the given data.
	 *
	 * @param data The artifact data.
	 * @return
	 */
	public <T extends ArtifactData> Artifact.Op<T> createArtifact(T data);


	// # ASSOCIATIONS ################################################################

	/**
	 * Creates a new empty instance of an association with all fields being initialized to the standard value.
	 *
	 * @return A empty initialized association.
	 */
	public Association.Op createAssociation();

	/**
	 * Creates an association initialized with the given condition and artifact nodes.
	 *
	 * @param presenceCondition
	 * @param nodes
	 * @return
	 */
	public Association.Op createAssociation(PresenceCondition presenceCondition, Set<Node.Op> nodes);


	// # FEATURES ################################################################

	/**
	 * Creates a new instance of a {@link Feature} with the given name and description.
	 *
	 * @param id          of the feature
	 * @param name        of the feature
	 * @param description of the feature
	 * @return A new initialized instance of feature.
	 */
	public Feature createFeature(final String id, final String name, final String description);

//	public FeatureInstance createFeatureInstance(Feature feature, FeatureVersion featureVersion, final boolean sign);

	/**
	 * Creates a new module.
	 *
	 * @return Returns a new initialized module.
	 */
	public Module createModule();

//	public ModuleFeature createModuleFeature(ModuleFeature moduleFeature);

	public ModuleFeature createModuleFeature(Feature feature, boolean sign);

	public ModuleFeature createModuleFeature(Feature feature, Collection<FeatureVersion> featureVersions, boolean sign);


	// # NODES ################################################################

	/**
	 * Creates a new empty node.
	 *
	 * @return A new empty node.
	 */
	public Node.Op createNode();

	/**
	 * Creates a {@link Node} with the given artifact.
	 *
	 * @param artifact that the node contains
	 * @return A new node instance containing the given artifact.s
	 */
	public Node.Op createNode(final Artifact.Op<?> artifact);

	/**
	 * Creates a new node with a new artifact containing the given data.
	 *
	 * @param artifactData The artifact data.
	 * @return The new node.
	 */
	public Node.Op createNode(final ArtifactData artifactData);

	/**
	 * Creates a new empty root node.
	 *
	 * @return A new empty root node.
	 */
	public RootNode.Op createRootNode();

	public Node.Op createOrderedNode(final Artifact.Op<?> artifact);

	public Node.Op createOrderedNode(final ArtifactData artifactData);

}
