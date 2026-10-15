package at.jku.isse.ecco.storage.ser.dao;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.artifact.ArtifactReference;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.counter.ModuleCounter;
import at.jku.isse.ecco.counter.ModuleRevisionCounter;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.module.ModuleRevision;
import at.jku.isse.ecco.pog.PartialOrderGraph;
import at.jku.isse.ecco.storage.ser.artifact.SerArtifact;
import at.jku.isse.ecco.storage.ser.artifact.SerArtifactReference;
import at.jku.isse.ecco.storage.ser.counter.SerAssociationCounter;
import at.jku.isse.ecco.storage.ser.counter.SerModuleCounter;
import at.jku.isse.ecco.storage.ser.counter.SerModuleRevisionCounter;
import at.jku.isse.ecco.storage.ser.module.SerModule;
import at.jku.isse.ecco.storage.ser.module.SerModuleRevision;
import at.jku.isse.ecco.storage.ser.pog.SerPartialOrderGraphNode;
import at.jku.isse.ecco.storage.ser.repository.SerRepository;
import at.jku.isse.ecco.storage.ser.tree.SerNode;
import at.jku.isse.ecco.tree.Node;

import java.util.HashMap;
import java.util.Map;

/**
 * After a repository's artifacts and associations were read, each from its own file: the
 * references between them, which their files carry as ids (or not at all), are made object
 * references again - see {@link SerTransactionStrategy}'s loading.
 */
final class LoadResolver {

	private LoadResolver() {
	}

	/**
	 * Each association was just deserialized from its own, independent file/stream, so any
	 * reference that points OUTSIDE that association's own tree (SerNode.artifactId,
	 * SerArtifactReference.source/target - transient, carrying only an id; and
	 * SerArtifact.containingNode, not persisted at all but derived from the trees) was not restored
	 * by the default deserialization of that file. artifactsById comes
	 * directly from the global artifact store (restoreArtifacts(), already loaded above) rather than
	 * being harvested by walking nodes - that's what actually fixes
	 * the duplicated-shared-artifact bug, since it means there is structurally exactly one
	 * instance per artifact id, not "whichever association's independently-deserialized copy
	 * happened to be indexed last". This walks every loaded association's tree once to wire each
	 * node's artifactId to that instance (and each artifact's containing node to the unique node
	 * holding it), then fills in the remaining transient fields - always resolving to a real, properly-reconstructed
	 * instance, never a dangling or independently-duplicated fragment. See
	 * TreesObjectIdentityDependencyTest and the node sharing across associations that broke per-association writing for why
	 * this matters.
	 */
	static void resolveCrossAssociationReferences(SerRepository repo) {
		Map<String, Artifact.Op<?>> artifactsById = new HashMap<>();
		for (String artifactId : repo.getArtifactIds()) {
			Artifact.Op<?> artifact = repo.getArtifact(artifactId);
			if (artifact != null) {
				artifactsById.put(artifactId, artifact);
			}
		}

		for (Association.Op association : repo.getAssociations()) {
			if (association.getRootNode() != null) {
				resolveNodeArtifacts(association.getRootNode(), artifactsById);
			}
		}

		for (Artifact.Op<?> artifact : artifactsById.values()) {
			if (!(artifact instanceof SerArtifact<?> serArtifact)) continue;

			resolveReferences(serArtifact.getUses(), artifactsById);
			resolveReferences(serArtifact.getUsedBy(), artifactsById);

			if (serArtifact.getPartialOrderGraph() != null) {
				resolvePartialOrderGraph(serArtifact.getPartialOrderGraph(), artifactsById);
			}
		}
	}

	/**
	 * Each association's AssociationCounter -> ModuleCounter -> ModuleRevisionCounter chain holds
	 * direct (non-transient) references to Module/ModuleRevision - deserialized independently once
	 * per association file (SerModuleCounter.module, SerModuleRevisionCounter.moduleRevision), so
	 * every association ends up with its own data-equal-but-object-distinct copy instead of sharing
	 * the repository's one canonical instance (SerRepository.modules/features, part of the "core"
	 * blob, not split into per-association files). Association.computeCondition() reads a mutable
	 * count directly off whichever ModuleRevision instance a counter happens to reference
	 * (moduleRevisionCounter.getObject().getCount()), not off the per-association counter itself -
	 * so that divergence corrupts presence-condition computation for every association after any
	 * reload, in a way a single continuous session never exhibits (the repository's registry is the
	 * only source of Module/ModuleRevision objects there, so everything shares by construction).
	 * Unlike artifacts/nodes, Module/ModuleRevision equality is already content-based (feature id
	 * strings all the way down), so no id surrogate is needed - resolving is just replacing each
	 * counter's reference with the repository's own lookup result.
	 */
	static void resolveModuleReferences(SerRepository repo) {
		// counters read in compact form are built on the repository's modules right away; only
		// those read in the old form hold copies to replace
		for (Association.Op association : repo.getAssociations()) {
			if (association.getCounter() instanceof SerAssociationCounter serAssociationCounter
					&& serAssociationCounter.resolveCompactChildren(repo))
				continue;
			for (ModuleCounter moduleCounter : association.getCounter().getChildren()) {
				if (!(moduleCounter instanceof SerModuleCounter serModuleCounter)) continue;

				Module module = moduleCounter.getObject();
				SerModule canonicalModule = repo.getModule(module.getPos(), module.getNeg());
				if (canonicalModule != null) {
					serModuleCounter.resolveModule(canonicalModule);
				}
				Module lookupModule = canonicalModule != null ? canonicalModule : module;

				for (ModuleRevisionCounter moduleRevisionCounter : moduleCounter.getChildren()) {
					if (!(moduleRevisionCounter instanceof SerModuleRevisionCounter serModuleRevisionCounter)) continue;

					ModuleRevision moduleRevision = moduleRevisionCounter.getObject();
					ModuleRevision canonicalModuleRevision = lookupModule.getRevision(moduleRevision.getPos(), moduleRevision.getNeg());
					if (canonicalModuleRevision instanceof SerModuleRevision serCanonicalModuleRevision) {
						serModuleRevisionCounter.resolveModuleRevision(serCanonicalModuleRevision);
					}
				}
			}
		}
	}

	/**
	 * PartialOrderGraphs get merged across nodes that can belong to different associations
	 * (Trees.slice(), Trees.java:115), so a POG node's artifact (SerPartialOrderGraphNode.artifact)
	 * can be a foreign, side-channel-serialized reference for the same reason
	 * SerArtifact.containingNode was - resolved here against the same global artifact-id index.
	 */
	private static void resolvePartialOrderGraph(PartialOrderGraph.Op graph, Map<String, Artifact.Op<?>> artifactsById) {
		for (PartialOrderGraph.Node.Op node : graph.collectNodes()) {
			if (!(node instanceof SerPartialOrderGraphNode serNode)) continue;

			String artifactId = serNode.getArtifactId();
			if (artifactId == null) continue; // head/tail sentinel nodes have no artifact

			Artifact.Op<?> artifact = artifactsById.get(artifactId);
			if (artifact == null) {
				throw new EccoException("Could not resolve POG node artifact " + artifactId + " after loading all associations.");
			}
			serNode.resolveArtifact(artifact);
		}
	}

	private static void resolveNodeArtifacts(Node.Op node, Map<String, Artifact.Op<?>> artifactsById) {
		if (node instanceof SerNode serNode) {

			String artifactId = serNode.getArtifactId();
			if (artifactId != null) {
				Artifact.Op<?> artifact = artifactsById.get(artifactId);
				if (artifact == null) {
					throw new EccoException("Could not resolve node artifact " + artifactId + " after loading all associations.");
				}
				serNode.resolveArtifact(artifact);
				// an artifact's containing node is the unique node holding it - derived here rather than
				// persisted (see SerArtifact's containingNode field); verified to equal the previously
				// persisted value on every repository examined
				if (node.isUnique() && artifact instanceof SerArtifact<?> serArtifact)
					serArtifact.resolveContainingNode(node);
			}
		}
		for (Node.Op child : node.getChildren()) {
			resolveNodeArtifacts(child, artifactsById);
		}
	}

	private static void resolveReferences(Iterable<ArtifactReference.Op> references, Map<String, Artifact.Op<?>> artifactsById) {
		for (ArtifactReference.Op reference : references) {
			if (!(reference instanceof SerArtifactReference serReference)) continue;

			Artifact.Op<?> source = artifactsById.get(serReference.getSourceId());
			Artifact.Op<?> target = artifactsById.get(serReference.getTargetId());
			if (source == null || target == null) {
				throw new EccoException("Could not resolve artifact reference (source=" + serReference.getSourceId() + ", target=" + serReference.getTargetId() + ") after loading all associations.");
			}
			serReference.resolveReferences(source, target);
		}
	}
}
