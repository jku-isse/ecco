package at.jku.isse.ecco.gui.view;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.module.Condition;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.repository.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure computation behind the Feature Model tab (see {@link FeaturesView}): places every feature in
 * a software-product-line-style tree using the repository's commit and association history, with no
 * dependency on JavaFX/GraphStream so the algorithm can be reasoned about and tested on its own.
 * <p>
 * Two computed signals decide the tree:
 * <ul>
 *     <li>{@link #computeIntroOrder} - which commit, chronologically, first referenced each
 *     feature.</li>
 *     <li>{@link #computeCoOccurrence} - whether (and how strongly) each pair of features appears
 *     together in the same conjunctive clause of an association's presence condition (i.e. actually
 *     used together in committed content, not just both existing somewhere in the repository).</li>
 * </ul>
 * Every feature's parent is, among the earlier-introduced features it co-occurs with at all, the
 * <em>most recently introduced</em> one - its nearest predecessor in commit history that it actually
 * depends on - not simply whichever one it co-occurs with most. Co-occurrence weight only breaks a
 * tie between candidates introduced in the very same commit; it is deliberately not the primary
 * signal. A feature with no earlier co-occurring feature at all becomes its own root ("first features
 * on top").
 * <p>
 * This "nearest predecessor" rule matters because co-occurrence is transitive in exactly the cases
 * that make a naive "strongest co-occurrence wins" rule collapse into a star: if a feature C was
 * built on top of B, which was built on top of A, then C's content co-occurs with BOTH A and B (often
 * equally, even more strongly with A if A is large and long-lived) even though C only actually
 * depends on B directly - A is only an indirect, transitive dependency. Picking the nearest (latest)
 * co-occurring predecessor instead performs a transitive reduction: it recovers the direct edges
 * (A-B, B-C) instead of also drawing the redundant transitive one (A-C), which is what produces a
 * real, differentiated tree instead of everything fanning out from the first-ever feature.
 * <p>
 * This is still a heuristic, not a logical derivation: it does not attempt to prove a true boolean
 * "requires" implication between features (that would need a SAT-style check across the whole
 * configuration space) - it only uses observed co-occurrence as a proxy for it.
 * <p>
 * Parent selection alone would still make the diagram's <em>depth</em> track commit count one-to-one
 * even where that isn't meaningful - a long, purely incremental rollout (B built only on A, C built
 * only on B, ...) is a straight-line dependency, not a deepening hierarchy. {@link #assignPositions}
 * therefore only steps depth down at an actual branch point: a feature with exactly one child passes
 * its own depth straight through to it, and depth only increases where a feature has more than one
 * feature built directly on top of it.
 */
public final class FeatureModelTree {

	private FeatureModelTree() {
	}

	/** One feature's computed place in the tree. */
	public static final class Placement {
		public final Feature feature;
		/** Null if {@code feature} is a root. */
		public final Feature parent;
		public final int depth;
		/** Arbitrary horizontal ordering unit (see {@link #assignPositions}) - scale for display as needed. */
		public final double x;
		/** Index into the (chronologically sorted) root list of {@code feature}'s ultimate ancestor - stable across a render for consistent grouping/coloring. */
		public final int rootIndex;

		private Placement(Feature feature, Feature parent, int depth, double x, int rootIndex) {
			this.feature = feature;
			this.parent = parent;
			this.depth = depth;
			this.x = x;
			this.rootIndex = rootIndex;
		}
	}

	public static List<Placement> compute(Repository repository) {
		Collection<? extends Feature> features = repository.getFeatures();

		Map<Feature, Integer> introOrder = computeIntroOrder(repository, features);
		Map<Feature, Map<Feature, Integer>> coOccurrence = computeCoOccurrence(repository);

		Map<Feature, Feature> parentOf = new HashMap<>();
		Map<Feature, List<Feature>> childrenOf = new HashMap<>();
		List<Feature> roots = new ArrayList<>();

		List<Feature> byIntro = new ArrayList<>(features);
		byIntro.sort(Comparator.comparingInt(introOrder::get));

		for (Feature feature : byIntro) {
			Map<Feature, Integer> candidates = coOccurrence.getOrDefault(feature, Map.of());
			Feature bestParent = null;
			int bestParentIntro = -1;
			int bestParentWeight = -1;
			for (Map.Entry<Feature, Integer> entry : candidates.entrySet()) {
				Feature candidate = entry.getKey();
				int candidateIntro = introOrder.get(candidate);
				int candidateWeight = entry.getValue();
				if (candidateIntro >= introOrder.get(feature)) {
					continue; // only an earlier-introduced feature is eligible as a parent
				}
				// nearest (most recently introduced) co-occurring predecessor wins; co-occurrence
				// weight only tie-breaks two candidates introduced in the very same commit; feature
				// id is the final, purely-for-determinism tie-break
				boolean better = bestParent == null
						|| candidateIntro > bestParentIntro
						|| (candidateIntro == bestParentIntro && candidateWeight > bestParentWeight)
						|| (candidateIntro == bestParentIntro && candidateWeight == bestParentWeight && candidate.getId().compareTo(bestParent.getId()) < 0);
				if (better) {
					bestParent = candidate;
					bestParentIntro = candidateIntro;
					bestParentWeight = candidateWeight;
				}
			}
			if (bestParent != null) {
				parentOf.put(feature, bestParent);
				childrenOf.computeIfAbsent(bestParent, k -> new ArrayList<>()).add(feature);
			} else {
				roots.add(feature);
			}
		}
		roots.sort(Comparator.comparingInt(introOrder::get));

		Map<Feature, Double> xPos = new HashMap<>();
		Map<Feature, Integer> depthOf = new HashMap<>();
		Map<Feature, Integer> rootIndexOf = new HashMap<>();
		double[] nextLeafSlot = {0};

		for (int i = 0; i < roots.size(); i++) {
			assignPositions(roots.get(i), 0, i, childrenOf, introOrder, xPos, depthOf, rootIndexOf, nextLeafSlot);
		}

		List<Placement> placements = new ArrayList<>();
		for (Feature feature : features) {
			placements.add(new Placement(feature, parentOf.get(feature),
					depthOf.getOrDefault(feature, 0), xPos.getOrDefault(feature, 0.0),
					rootIndexOf.getOrDefault(feature, 0)));
		}
		return placements;
	}

	/**
	 * Recursively assigns each feature an integer depth (root = 0) and an x "slot". A leaf, or a
	 * single-child "chain" continuation (see below), claims the next free slot for itself; a real
	 * branch (more than one child) is instead centered above the average of its children's slots -
	 * the standard simple tree-drawing technique, sufficient for a readable diagram without a
	 * dedicated layout library. Returns the assigned x so a branching parent call can average over it.
	 * <p>
	 * Depth only increases at an actual branch point - a feature with a single child passes its own
	 * depth straight through to that child, rather than every single link in a chain claiming its own
	 * tier. A long, purely incremental rollout (A introduced, then B depending only on A, then C
	 * depending only on B, ...) has no real structural depth and renders as one flat row connected by
	 * edges; depth only grows where the model genuinely diverges (a feature with more than one child,
	 * i.e. more than one feature was built directly on top of it).
	 * <p>
	 * That flat row still needs distinct x positions, though: a naive "average of children" x for a
	 * single-child node would just inherit that one child's x verbatim, which - stacked with every
	 * other link in the chain doing the same thing, at the same unchanging depth - would render every
	 * node in the chain at the exact same coordinates, hiding all but the last one drawn. So a
	 * single-child node is treated the same as a leaf for x-slot purposes (claims its own slot) and
	 * only then recurses into that child, which claims the next slot in turn.
	 */
	private static double assignPositions(Feature feature, int depth, int rootIndex,
										   Map<Feature, List<Feature>> childrenOf, Map<Feature, Integer> introOrder,
										   Map<Feature, Double> xPos, Map<Feature, Integer> depthOf,
										   Map<Feature, Integer> rootIndexOf, double[] nextLeafSlot) {
		depthOf.put(feature, depth);
		rootIndexOf.put(feature, rootIndex);

		List<Feature> children = new ArrayList<>(childrenOf.getOrDefault(feature, List.of()));
		children.sort(Comparator.comparingInt(introOrder::get));

		int childDepth = depth + (children.size() > 1 ? 1 : 0);

		double x;
		if (children.size() <= 1) {
			x = nextLeafSlot[0];
			nextLeafSlot[0] += 1.0;
			if (children.size() == 1) {
				assignPositions(children.get(0), childDepth, rootIndex, childrenOf, introOrder, xPos, depthOf, rootIndexOf, nextLeafSlot);
			}
		} else {
			double sum = 0;
			for (Feature child : children) {
				sum += assignPositions(child, childDepth, rootIndex, childrenOf, introOrder, xPos, depthOf, rootIndexOf, nextLeafSlot);
			}
			x = sum / children.size();
		}
		xPos.put(feature, x);
		return x;
	}

	/** First commit (chronologically) whose configuration references each feature. */
	private static Map<Feature, Integer> computeIntroOrder(Repository repository, Collection<? extends Feature> features) {
		List<Commit> commitsByDate = new ArrayList<>(repository.getCommits());
		commitsByDate.sort(Comparator.comparing(Commit::getDate));

		Map<Feature, Integer> introOrder = new HashMap<>();
		for (int i = 0; i < commitsByDate.size(); i++) {
			Configuration configuration = commitsByDate.get(i).getConfiguration();
			if (configuration == null) {
				continue;
			}
			for (FeatureRevision featureRevision : configuration.getFeatureRevisions()) {
				introOrder.putIfAbsent(featureRevision.getFeature(), i);
			}
		}
		// a feature never referenced by any commit's configuration (shouldn't normally happen)
		// sorts last rather than making a comparator using this map crash on a missing key
		for (Feature feature : features) {
			introOrder.putIfAbsent(feature, Integer.MAX_VALUE);
		}
		return introOrder;
	}

	/**
	 * Whether, and how often, each pair of features appears together in the same conjunctive clause
	 * of an association's presence condition (a {@link Module}, i.e. one AND-term of a
	 * {@code Condition}) - i.e. whether they were actually used together in committed content, as
	 * opposed to merely coexisting in the repository. {@link #compute} uses presence/absence of an
	 * entry here as the actual dependency signal; the count itself is only a tie-break (see there).
	 */
	private static Map<Feature, Map<Feature, Integer>> computeCoOccurrence(Repository repository) {
		Map<Feature, Map<Feature, Integer>> coOccurrence = new HashMap<>();
		for (Association association : repository.getAssociations()) {
			Condition condition = association.computeCondition();
			for (Module module : condition.getModules().keySet()) {
				Feature[] pos = module.getPos();
				for (Feature a : pos) {
					for (Feature b : pos) {
						if (a.equals(b)) {
							continue;
						}
						coOccurrence.computeIfAbsent(a, k -> new HashMap<>()).merge(b, 1, Integer::sum);
					}
				}
			}
		}
		return coOccurrence;
	}

}
