package at.jku.isse.ecco.storage.neo4j.dao;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.core.Remote;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.storage.neo4j.domain.*;
import at.jku.isse.ecco.storage.neo4j.domain.NeoArtifact;
import at.jku.isse.ecco.storage.neo4j.domain.NeoAssociation;
import at.jku.isse.ecco.storage.neo4j.domain.NeoCommit;
import at.jku.isse.ecco.storage.neo4j.domain.NeoConfiguration;
import at.jku.isse.ecco.storage.neo4j.domain.NeoFeature;
import at.jku.isse.ecco.storage.neo4j.domain.NeoNode;
import at.jku.isse.ecco.storage.neo4j.domain.NeoRootNode;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.tree.RootNode;

import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class NeoEntityFactory implements EntityFactory {

	@Override
	public Remote createRemote(String name, String address, Remote.Type type) {
		return new NeoRemote(name, address, type);
	}

	@Override
	public Commit createCommit() {
		return new NeoCommit();
	}

	@Override
	public Configuration createConfiguration(FeatureRevision[] featureRevisions) {
		return new NeoConfiguration(featureRevisions);
	}


	@Override
	public <T extends ArtifactData> Artifact.Op<T> createArtifact(T data) {
		return new NeoArtifact<>(data);
	}

	@Override
	public Repository.Op createRepository() {
		return new NeoRepository();
	}


	@Override
	public Association.Op createAssociation() {
		return new NeoAssociation();
	}

	@Override
	public Association.Op createAssociation(Set<Node.Op> nodes) {
		checkNotNull(nodes);
		checkArgument(!nodes.isEmpty(), "Expected a non-empty set of nodes but was empty.");

		final Association.Op association = new NeoAssociation();

		RootNode.Op rootNode = this.createRootNode();
		rootNode.setContainingAssociation(association);

		for (Node.Op node : nodes) {
			rootNode.addChild(node);
		}

		association.setRootNode(rootNode);

		return association;
	}


	@Override
	public NeoFeature createFeature(final String id, final String name) {
		checkNotNull(name);
		checkArgument(!name.isEmpty(), "Expected a non-empty name but was empty.");

		return new NeoFeature(id, name);
	}


	@Override
	public RootNode.Op createRootNode() {
		return new NeoRootNode();
	}

	@Override
	public Node.Op createNode() {
		return new NeoNode();
	}

	@Override
	public Node.Op createNode(final Artifact.Op artifact) {
		checkNotNull(artifact);

		final Node.Op node = this.createNode();
		node.setArtifact(artifact);
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
