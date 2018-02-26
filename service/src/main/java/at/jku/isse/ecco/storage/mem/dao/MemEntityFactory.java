package at.jku.isse.ecco.storage.mem.dao;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.storage.mem.artifact.BaseArtifact;
import at.jku.isse.ecco.core.*;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.feature.*;
import at.jku.isse.ecco.module.*;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.storage.mem.core.BaseCommit;
import at.jku.isse.ecco.storage.mem.core.BaseRemote;
import at.jku.isse.ecco.storage.mem.feature.MemConfiguration;
import at.jku.isse.ecco.storage.mem.feature.MemFeature;
import at.jku.isse.ecco.storage.mem.module.BasePresenceCondition;
import at.jku.isse.ecco.storage.mem.repository.MemRepository;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.storage.mem.core.BaseAssociation;
import at.jku.isse.ecco.storage.mem.core.BaseVariant;
import at.jku.isse.ecco.storage.mem.module.MemModule;
import at.jku.isse.ecco.storage.mem.module.BaseModuleFeature;
import at.jku.isse.ecco.storage.mem.tree.BaseNode;
import at.jku.isse.ecco.storage.mem.tree.BaseRootNode;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.tree.RootNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class MemEntityFactory implements EntityFactory {

	@Override
	public Remote createRemote(String name, String address, Remote.Type type) {
		return new BaseRemote(name, address, type);
	}

	@Override
	public Configuration createConfiguration() {
		return new MemConfiguration();
	}

	@Override
	public Variant createVariant() {
		return new BaseVariant();
	}

	@Override
	public Commit createCommit() {
		return new BaseCommit();
	}

	@Override
	public PresenceCondition createPresenceCondition() {
		return new BasePresenceCondition();
	}

	@Override
	public PresenceCondition createPresenceCondition(Configuration configuration, int maxOrder) {
		return new BasePresenceCondition(configuration, maxOrder);
	}

	@Override
	public PresenceCondition createPresenceCondition(PresenceCondition pc) {
		BasePresenceCondition clone = new BasePresenceCondition();
		clone.getMinModules().addAll(pc.getMinModules());
		clone.getMaxModules().addAll(pc.getMaxModules());
		clone.getAllModules().addAll(pc.getAllModules());
		clone.getNotModules().addAll(pc.getNotModules());
		return clone;
	}


	// # ARTIFACTS ################################################################

	@Override
	public <T extends ArtifactData> Artifact.Op<T> createArtifact(T data) {
		return new BaseArtifact<T>(data);
	}


	// # ASSOCIATIONS ################################################################

	@Override
	public Association.Op createAssociation() {
		return new BaseAssociation();
	}

	@Override
	public Association.Op createAssociation(PresenceCondition presenceCondition, Set<Node.Op> nodes) {
		checkNotNull(presenceCondition);
		checkNotNull(nodes);

		final Association.Op association = new BaseAssociation();

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

		final Feature feature = new MemFeature(id, name, description);

		return feature;
	}

//	@Override
//	public FeatureInstance createFeatureInstance(Feature feature, FeatureVersion featureVersion, final boolean sign) {
//		checkNotNull(feature);
//		checkNotNull(featureVersion);
//
//		BaseFeatureInstance featureInstance = new BaseFeatureInstance(feature, featureVersion, sign);
//		return featureInstance;
//	}

	@Override
	public Module createModule() {
		return new MemModule();
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
	public ModuleFeature createModuleFeature(Feature feature, Collection<FeatureRevision> featureVersions, boolean sign) {
		return new BaseModuleFeature(feature, featureVersions, sign);
	}


	// # NODES ################################################################

	@Override
	public Node.Op createNode() {
		return new BaseNode();
	}

	@Override
	public Node.Op createNode(final Artifact.Op artifact) {
		checkNotNull(artifact);

		final Node.Op node = new BaseNode();
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

	@Override
	public RootNode.Op createRootNode() {
		return new BaseRootNode();
	}

	@Override
	public Repository.Op createRepository() {
		return new MemRepository();
	}

}
