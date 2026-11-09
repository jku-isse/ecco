package at.jku.isse.ecco.repository;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.dao.EccoUtil;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.core.*;
import at.jku.isse.ecco.counter.ModuleCounter;
import at.jku.isse.ecco.counter.ModuleRevisionCounter;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.module.Condition;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.module.ModuleRevision;
import at.jku.isse.ecco.pog.PartialOrderGraph;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.tree.RootNode;
import at.jku.isse.ecco.tree.Trees;
import java.util.*;
import java.util.stream.Collectors;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Copies of a repository: whole, of some commits, without some feature revisions (subset), and the replay of commits copied - moved out of {@link Repository.Op}, whose default methods delegate here.
 */
final class RepositoryCopy {

	private RepositoryCopy() {
	}

	/** See {@link Repository.Op#copyInto}. */
	static void copyInto(Repository.Op repository, Repository.Op target) {
		copyInto(repository, target, new ArrayList<>(repository.getAssociations()), new ArrayList<>(repository.getCommits()), true);
	}

	/** See {@link Repository.Op#copyCommitsInto}. */
	static void copyCommitsInto(Repository.Op repository, Repository.Op target, List<? extends Commit> commits) {
		Set<String> ids = new HashSet<>();
		for (Commit commit : commits)
			for (Association association : commit.getAssociations())
				ids.add(association.getId());
		List<Association.Op> associations = new ArrayList<>();
		for (Association.Op association : repository.getAssociations())
			if (ids.contains(association.getId()))
				associations.add(association);
		copyInto(repository, target, associations, commits, false);
	}

	static void copyInto(Repository.Op repository, Repository.Op target, List<? extends Association.Op> associations, List<? extends Commit> commits, boolean counts) {
		checkNotNull(target);
		checkArgument(target.getAssociations().isEmpty() && target.getCommits().isEmpty(), "A repository is copied into an empty one only.");
		repository.checkArtifactFormats();
		EntityFactory entityFactory = target.getEntityFactory();
		target.setMaxOrder(repository.getMaxOrder());

		for (Feature feature : repository.getFeatures()) {
			Feature newFeature = target.addFeature(feature.getId(), feature.getName());
			newFeature.setDescription(feature.getDescription());
			for (FeatureRevision featureRevision : feature.getRevisions())
				newFeature.addRevision(featureRevision.getId()).setDescription(featureRevision.getDescription());
		}
		// into an empty repository: all of them
		target.mergeDecisions(repository);

		// modules and module revisions, with their counts
		for (int order = 0; counts && order <= repository.getMaxOrder(); order++) {
			for (Module module : repository.getModules(order)) {
				Module newModule = target.getModule(targetFeatures(target, module.getPos()), targetFeatures(target, module.getNeg()));
				if (newModule == null)
					newModule = target.addModule(targetFeatures(target, module.getPos()), targetFeatures(target, module.getNeg()));
				newModule.setCount(module.getCount());
				for (ModuleRevision moduleRevision : module.getRevisions()) {
					ModuleRevision newModuleRevision = newModule.getRevision(targetFeatureRevisions(target, moduleRevision.getPos()), newModule.getNeg());
					if (newModuleRevision == null)
						newModuleRevision = newModule.addRevision(targetFeatureRevisions(target, moduleRevision.getPos()), newModule.getNeg());
					newModuleRevision.setCount(moduleRevision.getCount());
				}
			}
		}

		// associations, with their counts and their trees - copied in one context (see EccoUtil.TreeCopy)
		EccoUtil.TreeCopy treeCopy = new EccoUtil.TreeCopy(entityFactory);
		Map<String, Association.Op> newAssociations = new LinkedHashMap<>();
		for (Association.Op association : associations) {
			Association.Op newAssociation = entityFactory.createAssociation();
			newAssociation.setId(association.getId());
			if (counts)
				newAssociation.getCounter().setCount(association.getCounter().getCount());
			for (ModuleCounter moduleCounter : counts ? association.getCounter().getChildren() : List.<ModuleCounter>of()) {
				Module module = moduleCounter.getObject();
				Module newModule = target.getModule(targetFeatures(target, module.getPos()), targetFeatures(target, module.getNeg()));
				for (ModuleRevisionCounter moduleRevisionCounter : moduleCounter.getChildren()) {
					ModuleRevision moduleRevision = moduleRevisionCounter.getObject();
					newAssociation.addObservation(newModule.getRevision(targetFeatureRevisions(target, moduleRevision.getPos()), newModule.getNeg()), moduleRevisionCounter.getCount());
				}
			}
			RootNode.Op newRootNode = entityFactory.createRootNode();
			for (Node.Op child : association.getRootNode().getChildren()) {
				Node.Op newChild = treeCopy.copy(child);
				newRootNode.addChild(newChild);
				newChild.setParent(newRootNode);
			}
			newAssociation.setRootNode(newRootNode);
			newAssociations.put(association.getId(), newAssociation);
		}
		treeCopy.finish(newAssociations.values().stream().map(Association.Op::getRootNode).toList());
		// copying some commits only, a graph node can hold an artifact of an association not copied:
		// each order graph is trimmed to the artifacts copied, or the copy refers to artifacts it
		// does not have (sent over the network, they could not be resolved)
		Set<Artifact.Op<?>> copied = Collections.newSetFromMap(new IdentityHashMap<>());
		for (Association.Op newAssociation : newAssociations.values())
			newAssociation.getRootNode().traverse((Node.Op node) -> {
				if (node.getArtifact() != null)
					copied.add(node.getArtifact());
			});
		for (Artifact.Op<?> artifact : copied)
			if (artifact.getPartialOrderGraph() != null)
				trimTo(artifact.getPartialOrderGraph(), copied);
		for (Association.Op newAssociation : newAssociations.values()) {
			Trees.checkConsistency(newAssociation.getRootNode());
			target.addAssociation(newAssociation);
		}

		// commits, in their order
		for (Commit commit : commits) {
			Commit newCommit = entityFactory.createCommit(commit.getUsername());
			newCommit.setId(commit.getId());
			newCommit.setDate(commit.getDate());
			newCommit.setCommitMessage(commit.getCommitMessage());
			if (commit.getConfiguration() != null)
				newCommit.setConfiguration(entityFactory.createConfiguration(targetFeatureRevisions(target, commit.getConfiguration().getFeatureRevisions())));
			for (Association association : commit.getAssociations()) {
				Association.Op newAssociation = newAssociations.get(association.getId());
				if (newAssociation != null)
					newCommit.addAssociation(newAssociation);
			}
			target.addCommit(newCommit);
		}

		// finish like a commit does (CommitService): the copied nodes' feature traces need their
		// retroactive conditions, and the main tree is built from the new associations
		if (counts)
			target.setRetroactiveConditions();
		target.invalidateMainTree();
	}

	/** See {@link Repository.Op#replay}. */
	static Commit replay(Repository.Op repository, Configuration configuration, Collection<? extends Association.Op> associations, String committer) {
		checkNotNull(configuration);
		checkNotNull(associations);
		EntityFactory entityFactory = repository.getEntityFactory();

		EccoUtil.TreeCopy treeCopy = new EccoUtil.TreeCopy(entityFactory);
		RootNode.Op rootNode = entityFactory.createRootNode();
		for (Association.Op association : associations) {
			RootNode.Op copiedRootNode = entityFactory.createRootNode();
			for (Node.Op child : association.getRootNode().getChildren()) {
				Node.Op copiedChild = treeCopy.copy(child);
				copiedRootNode.addChild(copiedChild);
				copiedChild.setParent(copiedRootNode);
			}
			rootNode.merge(copiedRootNode);
		}
		treeCopy.finish(List.of(rootNode));

		// as read: every node its own, the children of an ordered artifact in their order, and no
		// order graph or sequence number yet - the commit is aligned with this repository's own order
		// graphs, as a commit of the files is. The other repository's sequence numbers mean nothing
		// here: kept, a line of it was taken for an equal line here with the same number (a "}" of
		// another position) and one of the two was lost
		Set<Artifact.Op<?>> artifacts = Collections.newSetFromMap(new IdentityHashMap<>());
		rootNode.traverse((Node.Op node) -> {
			if (node.getArtifact() != null)
				artifacts.add(node.getArtifact());
		});
		rootNode.traverse((Node.Op node) -> {
			node.setUnique(true);
			Artifact.Op<?> artifact = node.getArtifact();
			if (artifact == null)
				return;
			artifact.setContainingNode(node);
			if (artifact.getPartialOrderGraph() != null) {
				trimTo(artifact.getPartialOrderGraph(), artifacts);
				inOrder(node, artifact.getPartialOrderGraph());
				artifact.setPartialOrderGraph(null);
			}
		});
		for (Artifact.Op<?> artifact : artifacts)
			artifact.setSequenceNumber(PartialOrderGraph.UNASSIGNED_SEQUENCE_NUMBER);

		Set<Node.Op> nodes = new LinkedHashSet<>();
		for (Node.Op child : rootNode.getChildren()) {
			child.setParent(null);
			nodes.add(child);
		}
		return repository.extract(repository.adoptConfiguration(configuration), nodes, committer);
	}

	/** See {@link Repository.Op#commitsMissingIn}. */
	static List<Commit> commitsMissingIn(Repository.Op repository, Collection<String> ids, Collection<FeatureRevision> deselected) {
		Set<String> have = new HashSet<>(ids);
		Set<String> left = new HashSet<>();
		for (FeatureRevision featureRevision : deselected)
			left.add(featureRevision.getFeature().getId() + "." + featureRevision.getId());
		List<Commit> missing = new ArrayList<>();
		for (Commit commit : repository.getCommits()) {
			if (commit.getConfiguration() == null || have.contains(commit.getId()))
				continue;
			if (Arrays.stream(commit.getConfiguration().getFeatureRevisions()).anyMatch(featureRevision -> left.contains(featureRevision.getFeature().getId() + "." + featureRevision.getId())))
				continue;
			missing.add(commit);
		}
		return missing;
	}

	/** See {@link Repository.Op#adoptConfiguration}. */
	static Configuration adoptConfiguration(Repository.Op repository, Configuration configuration) {
		List<FeatureRevision> featureRevisions = new ArrayList<>();
		for (FeatureRevision featureRevision : configuration.getFeatureRevisions()) {
			Feature feature = repository.getFeature(featureRevision.getFeature().getId());
			if (feature == null)
				feature = repository.getFeatures().stream().filter(candidate -> candidate.getName().equals(featureRevision.getFeature().getName())).findFirst().orElse(null);
			if (feature == null) {
				featureRevisions.add(featureRevision);
				continue;
			}
			FeatureRevision revision = feature.getRevision(featureRevision.getId());
			if (revision == null) {
				revision = feature.addRevision(featureRevision.getId());
				revision.setDescription(featureRevision.getDescription());
			}
			featureRevisions.add(revision);
		}
		return repository.getEntityFactory().createConfiguration(featureRevisions.toArray(new FeatureRevision[0]));
	}

	/**
	 * Puts the children of [node] in the order of [graph], its artifact's order graph: a topological
	 * order, ties by sequence number; children the graph does not hold last, as they were.
	 */
	@SuppressWarnings("unchecked")
	static void inOrder(Node.Op node, PartialOrderGraph.Op graph) {
		Map<Artifact.Op<?>, Node.Op> childByArtifact = new IdentityHashMap<>();
		for (Node.Op child : node.getChildren())
			if (child.getArtifact() != null)
				childByArtifact.put(child.getArtifact(), child);
		Map<PartialOrderGraph.Node.Op, Integer> inDegree = new IdentityHashMap<>();
		for (PartialOrderGraph.Node.Op graphNode : graph.collectNodes()) {
			inDegree.putIfAbsent(graphNode, 0);
			for (PartialOrderGraph.Node.Op next : graphNode.getNext())
				inDegree.merge(next, 1, Integer::sum);
		}
		PriorityQueue<PartialOrderGraph.Node.Op> ready = new PriorityQueue<>(Comparator.comparingInt(PartialOrderGraph.Node.Op::getSequenceNumber));
		inDegree.forEach((graphNode, degree) -> {
			if (degree == 0)
				ready.add(graphNode);
		});
		List<Node.Op> ordered = new ArrayList<>();
		Set<Node.Op> placed = Collections.newSetFromMap(new IdentityHashMap<>());
		while (!ready.isEmpty()) {
			PartialOrderGraph.Node.Op graphNode = ready.poll();
			Node.Op child = graphNode.getArtifact() == null ? null : childByArtifact.get(graphNode.getArtifact());
			if (child != null && placed.add(child))
				ordered.add(child);
			for (PartialOrderGraph.Node.Op next : graphNode.getNext())
				if (inDegree.merge(next, -1, Integer::sum) == 0)
					ready.add(next);
		}
		for (Node.Op child : node.getChildren())
			if (!placed.contains(child))
				ordered.add(child);
		List<Node.Op> children = (List<Node.Op>) node.getChildren();
		children.clear();
		children.addAll(ordered);
	}

	/** Removes from [graph] the nodes holding none of [artifacts] (by identity), keeping the order of the rest. */
	static void trimTo(PartialOrderGraph.Op graph, Set<Artifact.Op<?>> artifacts) {
		List<Artifact.Op<?>> symbols = new ArrayList<>();
		for (PartialOrderGraph.Node.Op graphNode : graph.collectNodes())
			if (graphNode.getArtifact() != null && artifacts.contains(graphNode.getArtifact()))
				symbols.add(graphNode.getArtifact());
		graph.trim(symbols);
	}

	static Feature[] targetFeatures(Repository.Op target, Feature[] features) {
		return Arrays.stream(features).map(feature -> target.getFeature(feature.getId())).toArray(Feature[]::new);
	}

	static FeatureRevision[] targetFeatureRevisions(Repository.Op target, FeatureRevision[] featureRevisions) {
		return Arrays.stream(featureRevisions).map(featureRevision -> target.getFeature(featureRevision.getFeature().getId()).getRevision(featureRevision.getId())).toArray(FeatureRevision[]::new);
	}

	/** See {@link Repository.Op#subset}. */
	static Repository.Op subset(Repository.Op repository, Collection<FeatureRevision> deselected, int maxOrder, EntityFactory entityFactory) {
		checkNotNull(deselected);
		checkArgument(maxOrder <= repository.getMaxOrder());
		checkNotNull(entityFactory);

		// create empty repository using the given entity factory
		Repository.Op newRepository = entityFactory.createRepository();
		newRepository.setMaxOrder(maxOrder);

		// add features (that have at least one revision) and feature revisions to subset repository, excluding deselected
		for (Feature feature : repository.getFeatures()) {
			if (!feature.getRevisions().isEmpty() && feature.getRevisions().stream().anyMatch(featureRevision -> !deselected.contains(featureRevision))) {
				// feature
				Feature newFeature = newRepository.addFeature(feature.getId(), feature.getName());
				newFeature.setDescription(feature.getDescription());
				// revisions
				for (FeatureRevision featureRevision : feature.getRevisions()) {
					// but only those that are not deselected
					if (!deselected.contains(featureRevision)) {
						FeatureRevision newFeatureRevision = newFeature.addRevision(featureRevision.getId());
						newFeatureRevision.setDescription(featureRevision.getDescription());
					}
				}
			}
		}

		// copy every accepted constraint as-is. Deliberately independent of the
		// module/association trimming below -- constraints have no counters and no
		// fusion/extract() participation to inherit that machinery's fragility from (see
		// the data loss of pruning at write time for why that matters). A
		// constraint whose features were excluded by "deselected" is still copied: it's
		// advisory metadata about feature names, not a consumer of module/association data,
		// and acceptedSuggestions()'s fresh-re-mining safety check (not this copy) is what
		// prevents a now-inapplicable constraint from ever being trusted.
		for (Constraint constraint : repository.getConstraints()) {
			newRepository.addConstraint(constraint.getKind(), constraint.getFeatureA(), constraint.getFeatureB());
		}
		// rejections likewise
		for (Constraint constraint : repository.getRejectedConstraints()) {
			newRepository.addRejectedConstraint(constraint.getKind(), constraint.getFeatureA(), constraint.getFeatureB());
		}
		// and the parents, accepted and rejected - feature names, as the constraints
		repository.getFeatureParents().forEach(newRepository::setFeatureParent);
		repository.getRejectedFeatureParents().forEach((feature, parents) -> parents.forEach(parent -> newRepository.addRejectedFeatureParent(feature, parent)));

		// for every association in this repository: trim condition, use it to check if matching association already exists, if not create it, add observations based on trimmed condition, copy artifact tree and trim order graphs. (basically current merge implementation)
		Map<Set<ModuleRevision>, Association.Op> andConditionAssociationMap = new HashMap<>();
		Map<Set<ModuleRevision>, Association.Op> orConditionAssociationMap = new HashMap<>();
		// the associations of this repository the subset takes - see the trimming below
		Set<Association> keptAssociations = Collections.newSetFromMap(new IdentityHashMap<>());
		// every artifact of this repository copied into the subset -> its copy (see redirectToCopies())
		Map<Artifact.Op<?>, Artifact.Op<?>> copies = new IdentityHashMap<>();
		// the associations' trees are copied in one context: an artifact several of them hold keeps
		// one copy (see EccoUtil.TreeCopy)
		EccoUtil.TreeCopy treeCopy = new EccoUtil.TreeCopy(entityFactory);
		for (Association.Op association : repository.getAssociations()) {
			Condition condition = association.computeCondition();

			// compute set of module revisions that need to be added to the new repository and associations
			Set<ModuleRevision> newModuleRevisions = new HashSet<>();
			for (Map.Entry<Module, Collection<ModuleRevision>> entry : condition.getModules().entrySet()) {
				Module module = entry.getKey();
				// exclude modules that are above max order or that contain positive features that are not in subset repository
				if (module.getOrder() <= newRepository.getMaxOrder() && Arrays.stream(module.getPos()).noneMatch(feature -> newRepository.getFeature(feature.getId()) == null)) {

					// exclude negative features that are not in subset repository
					Feature[] newNegFeatures = Arrays.stream(module.getNeg()).map(feature -> newRepository.getFeature(feature.getId())).filter(Objects::nonNull).toArray(Feature[]::new);

					for (ModuleRevision moduleRevision : entry.getValue()) {
						// exclude module revisions that contain positive feature revisions that are not in subset repository
						if (Arrays.stream(moduleRevision.getPos()).noneMatch(featureRevision -> newRepository.getFeature(featureRevision.getFeature().getId()).getRevision(featureRevision.getId()) == null)) {

							// get/add module from/to subset repository and increase its counter
							Module newModule = newRepository.getModule(module.getPos(), newNegFeatures);
							if (newModule == null) {
								newModule = newRepository.addModule(Arrays.stream(module.getPos()).map(feature -> newRepository.getFeature(feature.getId())).toArray(Feature[]::new), newNegFeatures);
								newModule.incCount();
							}

							ModuleRevision newModuleRevision = newModule.getRevision(moduleRevision.getPos(), newModule.getNeg());
							if (newModuleRevision == null) {
								newModuleRevision = newModule.addRevision(Arrays.stream(moduleRevision.getPos()).map(featureRevision -> newRepository.getFeature(featureRevision.getFeature().getId()).getRevision(featureRevision.getId())).toArray(FeatureRevision[]::new), newModule.getNeg());
								newModuleRevision.incCount();
							}

							// add module revision as observations to new association
							newModuleRevisions.add(newModuleRevision);
						}
					}
				}
			}

			// check if association has at least one module, if not exclude it
			if (!newModuleRevisions.isEmpty()) {
				keptAssociations.add(association);

				// check if a new association with equal condition (ignoring negative features without revisions) already exists
				Association.Op newAssociation = null;
				boolean newAssoc = false;
				if (condition.getType() == Condition.TYPE.AND) {
					// for an AND condition set association, module and combined count to 1
					newAssociation = andConditionAssociationMap.get(newModuleRevisions);
					// if not create a new association
					if (newAssociation == null) {
						newAssoc = true;
						newAssociation = repository.getEntityFactory().createAssociation();
						newAssociation.setId(UUID.randomUUID().toString());
						andConditionAssociationMap.put(newModuleRevisions, newAssociation);
						newAssociation.getCounter().setCount(1);
					}
				} else if (condition.getType() == Condition.TYPE.OR) {
					// for an OR condition set association=2, module=1 and combined count to 1
					newAssociation = orConditionAssociationMap.get(newModuleRevisions);
					// if not create a new association
					if (newAssociation == null) {
						newAssoc = true;
						newAssociation = repository.getEntityFactory().createAssociation();
						newAssociation.setId(UUID.randomUUID().toString());
						orConditionAssociationMap.put(newModuleRevisions, newAssociation);
						newAssociation.getCounter().setCount(2);
					}
				}
				if (newAssoc) {
					newAssociation.setRootNode(entityFactory.createRootNode());
					// add new association to subset repository
					newRepository.addAssociation(newAssociation);
				}

				// add observations to new association
				for (ModuleRevision newModuleRevision : newModuleRevisions) {
					// add module revisions as observations to new association
					ModuleCounter newModuleCounter = newAssociation.getCounter().getChild(newModuleRevision.getModule());
					// check if observation is already part of this association
					if (!(newModuleCounter != null && newModuleCounter.getChild(newModuleRevision) != null)) {
						newAssociation.addObservation(newModuleRevision, 1);
					}
				}

				// copy artifact tree
				RootNode.Op copiedRootNode = entityFactory.createRootNode();
				// clone tree
				for (Node.Op childNode : association.getRootNode().getChildren()) {
					Node.Op copiedChildNode = treeCopy.copy(childNode);
					recordCopies(childNode, copiedChildNode, copies);
					copiedRootNode.addChild(copiedChildNode);
					copiedChildNode.setParent(copiedRootNode);
				}

				// merge copied artifact tree into artifact tree of new association
				newAssociation.getRootNode().merge(copiedRootNode);
			}
		}

		// and the modules no kept association's condition has - configurations committed (a module
		// revision is one combination of features of a commit), counted once like the others: a
		// checkout of the subset looks them up, and without them warned that they were MISSING where
		// one of this repository did not (ForkModulesTest)
		for (int order = 0; order <= newRepository.getMaxOrder(); order++) {
			for (Module module : repository.getModules(order)) {
				if (Arrays.stream(module.getPos()).anyMatch(feature -> newRepository.getFeature(feature.getId()) == null))
					continue;
				Feature[] newNegFeatures = Arrays.stream(module.getNeg()).map(feature -> newRepository.getFeature(feature.getId())).filter(Objects::nonNull).toArray(Feature[]::new);
				for (ModuleRevision moduleRevision : module.getRevisions()) {
					if (Arrays.stream(moduleRevision.getPos()).anyMatch(featureRevision -> newRepository.getFeature(featureRevision.getFeature().getId()).getRevision(featureRevision.getId()) == null))
						continue;
					Module newModule = newRepository.getModule(module.getPos(), newNegFeatures);
					if (newModule == null) {
						newModule = newRepository.addModule(Arrays.stream(module.getPos()).map(feature -> newRepository.getFeature(feature.getId())).toArray(Feature[]::new), newNegFeatures);
						newModule.incCount();
					}
					if (newModule.getRevision(moduleRevision.getPos(), newModule.getNeg()) == null) {
						ModuleRevision newModuleRevision = newModule.addRevision(Arrays.stream(moduleRevision.getPos()).map(featureRevision -> newRepository.getFeature(featureRevision.getFeature().getId()).getRevision(featureRevision.getId())).toArray(FeatureRevision[]::new), newModule.getNeg());
						newModuleRevision.incCount();
					}
				}
			}
		}

		Collection<? extends Association.Op> newAssociations = newRepository.getAssociations();
		treeCopy.finish(newAssociations.stream().map(Association.Op::getRootNode).toList());

		// trim sequence graphs to only contain artifacts from the selected associations. Every
		// ordered artifact's graph is trimmed, not only the one under a unique node: an untrimmed one
		// kept nodes of left-out artifacts, which were then stored without those artifacts - the
		// repository could not be opened again (ExcludedRevisionsTest). A graph node is kept if the
		// association holding its artifact is.
		for (Association.Op newAssociation : newAssociations) {
			newAssociation.getRootNode().traverse((Node.Op node) -> {
				if (node.getArtifact() != null && node.getArtifact().isOrdered() && node.getArtifact().isSequenced() && node.getArtifact().getPartialOrderGraph() != null) {
					// get all symbols from sequence graph
					Collection<? extends Artifact.Op<?>> symbols = node.getArtifact().getPartialOrderGraph().collectNodes().stream().map(PartialOrderGraph.Node.Op::getArtifact).collect(Collectors.toList());

					// remove symbols that are not contained in the given associations
					symbols.removeIf(symbol -> {
						if (symbol == null)
							return false;
						Node containingNode = symbol.getContainingNode();
						Association containingAssociation = containingNode == null ? null : containingNode.getContainingAssociation();
						return !(newAssociations.contains(containingAssociation) || keptAssociations.contains(containingAssociation));
					});

					// trim sequence graph
					node.getArtifact().getPartialOrderGraph().trim(symbols);
				}
			});
		}

		// the kept graph nodes that still point to an artifact of this repository (a line another
		// association holds) now point to its copy - the subset is self-contained. A graph node
		// pointing outside it worked only where merge() repaired it next (a local fork, pull or
		// push); over the network only the artifacts in the trees are sent (collectArtifacts()),
		// and the receiver could not resolve those nodes (NetworkSyncSharedFileTest).
		redirectToCopies(newAssociations, copies);
		RepositoryMerge.repairPartialOrderGraphArtifacts(newRepository);

		// check consistency of copied trees
		for (Association.Op newAssociation : newAssociations) {
			Trees.checkConsistency(newAssociation.getRootNode());
		}

		// compute dependency graph for selected associations and check if there are any unresolved dependencies.
		DependencyGraph dg = new DependencyGraph(newAssociations, DependencyGraph.ReferencesResolveMode.LEAVE_REFERENCES_UNRESOLVED); // we do not trim unresolved references. instead we abort.
		if (!dg.getUnresolvedDependencies().isEmpty()) {
			throw new EccoException("Unresolved dependencies in selection.");
		}


		return newRepository;
	}

	/** Records each artifact of the source tree -> the artifact of the same node of its deep copy; a unique node's copy wins. */
	static void recordCopies(Node.Op source, Node.Op copy, Map<Artifact.Op<?>, Artifact.Op<?>> copies) {
		Deque<Node.Op> sources = new ArrayDeque<>();
		Deque<Node.Op> targets = new ArrayDeque<>();
		sources.push(source);
		targets.push(copy);
		while (!sources.isEmpty()) {
			Node.Op s = sources.pop();
			Node.Op t = targets.pop();
			if (s.getArtifact() != null && t.getArtifact() != null) {
				if (s.isUnique())
					copies.put(s.getArtifact(), t.getArtifact());
				else
					copies.putIfAbsent(s.getArtifact(), t.getArtifact());
			}
			List<? extends Node.Op> sourceChildren = s.getChildren();
			List<? extends Node.Op> copyChildren = t.getChildren();
			for (int i = 0; i < Math.min(sourceChildren.size(), copyChildren.size()); i++) {
				sources.push(sourceChildren.get(i));
				targets.push(copyChildren.get(i));
			}
		}
	}

	/**
	 * Points every partial order graph node of the given associations' trees that holds a source
	 * artifact to that artifact's copy, if a tree of the given associations holds the copy (merging
	 * a copied tree into an existing association can keep the existing equal artifact instead -
	 * repairPartialOrderGraphArtifacts() finds that one).
	 */
	static void redirectToCopies(Collection<? extends Association.Op> associations, Map<Artifact.Op<?>, Artifact.Op<?>> copies) {
		Set<Artifact.Op<?>> live = Collections.newSetFromMap(new IdentityHashMap<>());
		List<Artifact.Op<?>> ordered = new ArrayList<>();
		for (Association.Op association : associations) {
			Deque<Node.Op> stack = new ArrayDeque<>();
			stack.push(association.getRootNode());
			while (!stack.isEmpty()) {
				Node.Op node = stack.pop();
				Artifact.Op<?> artifact = node.getArtifact();
				if (artifact != null && live.add(artifact) && artifact.getPartialOrderGraph() != null)
					ordered.add(artifact);
				for (Node.Op child : node.getChildren())
					stack.push(child);
			}
		}
		for (Artifact.Op<?> artifact : ordered) {
			for (PartialOrderGraph.Node.Op graphNode : artifact.getPartialOrderGraph().collectNodes()) {
				Artifact.Op<?> target = graphNode.getArtifact();
				if (target == null || live.contains(target))
					continue;
				Artifact.Op<?> copy = copies.get(target);
				if (copy != null && live.contains(copy))
					graphNode.setArtifact(copy);
			}
		}
	}
}
