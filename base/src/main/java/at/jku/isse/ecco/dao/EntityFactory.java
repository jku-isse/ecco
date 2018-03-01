package at.jku.isse.ecco.dao;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.core.Remote;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.module.Condition;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.tree.RootNode;

import java.util.Set;

/**
 * Creates entities depending on the used data implementation.
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
	 * Creates an empty commit.
	 *
	 * @return
	 */
	public Commit createCommit();

	/**
	 * Creates an empty configuration.
	 *
	 * @return
	 */
	public Configuration createConfiguration();

	public Configuration createConfiguration(FeatureRevision[] featureRevisions);


	/**
	 * Creates an artifact containing the given data.
	 *
	 * @param data The artifact data.
	 * @return
	 */
	public <T extends ArtifactData> Artifact.Op<T> createArtifact(T data);


	/**
	 * Creates a new empty instance of an association with all fields being initialized to the standard value.
	 *
	 * @return A empty initialized association.
	 */
	public Association.Op createAssociation();

	/**
	 * Creates an association initialized with the given condition and artifact nodes.
	 *
	 * @param nodes
	 * @return
	 */
	public Association.Op createAssociation(Set<Node.Op> nodes);


	/**
	 * Creates a new instance of a {@link Feature} with the given name and description.
	 *
	 * @param id          of the feature
	 * @param name        of the feature
	 * @param description of the feature
	 * @return A new initialized instance of feature.
	 */
	public Feature createFeature(final String id, final String name, final String description);


	/**
	 * Creates a new module. TODO: move this from here to repository. nobody else should be allowed to create modules. same with features?
	 *
	 * @return Returns a new initialized module.
	 */
	public Module createModule(Feature[] pos, Feature[] neg);


	/**
	 * TODO: move this into Association.Op interface?
	 *
	 * @return
	 */
	public Condition createModuleCondition();


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
