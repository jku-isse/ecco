package at.jku.isse.ecco.storage.ser.dao;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.core.Remote;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.maintree.building.AssociationMerger;
import at.jku.isse.ecco.maintree.building.BoostedAssociationMerger;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.storage.ser.artifact.SerArtifact;
import at.jku.isse.ecco.storage.ser.core.SerAssociation;
import at.jku.isse.ecco.storage.ser.core.SerCommit;
import at.jku.isse.ecco.storage.ser.core.SerRemote;
import at.jku.isse.ecco.storage.ser.feature.SerConfiguration;
import at.jku.isse.ecco.storage.ser.feature.SerFeature;
import at.jku.isse.ecco.storage.ser.maintree.SerAssociationMerger;
import at.jku.isse.ecco.storage.ser.maintree.SerBoostedAssociationMerger;
import at.jku.isse.ecco.storage.ser.repository.SerRepository;
import at.jku.isse.ecco.storage.ser.tree.SerNode;
import at.jku.isse.ecco.storage.ser.tree.SerRootNode;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.tree.RootNode;

import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class SerEntityFactory implements EntityFactory {
	public SerEntityFactory() {
	}

	@Override
	public Remote createRemote(String name, String address, Remote.Type type) {
		return new SerRemote(name, address, type);
	}

	@Override
	public Commit createCommit(String username) {
		return new SerCommit(username);
	}

	@Override
	public Configuration createConfiguration(FeatureRevision[] featureRevisions) {
		return new SerConfiguration(featureRevisions);
	}

	@Override
	public <T extends ArtifactData> Artifact.Op<T> createArtifact(T data) {
		return new SerArtifact<T>(data);
	}

	@Override
	public Repository.Op createRepository() {
		return new SerRepository();
	}

	@Override
	public Association.Op createAssociation() {
		return new SerAssociation();
	}

	@Override
	public Association.Op createAssociation(Set<Node.Op> nodes) {
		checkNotNull(nodes);
		checkArgument(!nodes.isEmpty(), "Expected a non-empty set of nodes but was empty.");

		final Association.Op association = new SerAssociation();

		RootNode.Op rootNode = this.createRootNode();
		rootNode.setContainingAssociation(association);

		for (Node.Op node : nodes) {
			rootNode.addChild(node);
		}

		association.setRootNode(rootNode);

		return association;
	}


	@Override
	public SerFeature createFeature(final String id, final String name) {
		checkNotNull(name);
		checkArgument(!name.isEmpty(), "Expected a non-empty name but was empty.");

		return new SerFeature(id, name);
	}


	@Override
	public RootNode.Op createRootNode() {
		return new SerRootNode();
	}

	@Deprecated
	@Override
	public Node.Op createNode() {
		return new SerNode();
	}

	@Override
	public Node.Op createNode(final Artifact.Op artifact) {
		checkNotNull(artifact);

		final Node.Op node = new SerNode(artifact);
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

	@Override
	public BoostedAssociationMerger createBoostedAssociationMerger() {
		return new SerBoostedAssociationMerger();
	}

	@Override
	public AssociationMerger createAssociationMerger() {
		return new SerAssociationMerger();
	}
}
