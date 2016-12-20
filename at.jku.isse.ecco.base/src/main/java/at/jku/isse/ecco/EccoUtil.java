package at.jku.isse.ecco;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.artifact.ArtifactReference;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureVersion;
import at.jku.isse.ecco.sg.SequenceGraph;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.util.Trees;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

public class EccoUtil {

	private EccoUtil() {
	}


	public static Collection<Feature> deepCopyFeatures(Collection<? extends Feature> features, EntityFactory entityFactory) {
		Collection<Feature> copiedFeatures = new ArrayList<>();
		for (Feature feature : features) {
			Feature copiedFeature = entityFactory.createFeature(feature.getId(), feature.getName(), feature.getDescription());

			for (FeatureVersion featureVersion : feature.getVersions()) {
				FeatureVersion copiedFeatureVersion = copiedFeature.addVersion(featureVersion.getId());
				copiedFeatureVersion.setDescription(featureVersion.getDescription());
			}

			copiedFeatures.add(copiedFeature);
		}

		return copiedFeatures;
	}


	/**
	 * Trims all sequence graphs in the given set of associations by removing all artifacts that are not part of the given associations.
	 * Note:
	 * Should being part of an association in this case means solid as well as not solid?
	 * While it should not happen that an artifact is not contained in any of the associations solid (because that would violate dependencies) it could theoretically happen.
	 *
	 * @param associations Associations that contain artifacts to retain in the sequence graphs.
	 */
	public static void trimSequenceGraph(Collection<Association> associations) {
		for (Association association : associations) {
			EccoUtil.trimSequenceGraphRec(associations, association.getRootNode());
		}
	}

	private static void trimSequenceGraphRec(Collection<Association> associations, Node node) {

		if (node.isUnique() && node.getArtifact() != null && node.getArtifact().getSequenceGraph() != null) {
			// get all symbols from sequence graph
			Collection<Artifact<?>> symbols = node.getArtifact().getSequenceGraph().getSymbols();

			// remove symbols that are not contained in the given associations
			Iterator<Artifact<?>> symbolsIterator = symbols.iterator();
			while (symbolsIterator.hasNext()) {
				Artifact<?> symbol = symbolsIterator.next();
				if (!associations.contains(symbol.getContainingNode().getContainingAssociation())) {
					symbolsIterator.remove();
				}
			}

			// trim sequence graph
			node.getArtifact().getSequenceGraph().trim(symbols);
		}

		for (Node child : node.getChildren()) {
			EccoUtil.trimSequenceGraphRec(associations, child);
		}
	}


	/**
	 * Creates a deep copy of a tree using the given entity factory.
	 *
	 * @param node
	 * @param entityFactory
	 * @return
	 */
	public static Node deepCopyTree(Node node, EntityFactory entityFactory) {
		Node node2 = EccoUtil.deepCopyTreeRec(node, entityFactory);

		Trees.updateArtifactReferences(node2);

		return node2;
	}

	private static Node deepCopyTreeRec(Node node, EntityFactory entityFactory) {
		Node node2 = entityFactory.createNode();

		node2.setUnique(node.isUnique());

		if (node.getArtifact() != null) {
			Artifact<?> artifact = node.getArtifact();
			Artifact<?> artifact2;

			boolean firstMatch = false;
			if (artifact.getProperty(Artifact.PROPERTY_REPLACING_ARTIFACT).isPresent()) {
				artifact2 = artifact.<Artifact<?>>getProperty(Artifact.PROPERTY_REPLACING_ARTIFACT).get();
			} else {
				artifact2 = entityFactory.createArtifact(artifact.getData());
				artifact.putProperty(Artifact.PROPERTY_REPLACING_ARTIFACT, artifact2);
				firstMatch = true;
			}

			node2.setArtifact(artifact2);

			if (node.isUnique()) {
				artifact2.setContainingNode(node2);
			}

			artifact2.setAtomic(artifact.isAtomic());
			artifact2.setOrdered(artifact.isOrdered());
			artifact2.setSequenceNumber(artifact.getSequenceNumber());

			// sequence graph
			if (artifact.getSequenceGraph() != null && firstMatch) {
				SequenceGraph sequenceGraph = artifact.getSequenceGraph();
				SequenceGraph sequenceGraph2 = artifact2.createSequenceGraph();

				artifact2.setSequenceGraph(sequenceGraph2);

				// copy sequence graph
				sequenceGraph2.copy(sequenceGraph);
				//sequenceGraph2.sequence(sequenceGraph);
			}

			// TODO: make source and target artifacts both use the same artifact reference instance?
			// references
			for (ArtifactReference artifactReference : artifact.getUses()) {
				ArtifactReference artifactReference2 = entityFactory.createArtifactReference(artifact2, artifactReference.getTarget(), artifactReference.getType());
				artifact2.addUses(artifactReference2);
			}
			for (ArtifactReference artifactReference : artifact.getUsedBy()) {
				ArtifactReference artifactReference2 = entityFactory.createArtifactReference(artifactReference.getSource(), artifact2, artifactReference.getType());
				artifact2.addUsedBy(artifactReference2);
			}

		} else {
			node2.setArtifact(null);
		}

		for (Node childNode : node.getChildren()) {
			Node childNode2 = EccoUtil.deepCopyTreeRec(childNode, entityFactory);
			node2.addChild(childNode2);
			childNode2.setParent(node2);
		}

		return node2;
	}

}
