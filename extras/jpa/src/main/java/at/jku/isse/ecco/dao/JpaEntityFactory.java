package at.jku.isse.ecco.dao;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.artifact.ArtifactReference;
import at.jku.isse.ecco.artifact.JpaArtifact;
import at.jku.isse.ecco.artifact.JpaArtifactReference;
import at.jku.isse.ecco.core.*;
import at.jku.isse.ecco.feature.*;
import at.jku.isse.ecco.module.*;
import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.tree.JpaNode;
import at.jku.isse.ecco.tree.JpaRootNode;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.tree.RootNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class JpaEntityFactory implements EntityFactory {

	@Override
	public Configuration createConfiguration() {
		return new JpaConfiguration();
	}

	@Override
	public Variant createVariant() {
		return new JpaVariant();
	}

	@Override
	public Commit createCommit() {
		return new JpaCommit();
	}

	@Override
	public Checkout createCheckout() {
		return new BaseCheckout();
	}

	@Override
	public PresenceCondition createPresenceCondition() {
		return new JpaPresenceCondition();
	}

	@Override
	public PresenceCondition createPresenceCondition(Configuration configuration, int maxOrder) {
		return new JpaPresenceCondition(configuration, maxOrder);
	}

	@Override
	public <T extends ArtifactData> Artifact<T> createArtifact(T data) {
		return new JpaArtifact<T>(data);
	}

	@Override
	public ArtifactReference createArtifactReference(Artifact source, Artifact target) {
		checkNotNull(source);
		checkNotNull(target);

		final ArtifactReference reference = new JpaArtifactReference();
		reference.setSource(source);
		reference.setTarget(target);

		return reference;
	}

	@Override
	public ArtifactReference createArtifactReference(Artifact source, Artifact target, String type) {
		checkNotNull(source);
		checkNotNull(target);
		checkNotNull(type);

		final ArtifactReference reference = new JpaArtifactReference(type);
		reference.setSource(source);
		reference.setTarget(target);

		return reference;
	}

	@Override
	public Association createAssociation() {
		return new JpaAssociation();
	}

	@Override
	public Association createAssociation(PresenceCondition presenceCondition, Set<Node> nodes) {
		checkNotNull(presenceCondition);
		checkNotNull(nodes);

		final Association association = new JpaAssociation();

		RootNode rootNode = this.createRootNode();
		rootNode.setContainingAssociation(association);

		for (Node node : nodes) {
			rootNode.addChild(node);
		}

		association.setPresenceCondition(presenceCondition);
		association.setRootNode(rootNode);

		return association;
	}

	@Override
	public Feature createFeature(String name) {
		checkNotNull(name);
		checkArgument(!name.isEmpty(), "Expected a non-empty name but was empty.");

		return new JpaFeature(name);
	}

	@Override
	public Feature createFeature(String name, String description) {
		checkNotNull(name);
		checkArgument(!name.isEmpty(), "Expected a non-empty name but was empty.");
		checkNotNull(description);

		final Feature feature = new JpaFeature(name);
		feature.setDescription(description);

		return feature;
	}

	@Override
	public FeatureVersion createFeatureVersion(Feature feature, int version) {
		return new JpaFeatureVersion(feature, version);
	}

	@Override
	public FeatureInstance createFeatureInstance(Feature feature, FeatureVersion featureVersion, boolean sign) {
		checkNotNull(feature);
		checkNotNull(featureVersion);

		JpaFeatureInstance featureInstance = new JpaFeatureInstance(feature, featureVersion, sign);
		return featureInstance;
	}

	@Override
	public Module createModule() {
		return new JpaModule();
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
		return new JpaModuleFeature(feature, featureVersions, sign);
	}


	@Override
	public Node createNode() {
		return new JpaNode();
	}

	@Override
	public Node createNode(Artifact artifact) {
		checkNotNull(artifact);

		final Node node = new JpaNode();
		node.setArtifact(artifact);
		artifact.setContainingNode(node);

		return node;
	}

	@Override
	public RootNode createRootNode() {
		return new JpaRootNode();
	}

	@Override
	public Node createOrderedNode(Artifact artifact) {
		checkNotNull(artifact);

		final Node node = new JpaNode();
		node.setArtifact(artifact);
		artifact.setContainingNode(node);

		artifact.setOrdered(true);

		return node;
	}

	@Override
	public RootNode createRootNode(Association association) {
		checkNotNull(association);

		final RootNode root = new JpaRootNode();
		root.setContainingAssociation(association);

		return root;
	}

}
