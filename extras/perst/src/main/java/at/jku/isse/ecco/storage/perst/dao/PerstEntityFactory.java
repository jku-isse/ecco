package at.jku.isse.ecco.storage.perst.dao;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.core.Remote;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.storage.perst.artifact.PerstArtifact;
import at.jku.isse.ecco.storage.perst.core.PerstAssociation;
import at.jku.isse.ecco.storage.perst.core.PerstCommit;
import at.jku.isse.ecco.storage.perst.core.PerstRemote;
import at.jku.isse.ecco.storage.perst.feature.PerstConfiguration;
import at.jku.isse.ecco.storage.perst.feature.PerstFeature;
import at.jku.isse.ecco.storage.perst.tree.PerstNode;
import at.jku.isse.ecco.storage.perst.tree.PerstRootNode;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.tree.RootNode;

import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Creates database entities defined using Perst implementations.
 */
public class PerstEntityFactory implements EntityFactory {

	@Override
	public Remote createRemote(String name, String address, Remote.Type type) {
		return new PerstRemote(name, address, type);
	}

	@Override
	public Commit createCommit() {
		return new PerstCommit();
	}

	@Override
	public Configuration createConfiguration(FeatureRevision[] featureRevisions) {
		return new PerstConfiguration(featureRevisions);
	}


	@Override
	public <T extends ArtifactData> Artifact.Op<T> createArtifact(T data) {
		return new PerstArtifact<T>(data);
	}


	@Override
	public Association.Op createAssociation() {
		return new PerstAssociation();
	}

	@Override
	public Association.Op createAssociation(Set<Node.Op> nodes) {
		checkNotNull(nodes);
		checkArgument(!nodes.isEmpty(), "Expected a non-empty set of nodes but was empty.");

		final Association.Op association = new PerstAssociation();

		RootNode.Op rootNode = this.createRootNode();
		rootNode.setContainingAssociation(association);

		for (Node.Op node : nodes) {
			rootNode.addChild(node);
		}

		association.setRootNode(rootNode);

		return association;
	}


	@Override
	public Feature createFeature(final String id, final String name) {
		checkNotNull(name);
		checkArgument(!name.isEmpty(), "Expected a non-empty name but was empty.");

		return new PerstFeature(id, name);
	}


	@Override
	public RootNode.Op createRootNode() {
		return new PerstRootNode();
	}

	@Override
	public Node.Op createNode() {
		return new PerstNode();
	}

	@Override
	public Node.Op createNode(final Artifact.Op artifact) {
		checkNotNull(artifact);

		final Node.Op node = new PerstNode();
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
