package at.jku.isse.ecco.repository;

import at.jku.isse.ecco.logic.LogicCondition;
import at.jku.isse.ecco.featuretrace.ProactiveTraceCheck;
import at.jku.isse.ecco.core.RejectedTrace;
import at.jku.isse.ecco.composition.CheckoutComposer;
import at.jku.isse.ecco.composition.LazyCompositionRootNode;
import at.jku.isse.ecco.core.*;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.module.Condition;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.module.ModuleRevision;
import at.jku.isse.ecco.module.ModuleRevisions;
import at.jku.isse.ecco.tree.Node;
import java.util.*;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * The composition of a checkout of a configuration from the associations - moved out of {@link Repository.Op}, whose default methods delegate here.
 */
final class CheckoutComposition {

	private CheckoutComposition() {
	}

	/** See {@link Repository.Op#compose}. */
	static Checkout compose(Repository.Op repository, Configuration configuration, Map<String, LogicCondition> retroactiveConditions) {
		checkNotNull(configuration);
		checkNotNull(retroactiveConditions);

		Set<Association.Op> selectedAssociations = new HashSet<>();
		for (Association.Op association : repository.getAssociations()) {
			if (association.computeCondition().holds(configuration)) {
				selectedAssociations.add(association);
			}
		}

		Node.Op mainTree;
		List<RejectedTrace> rejectedTraces;
		if (retroactiveConditions.isEmpty()) {
			mainTree = repository.getMainTree();
			rejectedTraces = repository.getRejectedTraces();
		} else {
			ProactiveTraceCheck traceCheck = new ProactiveTraceCheck(repository.getCommits());
			mainTree = repository.getMainTreeBuildingStrategy().buildMainTree(new ArrayList<>(repository.getAssociations()), traceCheck, retroactiveConditions);
			rejectedTraces = traceCheck.getRejected();
		}
		CheckoutComposer composer = new CheckoutComposer(configuration, repository.getEvaluationStrategy());
		Checkout checkout = composer.composeCheckout(mainTree, selectedAssociations);
		checkout.getRejectedTraces().addAll(rejectedTraces);

		Set<ModuleRevision> desiredModules = new HashSet<>(repository.getOrphanedConfigurationModules(configuration));
		Set<ModuleRevision> missingModules = new HashSet<>();
		Map<ModuleRevision,String> surplusModules = new HashMap<>();

		// compute missing
		for (ModuleRevision desiredModuleRevision : desiredModules) {
			Feature[] posFeatures = Arrays.stream(desiredModuleRevision.getPos()).map(FeatureRevision::getFeature).toArray(Feature[]::new);
			Module desiredModule = repository.getModule(posFeatures, desiredModuleRevision.getNeg());
			if (desiredModule == null || desiredModule.getRevision(desiredModuleRevision.getPos(), desiredModuleRevision.getNeg()) == null) {
				missingModules.add(desiredModuleRevision);
			}
		}

		// trim missing modules to only leave modules that are LIKELY missing (see ModuleRevisions.trimRedundant)
		Set<ModuleRevision> trimmedMissingModules = ModuleRevisions.trimRedundant(missingModules);

		// locate each remaining missing module's positive features in the existing associations, for diagnostics
		Map<ModuleRevision, String> missingLocations = new HashMap<>();
		for (ModuleRevision missingModuleRevision : trimmedMissingModules) {
			missingLocations.put(missingModuleRevision, describeLocation(missingModuleRevision, repository.getAssociations()));
		}

		// compute surplus
		for (Association association : selectedAssociations) {
			Condition moduleCondition = association.computeCondition();
			if (moduleCondition.getType() == Condition.TYPE.AND) {
				Map<Module, Collection<ModuleRevision>> moduleMap = moduleCondition.getModules();
				for (Map.Entry<Module, Collection<ModuleRevision>> entry : moduleMap.entrySet()) {
					// a module's revisions are alternatives (Condition#holds): one in the
					// configuration explains it - each other one was reported surplus, so every
					// checkout of a feature in several revisions warned, also of a committed variant
					if (entry.getValue() != null && entry.getValue().stream().noneMatch(desiredModules::contains)) {
						for (ModuleRevision existingModuleRevision : entry.getValue()) {
							surplusModules.put(existingModuleRevision,association.getId());
						}
					}
				}
			}
		}

		checkout.setSurplusModules(surplusModules);
		checkout.getMissing().addAll(trimmedMissingModules);
		checkout.setMissingLocations(missingLocations);

		return checkout;
	}

	/** See {@link Repository.Op#compose}. */
	static Checkout compose(Repository.Op repository, Collection<? extends Association.Op> selectedAssociations) {
		LazyCompositionRootNode compRootNode = new LazyCompositionRootNode();
		for (Association.Op association : selectedAssociations) {
			compRootNode.addOrigNode(association.getRootNode());
		}
		Collection<Node> orderWarnings = compRootNode.getOrderSelector().getUncertainOrders();

		// compute unresolved dependencies
		DependencyGraph dg = new DependencyGraph(selectedAssociations, DependencyGraph.ReferencesResolveMode.INCLUDE_ALL_REFERENCED_ASSOCIATIONS);
		Set<Association> unresolvedAssociations = new HashSet<>(dg.getAssociations());
		unresolvedAssociations.removeAll(selectedAssociations);

		// put together result
		Checkout checkout = new Checkout();
		checkout.setNode(compRootNode);
		checkout.getOrderWarnings().addAll(orderWarnings);
		checkout.getUnresolvedAssociations().addAll(unresolvedAssociations);
		checkout.getSelectedAssociations().addAll(selectedAssociations);

		return checkout;
	}

	/**
	 * For each positive {@link at.jku.isse.ecco.feature.FeatureRevision} in {@code moduleRevision}, scans {@code associations}
	 * (the full, unfiltered collection -- a missing combination by definition isn't part of any
	 * association selected for the requested configuration) for one whose {@link
	 * Association#computeCondition()} contains a {@link Module} with that feature in its positive
	 * array, and reports the first match's association id (full id, matching the SURPLUS
	 * diagnostic's existing convention). Notes per-feature when a feature was never committed at
	 * all, e.g. {@code "FeatureA in association <id>; FeatureB not found in any association"}.
	 */
	static String describeLocation(ModuleRevision moduleRevision, Collection<? extends Association> associations) {
		List<String> parts = new ArrayList<>();
		for (FeatureRevision featureRevision : moduleRevision.getPos()) {
			Feature feature = featureRevision.getFeature();
			String associationId = findAssociationContaining(feature, associations);
			if (associationId != null) {
				parts.add(feature.getName() + " in association " + associationId);
			} else {
				parts.add(feature.getName() + " not found in any association");
			}
		}
		// same reasoning as describe(): getPos() array order isn't guaranteed, so sort for a
		// deterministic rendering of the same underlying combination.
		parts.sort(Comparator.naturalOrder());
		return String.join("; ", parts);
	}

	private static String findAssociationContaining(Feature feature, Collection<? extends Association> associations) {
		for (Association association : associations) {
			for (Module module : association.computeCondition().getModules().keySet()) {
				if (Arrays.asList(module.getPos()).contains(feature)) {
					return association.getId();
				}
			}
		}
		return null;
	}
}
