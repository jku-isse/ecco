package at.jku.isse.ecco.dao;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.artifact.ArtifactReference;
import at.jku.isse.ecco.artifact.PerstArtifact;
import at.jku.isse.ecco.artifact.PerstArtifactReference;
import at.jku.isse.ecco.core.*;
import at.jku.isse.ecco.feature.*;
import at.jku.isse.ecco.module.*;
import at.jku.isse.ecco.plugin.artifact.ArtifactData;
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
	public Checkout createCheckout() {
		return new PerstCheckout();
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
	public <T extends ArtifactData> Artifact<T> createArtifact(T data) {
		return new PerstArtifact<T>(data);
	}

	@Override
	public ArtifactReference createArtifactReference(final Artifact source, final Artifact target) {
		checkNotNull(source);
		checkNotNull(target);

		final ArtifactReference reference = new PerstArtifactReference();
		reference.setSource(source);
		reference.setTarget(target);

		return reference;
	}

	@Override
	public ArtifactReference createArtifactReference(final Artifact source, final Artifact target, final String type) {
		checkNotNull(source);
		checkNotNull(target);
//		checkNotNull(type);

		final ArtifactReference reference = new PerstArtifactReference(type);
		reference.setSource(source);
		reference.setTarget(target);

		return reference;

	}


	// # ASSOCIATIONS ################################################################

	@Override
	public Association createAssociation() {
		return new PerstAssociation();
	}

	@Override
	public Association createAssociation(PresenceCondition presenceCondition, Set<Node> nodes) {
		checkNotNull(presenceCondition);
		checkNotNull(nodes);

		final Association association = new PerstAssociation();

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

		return new PerstFeature(name);
	}

	@Override
	public Feature createFeature(final String name, final String description) {
		checkNotNull(name);
		checkArgument(!name.isEmpty(), "Expected a non-empty name but was empty.");
		checkNotNull(description);

		final Feature feature = new PerstFeature(name);
		feature.setDescription(description);

		return feature;
	}

	@Override
	public FeatureVersion createFeatureVersion(Feature feature, int version) {
		return new PerstFeatureVersion(feature, version);
	}

	@Override
	public FeatureInstance createFeatureInstance(Feature feature, FeatureVersion featureVersion, final boolean sign) {
		checkNotNull(feature);
		checkNotNull(featureVersion);

		PerstFeatureInstance featureInstance = new PerstFeatureInstance(feature, featureVersion, sign);
		return featureInstance;
	}

	@Override
	public Module createModule() {
		return new PerstModule();
	}

//	@Override
//	public Module createModule(final Set<FeatureInstance> featureInstances) {
//		checkNotNull(featureInstances);
//
//		final Module module = new PerstModule();
//		module.addAll(featureInstances);
//
//		return module;
//	}

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
		return new PerstModuleFeature(feature, featureVersions, sign);
	}


	// # NODES ################################################################

	@Override
	public Node createNode() {
		return new PerstNode();
	}

	@Override
	public Node createNode(final Artifact artifact) {
		checkNotNull(artifact);

		final Node node = new PerstNode();
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

		final Node node = new PerstNode();
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
		return new PerstRootNode();
	}

	@Override
	public RootNode createRootNode(final Association association) {
		checkNotNull(association);

		final RootNode root = new PerstRootNode();
		root.setContainingAssociation(association);

		return root;
	}


//	/**
//	 * Creates a new {@link PerstFeature} from the given feature. If it is already an instance of <code>PerstFeature</code> than the instance will be cast and returned.
//	 *
//	 * @param feature that should be used to create a new perst feature.
//	 * @return A new perst feature instance containing the properties of the given feature, or the cast of the given feature.
//	 */
//	public PerstFeature createPerstFeature(final Feature feature) {
//		checkNotNull(feature);
//
//		if (feature instanceof PerstFeature) {
//			return (PerstFeature) feature;
//		}
//
//		final PerstFeature perstFeature = new PerstFeature();
//
//		perstFeature.setDescription(feature.getDescription());
//		if (!feature.getName().isEmpty()) {
//			perstFeature.setName(feature.getName());
//		}
//
//		return perstFeature;
//	}

//	/**
//	 * Creates a new {@link PerstAssociation} from the given association. If it is already an instance of <code>PerstAssociation</code> than the instance will be cast and returned.
//	 *
//	 * @param association that should be used to create a new perst association.
//	 * @return A new perst association instance containing the properties of the given association, or the cast of the given association.
//	 */
//	public PerstAssociation createPerstAssociation(final Association association) {
//		checkNotNull(association);
//
//		if (association instanceof PerstAssociation) {
//			return (PerstAssociation) association;
//		}
//
//		final PerstAssociation perstAssociation = new PerstAssociation();
//
//		// do "translation"
//
//		return perstAssociation;
//	}

}
