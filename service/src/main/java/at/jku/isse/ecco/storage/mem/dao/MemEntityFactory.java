package at.jku.isse.ecco.storage.mem.dao;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.core.Remote;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.storage.mem.artifact.MemArtifact;
import at.jku.isse.ecco.storage.mem.core.MemAssociation;
import at.jku.isse.ecco.storage.mem.core.MemCommit;
import at.jku.isse.ecco.storage.mem.core.MemRemote;
import at.jku.isse.ecco.storage.mem.feature.MemFeature;
import at.jku.isse.ecco.storage.mem.module.MemModule;
import at.jku.isse.ecco.storage.mem.tree.MemNode;
import at.jku.isse.ecco.storage.mem.tree.MemRootNode;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.tree.RootNode;

import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class MemEntityFactory implements EntityFactory {

	@Override
	public Remote createRemote(String name, String address, Remote.Type type) {
		return new MemRemote(name, address, type);
	}

	@Override
	public Commit createCommit() {
		return new MemCommit();
	}

	@Override
	public Configuration createConfiguration(FeatureRevision[] featureRevisions) {
		return null;
	}


	@Override
	public <T extends ArtifactData> Artifact.Op<T> createArtifact(T data) {
		return new MemArtifact<T>(data);
	}


	@Override
	public Association.Op createAssociation() {
		return new MemAssociation();
	}

	@Override
	public Association.Op createAssociation(Set<Node.Op> nodes) {
		checkNotNull(nodes);
		checkArgument(!nodes.isEmpty(), "Expected a non-empty set of nodes but was empty.");

		final Association.Op association = new MemAssociation();

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

		return new MemFeature(id, name);
	}

	@Override
	public Module createModule(Feature[] pos, Feature[] neg) {
		return new MemModule(pos, neg);
	}


	@Override
	public RootNode.Op createRootNode() {
		return new MemRootNode();
	}

	@Override
	public Node.Op createNode() {
		return new MemNode();
	}

	@Override
	public Node.Op createNode(final Artifact.Op artifact) {
		checkNotNull(artifact);

		final Node.Op node = new MemNode();
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
