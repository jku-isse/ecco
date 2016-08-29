package at.jku.isse.ecco.dao;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.artifact.ArtifactReference;
import at.jku.isse.ecco.artifact.BaseArtifact;
import at.jku.isse.ecco.artifact.BaseArtifactReference;
import at.jku.isse.ecco.core.*;
import at.jku.isse.ecco.feature.*;
import at.jku.isse.ecco.module.*;
import at.jku.isse.ecco.plugin.artifact.ArtifactData;
import at.jku.isse.ecco.tree.BaseNode;
import at.jku.isse.ecco.tree.BaseRootNode;
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
		return new BaseConfiguration();
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
	public Checkout createCheckout() {
		return new BaseCheckout();
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
	public <T extends ArtifactData> Artifact<T> createArtifact(T data) {
		return new BaseArtifact<T>(data);
	}

	@Override
	public ArtifactReference createArtifactReference(final Artifact source, final Artifact target) {
		checkNotNull(source);
		checkNotNull(target);

		final ArtifactReference reference = new BaseArtifactReference();
		reference.setSource(source);
		reference.setTarget(target);

		return reference;
	}

	@Override
	public ArtifactReference createArtifactReference(final Artifact source, final Artifact target, final String type) {
		checkNotNull(source);
		checkNotNull(target);
		checkNotNull(type);

		final ArtifactReference reference = new BaseArtifactReference(type);
		reference.setSource(source);
		reference.setTarget(target);

		return reference;

	}


	// # ASSOCIATIONS ################################################################

	@Override
	public Association createAssociation() {
		return new BaseAssociation();
	}

	@Override
	public Association createAssociation(PresenceCondition presenceCondition, Set<Node> nodes) {
		checkNotNull(presenceCondition);
		checkNotNull(nodes);

		final Association association = new BaseAssociation();

		RootNode rootNode = this.createRootNode();
		rootNode.setContainingAssociation(association);

		for (Node node : nodes) {
			rootNode.addChild(node);
		}

		association.setPresenceCondition(presenceCondition);
		association.setRootNode(rootNode);

		return association;
	}


	// # FEATURES ################################################################

	@Override
	public Feature createFeature(final String name) {
		checkNotNull(name);
		checkArgument(!name.isEmpty(), "Expected a non-empty name but was empty.");

		return new BaseFeature(name);
	}

	@Override
	public Feature createFeature(final String name, final String description) {
		checkNotNull(name);
		checkArgument(!name.isEmpty(), "Expected a non-empty name but was empty.");
		checkNotNull(description);

		final Feature feature = new BaseFeature(name);
		feature.setDescription(description);

		return feature;
	}

	@Override
	public FeatureVersion createFeatureVersion(Feature feature, int version) {
		return new BaseFeatureVersion(feature, version);
	}

	@Override
	public FeatureInstance createFeatureInstance(Feature feature, FeatureVersion featureVersion, final boolean sign) {
		checkNotNull(feature);
		checkNotNull(featureVersion);

		BaseFeatureInstance featureInstance = new BaseFeatureInstance(feature, featureVersion, sign);
		return featureInstance;
	}

	@Override
	public Module createModule() {
		return new BaseModule();
	}

	@Override
	public ModuleFeature createModuleFeature(ModuleFeature moduleFeature) {
		return this.createModuleFeature(moduleFeature.getFeature(), moduleFeature, moduleFeature.getSign());
	}

	@Override
	public ModuleFeature createModuleFeature(Feature feature, boolean sign) {
		return this.createModuleFeature(feature, new ArrayList<>(), sign);
	}

	@Override
	public ModuleFeature createModuleFeature(Feature feature, Collection<FeatureVersion> featureVersions, boolean sign) {
		return new BaseModuleFeature(feature, featureVersions, sign);
	}


	// # NODES ################################################################

	@Override
	public Node createNode() {
		return new BaseNode();
	}

	@Override
	public Node createNode(final Artifact artifact) {
		checkNotNull(artifact);

		final Node node = new BaseNode();
		node.setArtifact(artifact);
		artifact.setContainingNode(node);

		return node;
	}

	@Override
	public Node createNode(ArtifactData artifactData) {
		return this.createNode(this.createArtifact(artifactData));
	}

	@Override
	public Node createOrderedNode(final Artifact artifact) {
		checkNotNull(artifact);

		final Node node = new BaseNode();
		node.setArtifact(artifact);
		artifact.setContainingNode(node);

		artifact.setOrdered(true);

		return node;
	}

	@Override
	public Node createOrderedNode(ArtifactData artifactData) {
		return this.createOrderedNode(this.createArtifact(artifactData));
	}

	@Override
	public RootNode createRootNode() {
		return new BaseRootNode();
	}

	@Override
	public RootNode createRootNode(final Association association) {
		checkNotNull(association);

		final RootNode root = new BaseRootNode();
		root.setContainingAssociation(association);

		return root;
	}

}
