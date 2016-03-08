package at.jku.isse.ecco.dao;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.artifact.ArtifactReference;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Checkout;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.core.Variant;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureInstance;
import at.jku.isse.ecco.feature.FeatureVersion;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.module.ModuleFeature;
import at.jku.isse.ecco.module.PresenceCondition;
import at.jku.isse.ecco.plugin.artifact.ArtifactData;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.tree.RootNode;

import java.util.Collection;
import java.util.Set;

public class MemEntityFactory implements EntityFactory {

	@Override
	public Configuration createConfiguration() {
		return null;
	}

	@Override
	public Variant createVariant() {
		return null;
	}

	@Override
	public Commit createCommit() {
		return null;
	}

	@Override
	public Checkout createCheckout() {
		return null;
	}

	@Override
	public PresenceCondition createPresenceCondition() {
		return null;
	}

	@Override
	public PresenceCondition createPresenceCondition(Configuration configuration, int maxOrder) {
		return null;
	}

	@Override
	public <T extends ArtifactData> Artifact<T> createArtifact(T data) {
		return null;
	}

	@Override
	public ArtifactReference createArtifactReference(Artifact source, Artifact target) {
		return null;
	}

	@Override
	public ArtifactReference createArtifactReference(Artifact source, Artifact target, String type) {
		return null;
	}

	@Override
	public Association createAssociation() {
		return null;
	}

	@Override
	public Association createAssociation(PresenceCondition presenceCondition, Set<Node> nodes) {
		return null;
	}

	@Override
	public Feature createFeature(String name) {
		return null;
	}

	@Override
	public Feature createFeature(String name, String description) {
		return null;
	}

	@Override
	public FeatureVersion createFeatureVersion(Feature feature, int version) {
		return null;
	}

	@Override
	public FeatureInstance createFeatureInstance(Feature feature, FeatureVersion featureVersion, boolean sign) {
		return null;
	}

	@Override
	public Module createModule() {
		return null;
	}

	@Override
	public ModuleFeature createModuleFeature(ModuleFeature moduleFeature) {
		return null;
	}

	@Override
	public ModuleFeature createModuleFeature(Feature feature, boolean sign) {
		return null;
	}

	@Override
	public ModuleFeature createModuleFeature(Feature feature, Collection<FeatureVersion> featureVersions, boolean sign) {
		return null;
	}

	@Override
	public Node createNode() {
		return null;
	}

	@Override
	public Node createNode(Artifact artifact) {
		return null;
	}

	@Override
	public RootNode createRootNode() {
		return null;
	}

	@Override
	public Node createOrderedNode(Artifact artifact) {
		return null;
	}

	@Override
	public RootNode createRootNode(Association association) {
		return null;
	}

}
