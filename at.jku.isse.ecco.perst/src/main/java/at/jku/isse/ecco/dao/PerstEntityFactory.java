package at.jku.isse.ecco.dao;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.artifact.PerstArtifact;
import at.jku.isse.ecco.core.*;
import at.jku.isse.ecco.feature.*;
import at.jku.isse.ecco.module.*;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.repository.PerstRepository;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.tree.PerstNode;
import at.jku.isse.ecco.tree.PerstRootNode;
import at.jku.isse.ecco.tree.RootNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Creates database entities defined using Perst implementations.
 *
 * @author Hannes Thaller
 * @version 1.0
 */
public class PerstEntityFactory implements EntityFactory {

	public PerstEntityFactory() {
	}

	@Override
	public Remote createRemote(String name, String address, Remote.Type type) {
		return new PerstRemote(name, address, type);
	}

	@Override
	public Configuration createConfiguration() {
		return new PerstConfiguration();
	}

	@Override
	public Variant createVariant() {
		return new PerstVariant();
	}

	@Override
	public Commit createCommit() {
		return new PerstCommit();
	}

	@Override
	public PresenceCondition createPresenceCondition() {
		return new PerstPresenceCondition();
	}

	@Override
	public PresenceCondition createPresenceCondition(Configuration configuration, int maxOrder) {
		return new PerstPresenceCondition(configuration, maxOrder);
	}

	@Override
	public PresenceCondition createPresenceCondition(PresenceCondition pc) {
		PerstPresenceCondition clone = new PerstPresenceCondition(); // TODO: reuse module instances!? when modules are modified, all associations that contain the are affected!
		clone.getMinModules().addAll(pc.getMinModules());
		clone.getMaxModules().addAll(pc.getMaxModules());
		clone.getAllModules().addAll(pc.getAllModules());
		clone.getNotModules().addAll(pc.getNotModules());
		return clone;
	}


	// # ARTIFACTS ################################################################

	@Override
	public <T extends ArtifactData> Artifact.Op<T> createArtifact(T data) {
		return new PerstArtifact<T>(data);
	}


	// # ASSOCIATIONS ################################################################

	@Override
	public Association.Op createAssociation() {
		return new PerstAssociation();
	}

	@Override
	public Association.Op createAssociation(PresenceCondition presenceCondition, Set<Node.Op> nodes) {
		checkNotNull(presenceCondition);
		checkNotNull(nodes);

		final Association.Op association = new PerstAssociation();

		RootNode.Op rootNode = this.createRootNode();
		rootNode.setContainingAssociation(association);

		for (Node.Op node : nodes) {
			rootNode.addChild(node);
		}

		association.setPresenceCondition(presenceCondition);
		association.setRootNode(rootNode);

		return association;
	}


	// # FEATURES ################################################################

	@Override
	public Feature createFeature(final String id, final String name, final String description) {
		checkNotNull(name);
		checkArgument(!name.isEmpty(), "Expected a non-empty name but was empty.");
		checkNotNull(description);

		final Feature feature = new PerstFeature(id, name, description);

		return feature;
	}

//	@Override
//	public FeatureInstance createFeatureInstance(Feature feature, FeatureVersion featureVersion, final boolean sign) {
//		checkNotNull(feature);
//		checkNotNull(featureVersion);
//
//		PerstFeatureInstance featureInstance = new PerstFeatureInstance(feature, featureVersion, sign);
//		return featureInstance;
//	}

	@Override
	public Module createModule() {
		return new PerstModule();
	}

//	@Override
//	public ModuleFeature createModuleFeature(ModuleFeature moduleFeature) {
//		return this.createModuleFeature(moduleFeature.getFeature(), moduleFeature, moduleFeature.getSign());
//	}

	@Override
	public ModuleFeature createModuleFeature(Feature feature, boolean sign) {
		return this.createModuleFeature(feature, new ArrayList<>(), sign);
	}

	@Override
	public ModuleFeature createModuleFeature(Feature feature, Collection<FeatureVersion> featureVersions, boolean sign) {
		return new PerstModuleFeature(feature, featureVersions, sign);
	}


	// # NODES ################################################################

	@Override
	public Node.Op createNode() {
		return new PerstNode();
	}

	@Override
	public Node.Op createNode(final Artifact.Op<?> artifact) {
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
	public Node.Op createOrderedNode(final Artifact.Op<?> artifact) {
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
	public RootNode.Op createRootNode() {
		return new PerstRootNode();
	}


	@Override
	public Repository.Op createRepository() {
		return new PerstRepository();
	}

}
