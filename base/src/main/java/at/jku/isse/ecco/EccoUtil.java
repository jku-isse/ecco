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
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

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
		Set<Artifact.Op<?>> touchedSourceArtifacts = Collections.newSetFromMap(new IdentityHashMap<>());
		Node.Op node2 = EccoUtil.deepCopyTreeRec(node, entityFactory, touchedSourceArtifacts);

		// updateArtifactReferences() (both here and PartialOrderGraph.Op.copy(), called from within
		// deepCopyTreeRec() above) still needs every touched source artifact's replacingArtifact -
		// e.g. a copied artifact's own sequence graph nodes are wired directly to the SOURCE line
		// artifacts by PartialOrderGraph.Op.copy(), and only get redirected to their copies here.
		Trees.updateArtifactReferences(node2);

		// replacingArtifact is only meant as a "have I already copied this artifact" cache scoped to
		// THIS deepCopyTree() call (see its javadoc on Artifact.Op) - clearing it now, once fully
		// consumed above, stops it from leaking into a LATER, unrelated deepCopyTree()/Trees.slice()
		// call that happens to encounter the same (e.g. shared/non-unique) source artifact object
		// again. Left uncleared, a subsequent call would either wrongly treat the source as "already
		// copied" (reusing a copy instance that belongs to a different, unrelated copy operation) or
		// trip Trees.slice()'s "replacing artifact should not itself have a replacing artifact" guard
		// - both observed in practice via fork()+local-commit()+push() (see
		// RemoteSyncCharacterizationTest/ReplacingArtifactLeakRegressionTest).
		for (Artifact.Op<?> touchedSourceArtifact : touchedSourceArtifacts) {
			touchedSourceArtifact.setReplacingArtifact(null);
		}

		return node2;
	}

	private static Node.Op deepCopyTreeRec(Node.Op node, EntityFactory entityFactory, Set<Artifact.Op<?>> touchedSourceArtifacts) {
		Node.Op node2;

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
				touchedSourceArtifacts.add(artifact);
				firstMatch = true;
			}

			if (node.isUnique()) {
				// createNode(Artifact.Op) -- unlike the no-arg createNode() + setArtifact() used
				// below for the non-unique case -- is what actually gives the copy a FeatureTrace
				// (see SerNode's no-arg-vs-artifact constructor javadoc); without it, a later
				// commit() walking this copy NPEs in RetroactiveConditionSetterVisitor the first
				// time it reaches a unique node. It also sets containingNode itself, which is
				// exactly what a unique node needs anyway (same as the explicit call this replaces).
				node2 = entityFactory.createNode(artifact2);
			} else {
				node2 = entityFactory.createNode();
				node2.setArtifact(artifact2);
			}
			node2.setUnique(node.isUnique());

			artifact2.setAtomic(artifact.isAtomic());
			artifact2.setOrdered(artifact.isOrdered());
			artifact2.setSequenceNumber(artifact.getSequenceNumber());

			// sequence graph
			if (artifact.getPartialOrderGraph() != null && firstMatch) {
				PartialOrderGraph.Op sequenceGraph = artifact.getPartialOrderGraph();
				PartialOrderGraph.Op sequenceGraph2 = artifact2.createSequenceGraph();

				artifact2.setPartialOrderGraph(sequenceGraph2);

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
			node2 = entityFactory.createNode();
			node2.setUnique(node.isUnique());
			node2.setArtifact(null);
		}

		for (Node.Op childNode : node.getChildren()) {
			Node.Op childNode2 = EccoUtil.deepCopyTreeRec(childNode, entityFactory, touchedSourceArtifacts);
			node2.addChild(childNode2);
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
