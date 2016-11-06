package at.jku.isse.ecco.dao;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.artifact.ArtifactReference;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.core.Remote;
import at.jku.isse.ecco.core.Variant;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureInstance;
import at.jku.isse.ecco.feature.FeatureVersion;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.module.ModuleFeature;
import at.jku.isse.ecco.module.PresenceCondition;
import at.jku.isse.ecco.plugin.artifact.ArtifactData;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.tree.RootNode;

import java.util.Collection;
import java.util.Set;

/**
 * Creates database entities depending on the underlying database implementation.
 *
 * @author Hannes Thaller
 * @version 1.0
 */
public interface EntityFactory {

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
	//public Artifact createArtifact(ArtifactData data);
	public <T extends ArtifactData> Artifact<T> createArtifact(T data);

	/**
	 * Creates a new artifact reference with the given source and target that is referenced.
	 *
	 * @param source of the reference
	 * @param target that is referenced
	 * @return The initialized artifact reference.
	 */
	public ArtifactReference createArtifactReference(final Artifact source, final Artifact target);

	/**
	 * Creates a new artifact reference with the given source and target that is referenced. Additionally the type of reference is set.
	 *
	 * @param source of the reference
	 * @param target that is referenced
	 * @param type   of the reference
	 * @return The initialized artifact reference.
	 */
	public ArtifactReference createArtifactReference(final Artifact source, final Artifact target, final String type);


	// # ASSOCIATIONS ################################################################

	/**
	 * Creates a new empty instance of an association with all fields being initialized to the standard value.
	 *
	 * @return A empty initialized association.
	 */
	public Association createAssociation();

	/**
	 * Creates an association initialized with the given condition and artifact nodes.
	 *
	 * @param presenceCondition
	 * @param nodes
	 * @return
	 */
	public Association createAssociation(PresenceCondition presenceCondition, Set<Node> nodes);


	// # FEATURES ################################################################

	/**
	 * Creates a featuer with the given name.
	 *
	 * @param name The name of the feature.
	 * @return
	 */
	public Feature createFeature(final String name);

	/**
	 * Creates a new instance of a {@link Feature} with the given name and description.
	 *
	 * @param name        of the feature
	 * @param description of the feature
	 * @return A new initialized instance of feature.
	 */
	public Feature createFeature(final String name, final String description);

	public FeatureVersion createFeatureVersion(Feature feature, int version);

	public FeatureInstance createFeatureInstance(Feature feature, FeatureVersion featureVersion, final boolean sign);

	/**
	 * Creates a new module.
	 *
	 * @return Returns a new initialized module.
	 */
	public Module createModule();

//	/**
//	 * Creates a new module with the given features.
//	 * <p>
//	 * If the given features are not of the type which the entity factory usually provides they will be cast to them.
//	 *
//	 * @param featureInstances that the module contains
//	 * @return A new module containing the given features.
//	 */
//	public Module createModule(Set<FeatureInstance> featureInstances);

	public ModuleFeature createModuleFeature(ModuleFeature moduleFeature);

	public ModuleFeature createModuleFeature(Feature feature, boolean sign);

	public ModuleFeature createModuleFeature(Feature feature, Collection<FeatureVersion> featureVersions, boolean sign);


	// # NODES ################################################################

	/**
	 * Creates a new empty node.
	 *
	 * @return A new empty node.
	 */
	public Node createNode();

	/**
	 * Creates a {@link Node} with the given artifact.
	 *
	 * @param artifact that the node contains
	 * @return A new node instance containing the given artifact.s
	 */
	public Node createNode(final Artifact artifact);

	/**
	 * Creates a new node with a new artifact containing the given data.
	 *
	 * @param artifactData The artifact data.
	 * @return The new node.
	 */
	public Node createNode(final ArtifactData artifactData);

	/**
	 * Creates a new empty root node.
	 *
	 * @return A new empty root node.
	 */
	public RootNode createRootNode();

	public Node createOrderedNode(final Artifact artifact);

	public Node createOrderedNode(final ArtifactData artifactData);

	/**
	 * Creates a new empty root node that is contained in the given association.
	 *
	 * @param association which contains the root node.
	 * @return A root node that is contained in the given association.
	 */
	public RootNode createRootNode(final Association association);

}
