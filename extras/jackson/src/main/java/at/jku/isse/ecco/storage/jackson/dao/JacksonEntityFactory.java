package at.jku.isse.ecco.storage.jackson.dao;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.core.Remote;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.storage.jackson.artifact.JacksonArtifact;
import at.jku.isse.ecco.storage.jackson.core.JacksonAssociation;
import at.jku.isse.ecco.storage.jackson.core.JacksonCommit;
import at.jku.isse.ecco.storage.jackson.core.JacksonRemote;
import at.jku.isse.ecco.storage.jackson.feature.JacksonConfiguration;
import at.jku.isse.ecco.storage.jackson.feature.JacksonFeature;
import at.jku.isse.ecco.storage.jackson.repository.JacksonRepository;
import at.jku.isse.ecco.storage.jackson.tree.JacksonNode;
import at.jku.isse.ecco.storage.jackson.tree.JacksonRootNode;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.tree.RootNode;

import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class JacksonEntityFactory implements EntityFactory {

	@Override
	public Remote createRemote(String name, String address, Remote.Type type) {
		return new JacksonRemote(name, address, type);
	}

	@Override
	public Commit createCommit() {
		return new JacksonCommit();
	}

	@Override
	public Configuration createConfiguration(FeatureRevision[] featureRevisions) {
		return new JacksonConfiguration(featureRevisions);
	}


	@Override
	public <T extends ArtifactData> Artifact.Op<T> createArtifact(T data) {
		return new JacksonArtifact<T>(data);
	}

	@Override
	public Repository.Op createRepository() {
		return new JacksonRepository();
	}


	@Override
	public Association.Op createAssociation() {
		return new JacksonAssociation();
	}

	@Override
	public Association.Op createAssociation(Set<Node.Op> nodes) {
		checkNotNull(nodes);
		checkArgument(!nodes.isEmpty(), "Expected a non-empty set of nodes but was empty.");

		final Association.Op association = new JacksonAssociation();

		RootNode.Op rootNode = this.createRootNode();
		rootNode.setContainingAssociation(association);

		for (Node.Op node : nodes) {
			rootNode.addChild(node);
		}

		association.setRootNode(rootNode);

		return association;
	}


	@Override
	public JacksonFeature createFeature(final String id, final String name) {
		checkNotNull(name);
		checkArgument(!name.isEmpty(), "Expected a non-empty name but was empty.");

		return new JacksonFeature(id, name);
	}


	@Override
	public RootNode.Op createRootNode() {
		return new JacksonRootNode();
	}

	@Deprecated
	@Override
	public Node.Op createNode() {
		return new JacksonNode();
	}

	@Override
	public Node.Op createNode(final Artifact.Op artifact) {
		checkNotNull(artifact);

		final Node.Op node = new JacksonNode(artifact);
		artifact.setContainingNode(node);

		return node;
	}

	@Override
	public Node.Op createNode(ArtifactData artifactData) {
		return this.createNode(this.createArtifact(artifactData));
	}

	@Override
	public Node.Op createOrderedNode(final Artifact.Op artifact) {
		checkNotNull(artifact);

		final Node.Op node = this.createNode(artifact);
		artifact.setOrdered(true);

		return node;
	}

	@Override
	public Node.Op createOrderedNode(ArtifactData artifactData) {
		return this.createOrderedNode(this.createArtifact(artifactData));
	}

}
