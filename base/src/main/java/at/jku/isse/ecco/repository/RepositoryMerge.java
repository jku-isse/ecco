package at.jku.isse.ecco.repository;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.dao.EccoUtil;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.core.*;
import at.jku.isse.ecco.counter.ModuleCounter;
import at.jku.isse.ecco.counter.ModuleRevisionCounter;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureLiterals;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.module.ModuleRevision;
import at.jku.isse.ecco.pog.PartialOrderGraph;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.tree.RootNode;
import at.jku.isse.ecco.tree.Trees;
import java.util.*;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * The merge of another repository into one, of the decisions taken in it, and the checks before - moved out of {@link Repository.Op}, whose default methods delegate here.
 */
final class RepositoryMerge {

	private RepositoryMerge() {
	}

	/** See {@link Repository.Op#checkArtifactFormats}. */
	static void checkArtifactFormats(Repository.Op repository) {
		for (Association.Op association : repository.getAssociations()) {
			if (association.getRootNode() != null)
				checkArtifactFormats(association.getRootNode());
		}
	}

	static void checkArtifactFormats(Node.Op node) {
		if (node.getArtifact() != null && node.getArtifact().getData() != null) {
			ArtifactData data = node.getArtifact().getData();
			if (!node.getArtifact().isOrdered() && data.requiresOrderedArtifact()) {
				throw new EccoException("This repository holds " + data.getClass().getSimpleName()
						+ " artifacts written by an older version of their adapter, which did not keep the order of"
						+ " their children (e.g. the cases of a TypeScript switch). Adding to it would make checkouts"
						+ " lose them - re-create the repository by committing its variants with this version.");
			}
			if (data.retiredFormat() != null) {
				throw new EccoException("This repository holds artifacts written by an older version of their adapter: "
						+ data.retiredFormat() + " Adding to it would mix both - re-create the repository by committing"
						+ " its variants with this version (checking out of it still works).");
			}
		}
		for (Node.Op child : node.getChildren())
			checkArtifactFormats(child);
	}

	/** See {@link Repository.Op#mergeDecisions}. */
	static void mergeDecisions(Repository.Op repository, Repository.Op otherRepository) {
		// copy every constraint from other repository into this one that doesn't already exist
		// here (by natural kind|a|b id). Kept deliberately independent of the module/association
		// counter-copying logic below (which has known fragility under certain conditions -- see
		// the data loss of pruning at write time), since constraints have no
		// counters or fusion participation to inherit that fragility from.
		// A suggestion is accepted, rejected or neither: one this repository has already decided
		// either way keeps its decision, the other repository's decision only fills the gaps.
		for (Constraint otherConstraint : otherRepository.getConstraints()) {
			if (repository.getConstraint(otherConstraint.getId()) == null && repository.getRejectedConstraint(otherConstraint.getId()) == null) {
				repository.addConstraint(otherConstraint.getKind(), otherConstraint.getFeatureA(), otherConstraint.getFeatureB());
			}
		}
		for (Constraint otherRejected : otherRepository.getRejectedConstraints()) {
			if (repository.getConstraint(otherRejected.getId()) == null && repository.getRejectedConstraint(otherRejected.getId()) == null) {
				repository.addRejectedConstraint(otherRejected.getKind(), otherRejected.getFeatureA(), otherRejected.getFeatureB());
			}
		}
		// parents likewise: a feature this repository gave a parent keeps it, a parent it rejected
		// is not taken - nor one that would close a cycle with its own
		otherRepository.getFeatureParents().forEach((feature, parent) -> {
			if (!repository.getFeatureParents().containsKey(feature)
					&& !repository.getRejectedFeatureParents().getOrDefault(feature, Set.of()).contains(parent)
					&& !Repository.makesCycle(repository.getFeatureParents(), feature, parent))
				repository.setFeatureParent(feature, parent);
		});
		otherRepository.getRejectedFeatureParents().forEach((feature, parents) -> parents.forEach(parent -> {
			if (!parent.equals(repository.getFeatureParents().get(feature)))
				repository.addRejectedFeatureParent(feature, parent);
		}));
	}

	/** See {@link Repository.Op#merge}. */
	static void merge(Repository.Op repository, Repository.Op otherRepository) {
		checkNotNull(otherRepository);
		repository.checkArtifactFormats();
		otherRepository.checkArtifactFormats();
		List<FeatureRevision> otherRevisions = new ArrayList<>();
		for (Feature otherFeature : otherRepository.getFeatures())
			otherRevisions.addAll(otherFeature.getRevisions());
		FeatureLiterals.checkDistinct(repository.getFeatures(), otherRevisions);

		repository.mergeDecisions(otherRepository);

		// extract every association. treat it as if it was an input product. only that there is no configuration.

		// add features (that have at least one revision) and feature revisions in other repository to this repository
		for (Feature otherFeature : otherRepository.getFeatures()) {
			if (!otherFeature.getRevisions().isEmpty()) {
				Feature repoFeature = repository.getFeature(otherFeature.getId());
				if (repoFeature == null) {
					repoFeature = repository.addFeature(otherFeature.getId(), otherFeature.getName());
					repoFeature.setDescription(otherFeature.getDescription());

					repository.addNegativeFeatureModules(repoFeature);
				}
				for (FeatureRevision otherFeatureRevision : otherFeature.getRevisions()) {
					FeatureRevision repoFeatureRevision = repoFeature.getRevision(otherFeatureRevision.getId());
					if (repoFeatureRevision == null) {
						repoFeatureRevision = repoFeature.addRevision(otherFeatureRevision.getId());
						repoFeatureRevision.setDescription(otherFeatureRevision.getDescription());
					}
				}
			}
		}

		// add features in this repository that are not in other repository negatively to other repository
		Set<Feature> negFeaturesWithoutRevisions = new HashSet<>();
		for (Feature feature : repository.getFeatures()) {
			if (otherRepository.getFeature(feature.getId()) == null) {
				Feature otherFeature = otherRepository.addFeature(feature.getId(), feature.getName());
				otherFeature.setDescription(feature.getDescription());
				otherRepository.addNegativeFeatureModules(otherFeature);
				negFeaturesWithoutRevisions.add(otherFeature);
			}
		}

		// add modules and module revisions (i.e. add new ones and increase the counters of existing ones)
		for (int order = 0; order <= repository.getMaxOrder(); order++) {
			for (final Module otherModule : otherRepository.getModules(order)) {
				// check if module contains any negative features without any revisions
				if (Arrays.stream(otherModule.getNeg()).noneMatch(feature -> feature.getRevisions().isEmpty() && !negFeaturesWithoutRevisions.contains(feature))) {
					// add module to this repository and increase its counter
					Module repoModule = repository.getModule(otherModule.getPos(), otherModule.getNeg());
					if (repoModule == null) {
						repoModule = repository.addModule(Arrays.stream(otherModule.getPos()).map(feature -> repository.getFeature(feature.getId())).toArray(Feature[]::new), Arrays.stream(otherModule.getNeg()).map(feature -> repository.getFeature(feature.getId())).toArray(Feature[]::new));
					}
					repoModule.incCount(otherModule.getCount());
					for (final ModuleRevision otherModuleRevision : otherModule.getRevisions()) {
						ModuleRevision repoModuleRevision = repoModule.getRevision(otherModuleRevision.getPos(), otherModuleRevision.getNeg());
						if (repoModuleRevision == null) {
							repoModuleRevision = repoModule.addRevision(Arrays.stream(otherModuleRevision.getPos()).map(featureRevision -> repository.getFeature(featureRevision.getFeature().getId()).getRevision(featureRevision.getId())).toArray(FeatureRevision[]::new), repoModule.getNeg());
						}
						repoModuleRevision.incCount(otherModuleRevision.getCount());
					}
				}
			}
		}

		// copy the other repository's artifact trees, all in one context: an artifact several of its
		// associations hold keeps one copy (see EccoUtil.TreeCopy)
		EccoUtil.TreeCopy treeCopy = new EccoUtil.TreeCopy(repository.getEntityFactory());
		Map<Association.Op, RootNode.Op> copiedRootNodes = new LinkedHashMap<>();
		for (Association.Op otherAssociation : otherRepository.getAssociations()) {
			RootNode.Op copiedRootNode = repository.getEntityFactory().createRootNode();
			for (Node.Op otherChildNode : otherAssociation.getRootNode().getChildren()) {
				Node.Op copiedChildNode = treeCopy.copy(otherChildNode);
				copiedRootNode.addChild(copiedChildNode);
				copiedChildNode.setParent(copiedRootNode);
			}
			copiedRootNodes.put(otherAssociation, copiedRootNode);
		}
		treeCopy.finish(copiedRootNodes.values());

		// for every association in other repository
		for (Association.Op otherAssociation : otherRepository.getAssociations()) {
			// prepare new associations for commit
			Association.Op association = repository.getEntityFactory().createAssociation();
			association.setId(UUID.randomUUID().toString());

			// its copied artifact tree
			RootNode.Op copiedRootNode = copiedRootNodes.get(otherAssociation);
			// a copy an association extracted before was matched to an artifact of this repository
			// (Trees.slice() replaced it): this tree holds that artifact too
			adoptReplacingArtifacts(copiedRootNode);
			association.setRootNode(copiedRootNode); // TODO: have association implementation take care of creating root node in constructor.


			// set association counter
			association.getCounter().setCount(otherAssociation.getCounter().getCount());

			for (ModuleCounter otherModuleCounter : otherAssociation.getCounter().getChildren()) {
				// set module counter
				Module otherModule = otherModuleCounter.getObject();
				// only modules up to this repository's maxOrder were copied above (and are tracked
				// here at all) - the other repository may have been created with a higher one.
				// Leaving those out records exactly what committing the same content here would
				// have; looking them up used to fail the whole merge instead.
				if (otherModule.getOrder() > repository.getMaxOrder())
					continue;
				Module module = repository.getModule(otherModule.getPos(), otherModule.getNeg());

				if (module == null)
					throw new EccoException("Association to be merged into this repository contains module " + otherModule + " which is not part of this repository.");

				ModuleCounter moduleCounter = association.getCounter().addChild(module);
				moduleCounter.setCount(otherModuleCounter.getCount());

				for (ModuleRevisionCounter otherModuleRevisionCounter : otherModuleCounter.getChildren()) {
					// set module revision counter
					ModuleRevision otherModuleRevision = otherModuleRevisionCounter.getObject();
					ModuleRevision moduleRevision = module.getRevision(otherModuleRevision.getPos(), otherModuleRevision.getNeg());
					ModuleRevisionCounter moduleRevisionCounter = moduleCounter.addChild(moduleRevision);
					moduleRevisionCounter.setCount(otherModuleRevisionCounter.getCount());
				}
			}

			// commit association to this repository
			Commit commit = repository.getEntityFactory().createCommit(Repository.MERGE);
			repository.extract(association, commit);
			repository.addCommit(commit); // extract(Association.Op, Commit) does not register the commit itself, unlike extract(Configuration, ...) - see mergeRegistersAMergeCommitPerMergedAssociation (RepositoryOpExtractTest / FileRepositoryServiceTest)
		}

		repairPartialOrderGraphArtifacts(repository);

		// finish like a commit does (CommitService): the merged-in nodes' feature traces need their
		// retroactive conditions, or composition's evaluation drops them all - a fork/pull
		// checked out EMPTY (ForkReopenTest); and the main tree must be rebuilt from the new
		// associations
		repository.setRetroactiveConditions();
		repository.invalidateMainTree();
	}

	/**
	 * Lets each node of the tree hold the artifact that replaced its own - the end of its
	 * replacing chain - for a tree whose artifacts were matched while extracting another tree that
	 * holds them too (merge()).
	 */
	static void adoptReplacingArtifacts(Node.Op node) {
		Artifact.Op<?> artifact = node.getArtifact();
		if (artifact != null && artifact.hasReplacingArtifact()) {
			Artifact.Op<?> replacing = artifact.getReplacingArtifact();
			while (replacing.hasReplacingArtifact())
				replacing = replacing.getReplacingArtifact();
			node.setArtifact(replacing);
			if (node.isUnique())
				replacing.setContainingNode(node);
		}
		if (node.getArtifact() != null)
			node.getArtifact().updateArtifactReferences();
		for (Node.Op child : node.getChildren())
			adoptReplacingArtifacts(child);
	}

	/**
	 * Points every partial order graph node at an artifact that a tree node actually holds.
	 * <p>
	 * merge() deep-copies the other repository one association at a time. An ordered artifact's
	 * graph is copied with the first association that holds the artifact, pointing at the SOURCE
	 * artifacts and redirected only to the copies made in that same call - lines that belong to
	 * another association's tree were copied separately later, so the graph kept referring to
	 * source objects that no tree holds. Those were never persisted, and the repository could not
	 * be opened again ("Could not resolve POG node artifact", ForkReopenTest). (Copying all
	 * associations in one context instead collides with Trees.slice()'s use of replacingArtifact.)
	 * <p>
	 * Such a node is redirected to the live child artifact of that ordered artifact - across all
	 * associations - with equal data at the same sequence number, or, failing that, the only live
	 * child with equal data. Anything ambiguous is left untouched.
	 */
	static void repairPartialOrderGraphArtifacts(Repository.Op repository) {
		// every ordered artifact -> the artifacts held by the children of the nodes holding it
		Map<Artifact.Op<?>, List<Artifact.Op<?>>> childrenByOrderedArtifact = new IdentityHashMap<>();
		Set<Artifact.Op<?>> live = Collections.newSetFromMap(new IdentityHashMap<>());
		for (Association.Op association : repository.getAssociations()) {
			Deque<Node.Op> stack = new ArrayDeque<>();
			stack.push(association.getRootNode());
			while (!stack.isEmpty()) {
				Node.Op node = stack.pop();
				Artifact.Op<?> artifact = node.getArtifact();
				if (artifact != null) {
					live.add(artifact);
					if (artifact.isOrdered() && artifact.getPartialOrderGraph() != null) {
						List<Artifact.Op<?>> children = childrenByOrderedArtifact.computeIfAbsent(artifact, k -> new ArrayList<>());
						for (Node.Op child : node.getChildren()) {
							if (child.getArtifact() != null)
								children.add(child.getArtifact());
						}
					}
				}
				for (Node.Op child : node.getChildren())
					stack.push(child);
			}
		}

		for (Map.Entry<Artifact.Op<?>, List<Artifact.Op<?>>> entry : childrenByOrderedArtifact.entrySet()) {
			PartialOrderGraph.Op graph = entry.getKey().getPartialOrderGraph();
			for (PartialOrderGraph.Node.Op graphNode : graph.collectNodes()) {
				Artifact.Op<?> artifact = graphNode.getArtifact();
				if (artifact == null || live.contains(artifact))
					continue;
				Artifact.Op<?> replacement = null;
				int bySequenceNumber = 0, byData = 0;
				Artifact.Op<?> dataMatch = null;
				for (Artifact.Op<?> candidate : entry.getValue()) {
					if (!artifact.getData().equals(candidate.getData()))
						continue;
					byData++;
					dataMatch = candidate;
					if (candidate.getSequenceNumber() == graphNode.getSequenceNumber()) {
						bySequenceNumber++;
						replacement = candidate;
					}
				}
				if (bySequenceNumber == 1)
					graphNode.setArtifact(replacement);
				else if (bySequenceNumber == 0 && byData == 1)
					graphNode.setArtifact(dataMatch);
			}
		}
	}
}
