package at.jku.isse.ecco.repository;

import at.jku.isse.ecco.dao.EntityFactory;

import static com.google.common.base.Preconditions.checkNotNull;

public class RepositoryOperator {

	private Repository.Op repository;
	private EntityFactory entityFactory;

	public RepositoryOperator(Repository.Op repository) {
		checkNotNull(repository);
		this.repository = repository;
		this.entityFactory = repository.getEntityFactory();
	}


//	public Repository.Op subset(Collection<FeatureRevision> deselected, int maxOrder, EntityFactory entityFactory) {
//		checkNotNull(deselected);
//		checkArgument(maxOrder <= this.repository.getMaxOrder());
//
//
//		// create empty repository using the given entity factory
//		Repository.Op newRepository = entityFactory.createRepository();
//		newRepository.setMaxOrder(maxOrder);
//
//
//		// add all features and versions in this repository to new repository, excluding the deselected feature versions.
//		Map<Feature, Feature> featureReplacementMap = new HashMap<>();
//		Map<FeatureRevision, FeatureRevision> featureVersionReplacementMap = new HashMap<>();
//		Collection<FeatureRevision> newFeatureVersions = new ArrayList<>();
//		for (Feature feature : this.repository.getFeatures()) {
//			Feature newFeature = newRepository.addFeature(feature.getId(), feature.getName(), feature.getDescription());
//
//			for (FeatureRevision featureVersion : feature.getRevisions()) {
//				if (!deselected.contains(featureVersion)) {
//					FeatureRevision newFeatureVersion = newFeature.addRevision(featureVersion.getId());
//					newFeatureVersion.setDescription(featureVersion.getDescription());
//					newFeatureVersions.add(newFeatureVersion);
//					featureVersionReplacementMap.put(featureVersion, newFeatureVersion);
//				}
//			}
//
//			if (!newFeature.getRevisions().isEmpty()) {
//				featureReplacementMap.put(feature, newFeature);
//			}
//		}
//		for (Association newAssociation : newRepository.getAssociations()) {
//			for (FeatureRevision newFeatureVersion : newFeatureVersions) {
//				newAssociation.getPresenceCondition().addFeatureVersion(newFeatureVersion);
//				newAssociation.getPresenceCondition().addFeatureInstance(newFeatureVersion, false, newRepository.getMaxOrder());
//			}
//		}
//
//
//		// copy associations in this repository and add them to new repository, but exclude modules or module features that evaluate to false given the deselected feature versions
//		Collection<Association.Op> copiedAssociations = new ArrayList<>();
//		for (Association association : this.repository.getAssociations()) {
//			Association.Op copiedAssociation = entityFactory.createAssociation();
//			copiedAssociation.setId(UUID.randomUUID().toString());
//
//			PresenceCondition thisPresenceCondition = association.getPresenceCondition();
//
//
//			// copy presence condition
//			PresenceCondition copiedPresenceCondition = entityFactory.createPresenceCondition();
//			copiedAssociation.setPresenceCondition(copiedPresenceCondition);
//
//			Set<at.jku.isse.ecco.module.Module>[][] moduleSetPairs = new Set[][]{{thisPresenceCondition.getMinModules(), copiedPresenceCondition.getMinModules()}, {thisPresenceCondition.getMaxModules(), copiedPresenceCondition.getMaxModules()}, {thisPresenceCondition.getNotModules(), copiedPresenceCondition.getNotModules()}, {thisPresenceCondition.getAllModules(), copiedPresenceCondition.getAllModules()}};
//
//			for (Set<at.jku.isse.ecco.module.Module>[] moduleSetPair : moduleSetPairs) {
//				Set<at.jku.isse.ecco.module.Module> fromModuleSet = moduleSetPair[0];
//				Set<at.jku.isse.ecco.module.Module> toModuleSet = moduleSetPair[1];
//
//				for (at.jku.isse.ecco.module.Module fromModule : fromModuleSet) {
//					at.jku.isse.ecco.module.Module toModule = entityFactory.createModule();
//					for (ModuleFeature fromModuleFeature : fromModule) {
//
//						// feature
//						Feature fromFeature = fromModuleFeature.getFeature();
//						Feature toFeature;
//						if (featureReplacementMap.containsKey(fromFeature)) {
//							toFeature = featureReplacementMap.get(fromFeature);
//
//							// if a deselected feature version is contained in module feature:
//							//  if module feature is positive: remove / do not add feature version from module feature
//							//   if module feature is empty: remove it / do not add it
//							//  else if module feature is negative: remove module feature from module
//							//   if module is empty (should not happen?) then leave it! module is always TRUE (again: should not happen, because at least one positive module feature should be in every module, but that might currently not be the case)
//
//							ModuleFeature toModuleFeature = entityFactory.createModuleFeature(toFeature, fromModuleFeature.getSign());
//							boolean addToModule = true;
//							for (FeatureRevision fromFeatureVersion : fromModuleFeature) {
//								if (deselected.contains(fromFeatureVersion)) { // if a deselected feature version is contained in module feature
//
//									if (fromModuleFeature.getSign()) {  // if module feature is positive
//										// do not add feature version to module feature
//									} else {
//										// do not add module feature to module because it is always true
//										addToModule = false;
//										break;
//									}
//
//								} else { // ordinary copy
//									FeatureRevision toFeatureVersion;
//									if (featureVersionReplacementMap.containsKey(fromFeatureVersion)) {
//										toFeatureVersion = featureVersionReplacementMap.get(fromFeatureVersion);
//									} else {
//										toFeatureVersion = fromFeatureVersion;
//
//										throw new EccoException("This should not happen!");
//									}
//									toModuleFeature.add(toFeatureVersion);
//								}
//							}
//							if (!toModuleFeature.isEmpty() && addToModule) { // if module feature is empty: do not add it
//								toModule.add(toModuleFeature);
//							}
//							if (fromModuleFeature.getSign() && toModuleFeature.isEmpty()) { // don't add module because it is false
//								toModule.clear();
//								break;
//							}
//						} else {
//							//toFeature = fromFeature;
//							//throw new EccoException("This should not happen!");
//							if (fromModuleFeature.getSign()) {
//								toModule.clear();
//								break;
//							}
//						}
//
//					}
//					if (!toModule.isEmpty())
//						toModuleSet.add(toModule);
//				}
//			}
//
//
//			// copy artifact tree
//			RootNode.Op copiedRootNode = entityFactory.createRootNode();
//			copiedAssociation.setRootNode(copiedRootNode);
//			// clone tree
//			for (Node.Op parentChildNode : association.getRootNode().getChildren()) {
//				Node.Op copiedChildNode = EccoUtil.deepCopyTree(parentChildNode, entityFactory);
//				copiedRootNode.addChild(copiedChildNode);
//				copiedChildNode.setParent(copiedRootNode);
//			}
//			//Trees.checkConsistency(copiedRootNode);
//
//
//			copiedAssociations.add(copiedAssociation);
//		}
//
//		for (Association a : copiedAssociations) {
//			Trees.checkConsistency(a.getRootNode());
//		}
//
//
//		// remove (fixate) all provided (selected) feature instances in the presence conditions of the copied associations.
//		// this is already done in the previous step
//
//		// remove cloned associations with empty PCs.
//		Iterator<Association.Op> associationIterator = copiedAssociations.iterator();
//		while (associationIterator.hasNext()) {
//			Association.Op association = associationIterator.next();
//			if (association.getPresenceCondition().isEmpty())
//				associationIterator.remove();
//		}
//
//		// compute dependency graph for selected associations and check if there are any unresolved dependencies.
//		DependencyGraph dg = new DependencyGraph(copiedAssociations, DependencyGraph.ReferencesResolveMode.LEAVE_REFERENCES_UNRESOLVED); // we do not trim unresolved references. instead we abort.
//		if (!dg.getUnresolvedDependencies().isEmpty()) {
//			throw new EccoException("Unresolved dependencies in selection.");
//		}
//
//		// merge cloned associations with equal PCs.
//		Associations.consolidate(copiedAssociations);
//
//		// trim sequence graphs to only contain artifacts from the selected associations.
//		EccoUtil.trimSequenceGraph(copiedAssociations);
//
//		for (Association.Op copiedAssociation : copiedAssociations) {
//			newRepository.addAssociation(copiedAssociation);
//		}
//
//		return newRepository;
//	}
//
//
//	public void merge(Repository.Op other) {
//		checkNotNull(other);
//		checkArgument(other.getClass().equals(this.repository.getClass()));
//
//		// step 1: add new features and versions in other repository to associations in this repository,
//		Map<Feature, Feature> featureReplacementMap = new HashMap<>();
//		Map<FeatureRevision, FeatureRevision> featureVersionReplacementMap = new HashMap<>();
//		Collection<FeatureRevision> newThisFeatureVersions = new ArrayList<>();
//		for (Feature otherFeature : other.getFeatures()) {
//			Feature thisFeature = this.repository.getFeature(otherFeature.getId()); // TODO: what to do when parent and child feature have different description? e.g. because it was changed on one of the two before the pull.
//			if (thisFeature == null) {
//				thisFeature = this.repository.addFeature(otherFeature.getId(), otherFeature.getName(), otherFeature.getDescription());
//			}
//
//			for (FeatureRevision otherFeatureVersion : otherFeature.getRevisions()) {
//				FeatureRevision thisFeatureVersion = thisFeature.getRevision(otherFeatureVersion.getId());
//				if (thisFeatureVersion == null) {
//					thisFeatureVersion = thisFeature.addRevision(otherFeatureVersion.getId());
//					thisFeatureVersion.setDescription(otherFeatureVersion.getDescription());
//					newThisFeatureVersions.add(thisFeatureVersion);
//				}
//				featureVersionReplacementMap.put(otherFeatureVersion, thisFeatureVersion);
//			}
//
//			if (!thisFeature.getRevisions().isEmpty()) {
//				featureReplacementMap.put(otherFeature, thisFeature);
//			}
//		}
//		for (Association thisAssociation : this.repository.getAssociations()) {
//			for (FeatureRevision newThisFeatureVersion : newThisFeatureVersions) {
//				thisAssociation.getPresenceCondition().addFeatureVersion(newThisFeatureVersion);
//				thisAssociation.getPresenceCondition().addFeatureInstance(newThisFeatureVersion, false, this.repository.getMaxOrder());
//			}
//		}
//
//		// step 2: add new features in this repository to associations in other repository.
//		Collection<FeatureRevision> newOtherFeatureVersions = new ArrayList<>();
//		for (Feature thisFeature : this.repository.getFeatures()) {
//			Feature otherFeature = other.getFeature(thisFeature.getId());
//			if (otherFeature == null) {
//				// add all its versions to list
//				for (FeatureRevision thisFeatureVersion : thisFeature.getRevisions()) {
//					newOtherFeatureVersions.add(thisFeatureVersion);
//				}
//			} else {
//				// compare versions and add new ones to list
//				for (FeatureRevision thisFeatureVersion : thisFeature.getRevisions()) {
//					FeatureRevision otherFeatureVersion = otherFeature.getRevision(thisFeatureVersion.getId());
//					if (otherFeatureVersion == null) {
//						newOtherFeatureVersions.add(thisFeatureVersion);
//					}
//				}
//			}
//		}
//		for (Association otherAssociation : other.getAssociations()) {
//			for (FeatureRevision newOtherFeatureVersion : newOtherFeatureVersions) {
//				otherAssociation.getPresenceCondition().addFeatureVersion(newOtherFeatureVersion);
//				otherAssociation.getPresenceCondition().addFeatureInstance(newOtherFeatureVersion, false, other.getMaxOrder());
//			}
//		}
//
//		// step 3: commit associations in other repository to this repository.
//		this.extract(other.getAssociations());
//	}
//
//
//	/**
//	 * Splits all marked artifacts in the repository from their previous association into a new one.
//	 *
//	 * @return The commit object containing the affected associations.
//	 */
//	public Commit split() { // TODO: the presence condition must also somehow be marked and extracted! otherwise the repo becomes inconsistent.
//		Commit commit = this.entityFactory.createCommit();
//
//		Collection<? extends Association.Op> originalAssociations = this.repository.getAssociations();
//		Collection<Association.Op> newAssociations = new ArrayList<>();
//
//		// extract from every  original association
//		for (Association.Op origA : originalAssociations) {
//
//			// ASSOCIATION
//			Association.Op extractedA = this.entityFactory.createAssociation();
//			extractedA.setId(UUID.randomUUID().toString());
//
//
//			// PRESENCE CONDITION
//			//extractedA.setPresenceCondition(this.entityFactory.createPresenceCondition(origA.getPresenceCondition())); // copy presence condition
//			extractedA.setPresenceCondition(this.entityFactory.createPresenceCondition()); // new empty presence condition
//
//
//			// ARTIFACT TREE
//			RootNode.Op extractedTree = (RootNode.Op) Trees.extractMarked(origA.getRootNode());
//			if (extractedTree != null)
//				extractedA.setRootNode(extractedTree);
//
//
//			// if the intersection association has artifacts or a not empty presence condition store it
//			if (extractedA.getRootNode() != null && (extractedA.getRootNode().getChildren().size() > 0 || !extractedA.getPresenceCondition().isEmpty())) {
//				// set parents for intersection association (and child for parents)
//				extractedA.setName("EXTRACTED " + origA.getId());
//
//				// store association
//				newAssociations.add(extractedA);
//			}
//
//			Trees.checkConsistency(origA.getRootNode());
//			if (extractedA.getRootNode() != null)
//				Trees.checkConsistency(extractedA.getRootNode());
//		}
//
//		for (Association.Op newA : newAssociations) {
//			this.repository.addAssociation(newA);
//
////			commit.addAssociation(newA);
//		}
//
//		return commit;
//	}
//
//
//	/**
//	 * Merges all associations that have the same presence condition.
//	 */
//	protected void consolidateAssociations() {
//		Collection<Association.Op> toRemove = new ArrayList<>();
//
//		Map<PresenceCondition, Association.Op> pcToAssocMap = new HashMap<>();
//
//		Collection<? extends Association.Op> associations = this.repository.getAssociations();
//		Iterator<? extends Association.Op> it = associations.iterator();
//		while (it.hasNext()) {
//			Association.Op association = it.next();
//			Association.Op equalAssoc = pcToAssocMap.get(association.getPresenceCondition());
//			if (equalAssoc == null) {
//				pcToAssocMap.put(association.getPresenceCondition(), association);
//			} else {
//				Trees.merge(equalAssoc.getRootNode(), association.getRootNode());
//				toRemove.add(association);
//				it.remove();
//			}
//		}
//
//		// delete removed associations
//		for (Association.Op a : toRemove) {
//			repository.removeAssociation(a);
//		}
//	}
//
//	protected void mergeEmptyAssociations() {
//		Collection<? extends Association.Op> originalAssociations = this.repository.getAssociations();
//		Collection<Association.Op> toRemove = new ArrayList<>();
//		Association emptyAssociation = null;
//
//		// look for empty association
//		for (Association originalAssociation : originalAssociations) {
//			if (originalAssociation.getRootNode().getChildren().isEmpty()) {
//				emptyAssociation = originalAssociation;
//				break;
//			}
//		}
//		if (emptyAssociation == null) { // if no empty association was found we are done
//			return;
//		}
//
//
//		// TODO
//
//
//		// delete removed associations
//		for (Association.Op a : toRemove) {
//			this.repository.removeAssociation(a);
//		}
//	}
//
//
//	public static void consolidate(Collection<? extends Association> associations) {
//		Map<PresenceCondition, Association> pcToAssocMap = new HashMap<>();
//		Association emptyAssoc = null;
//
//		Iterator<? extends Association> it = associations.iterator();
//		while (it.hasNext()) {
//			Association association = it.next();
//
//			// if association contains no artifacts it requires special treatment here
//			if (association.getRootNode().getChildren().size() == 0) {
//				emptyAssoc = association;
//			} else {
//				Association equalAssoc = pcToAssocMap.get(association.getPresenceCondition());
//				if (equalAssoc == null) {
//					pcToAssocMap.put(association.getPresenceCondition(), association);
//				} else {
//					Trees.merge(equalAssoc.getRootNode(), association.getRootNode());
//					it.remove();
//				}
//			}
//		}
//
//		if (emptyAssoc != null) {
//			for (Association assoc : associations) {
//				if (assoc != emptyAssoc) {
//					emptyAssoc.getPresenceCondition().getMinModules().removeAll(assoc.getPresenceCondition().getMinModules());
//				}
//			}
//		}
//
//	}

}
