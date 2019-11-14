package at.jku.isse.ecco;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.artifact.ArtifactReference;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.pog.PartialOrderGraph;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.util.Trees;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;

public class EccoUtil {

	private EccoUtil() {
	}


	public static Collection<Feature> deepCopyFeatures(Collection<? extends Feature> features, EntityFactory entityFactory) {
		Collection<Feature> copiedFeatures = new ArrayList<>();
		for (Feature feature : features) {
			Feature copiedFeature = entityFactory.createFeature(feature.getId(), feature.getName());
			copiedFeature.setDescription(feature.getDescription());

			for (FeatureRevision featureVersion : feature.getRevisions()) {
				FeatureRevision copiedFeatureVersion = copiedFeature.addRevision(featureVersion.getId());
				copiedFeatureVersion.setDescription(featureVersion.getDescription());
			}

			copiedFeatures.add(copiedFeature);
		}

		return copiedFeatures;
	}


	/**
	 * Creates a deep copy of a tree using the given entity factory.
	 *
	 * @param node          The tree to copy.
	 * @param entityFactory The entity factory to use for creating tree nodes and other necessary objects for the copied tree.
	 * @return The copied tree.
	 */
	public static Node.Op deepCopyTree(Node.Op node, EntityFactory entityFactory) {
		Node.Op node2 = EccoUtil.deepCopyTreeRec(node, entityFactory);

		Trees.updateArtifactReferences(node2);

		return node2;
	}

	private static Node.Op deepCopyTreeRec(Node.Op node, EntityFactory entityFactory) {
		Node.Op node2 = entityFactory.createNode();

		node2.setUnique(node.isUnique());

		if (node.getArtifact() != null) {
			Artifact.Op<?> artifact = node.getArtifact();
			Artifact.Op<?> artifact2;

			boolean firstMatch = false;
			if (artifact.hasReplacingArtifact()) {
				artifact2 = artifact.getReplacingArtifact();
				while (artifact2.hasReplacingArtifact()) {
					artifact2 = artifact2.getReplacingArtifact();
				}
			} else {
				artifact2 = entityFactory.createArtifact(artifact.getData());
				artifact.setReplacingArtifact(artifact2);
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
				PartialOrderGraph.Op sequenceGraph = artifact.getSequenceGraph();
				PartialOrderGraph.Op sequenceGraph2 = artifact2.createSequenceGraph();

				artifact2.setSequenceGraph(sequenceGraph2);

				// copy sequence graph
				sequenceGraph2.copy(sequenceGraph);
				//sequenceGraph2.sequence(sequenceGraph);
			}

			// TODO: make source and target artifacts both use the same artifact reference instance?
			// references
			// if the target has already been replaced set the uses artifact reference. if not, wait until the target is being processed and set it there as a usedBy. this way no reference is processed twice either. and if the target is never processed, then there is no inconsistent reference.
			if (firstMatch) {
				for (ArtifactReference.Op artifactReference : artifact.getUses()) {
//				ArtifactReference artifactReference2 = entityFactory.createArtifactReference(artifact2, artifactReference.getTarget(), artifactReference.getType());
//				artifact2.addUses(artifactReference2);

					if (artifactReference.getTarget().hasReplacingArtifact())
						artifact2.addUses(artifactReference.getTarget().getReplacingArtifact(), artifactReference.getType());
				}
				for (ArtifactReference.Op artifactReference : artifact.getUsedBy()) {
//				ArtifactReference artifactReference2 = entityFactory.createArtifactReference(artifactReference.getSource(), artifact2, artifactReference.getType());
//				artifact2.addUsedBy(artifactReference2);

					if (artifactReference.getSource().hasReplacingArtifact())
						artifactReference.getSource().getReplacingArtifact().addUses(artifact2, artifactReference.getType());
				}
			}

		} else {
			node2.setArtifact(null);
		}

		for (Node.Op childNode : node.getChildren()) {
			Node.Op childNode2 = EccoUtil.deepCopyTreeRec(childNode, entityFactory);
			node2.addChild(childNode2);
			//childNode2.setParent(node2); // not necessary
		}

		return node2;
	}


	public static String getSHA(Path path) {
		try {
			MessageDigest complete = MessageDigest.getInstance("SHA1");

			try (InputStream fis = Files.newInputStream(path)) {
				byte[] buffer = new byte[1024];
				int numRead = 0;
				while (numRead != -1) {
					numRead = fis.read(buffer);
					if (numRead > 0) {
						complete.update(buffer, 0, numRead);
					}
				}
			}
			BigInteger bi = new BigInteger(1, complete.digest());
			return bi.toString(16);
		} catch (IOException | NoSuchAlgorithmException e) {
			throw new EccoException("Could not compute hash for " + path, e);
		}
	}

}
