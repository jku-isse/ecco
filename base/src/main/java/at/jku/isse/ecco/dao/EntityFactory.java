package at.jku.isse.ecco.dao;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.core.Remote;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.tree.RootNode;

import java.util.Set;

/**
 * Creates entities depending on the used data implementation.
 */
public interface EntityFactory {

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
	 * @return The commit object.
	 */
	public Commit createCommit();

	/**
	 * Creates a configuration containing the given feature revisions.
	 *
	 * @param featureRevisions An array of feature revisions.
	 * @return A configuration containing the given feature revisions.
	 */
	public Configuration createConfiguration(FeatureRevision[] featureRevisions);


	/**
	 * Creates an artifact containing the given data.
	 *
	 * @param data The artifact data.
	 * @param <T>  The type of the artifact data object to be stored in the artifact.
	 * @return An artifact containing the given data object.
	 */
	public <T extends ArtifactData> Artifact.Op<T> createArtifact(T data);


	public Repository.Op createRepository();


	/**
	 * Creates a new empty instance of an association with all fields being initialized to the standard value.
	 *
	 * @return A empty initialized association.
	 */
	public Association.Op createAssociation();

	/**
	 * Creates an association initialized with the given artifact nodes.
	 *
	 * @param nodes A set of artifact nodes.
	 * @return An association containing the given nodes.
	 */
	public Association.Op createAssociation(Set<Node.Op> nodes);


	/**
	 * Creates a new instance of a {@link Feature} with the given name and description.
	 *
	 * @param id   of the feature
	 * @param name of the feature
	 * @return A new initialized instance of feature.
	 */
	public Feature createFeature(final String id, final String name);


	/**
	 * Creates a new empty root node.
	 *
	 * @return A new empty root node.
	 */
	public RootNode.Op createRootNode();

	/**
	 * Creates a new empty node.
	 *
	 * @return A new empty node.
	 */
	@Deprecated
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

	public Node.Op createOrderedNode(final Artifact.Op<?> artifact);

	public Node.Op createOrderedNode(final ArtifactData artifactData);

}
