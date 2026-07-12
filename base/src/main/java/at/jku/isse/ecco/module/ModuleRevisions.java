package at.jku.isse.ecco.module;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Trims, ranks, and renders {@link ModuleRevision}s reported as {@code MISSING} by
 * {@link at.jku.isse.ecco.repository.Repository.Op#compose}, so a checkout's diagnostics are non-redundant and
 * user-understandable instead of a raw, unordered dump of {@link ModuleRevision#toString()}.
 */
public final class ModuleRevisions {

	private ModuleRevisions() {
	}

	/**
	 * True if every {@link FeatureRevision} in {@code sub.getPos()} is in {@code sup.getPos()}, and
	 * every {@link Feature} in {@code sub.getNeg()} is in {@code sup.getNeg()} -- a symmetric
	 * two-set-containment check. Deliberately not {@link ModuleRevision#implies}, which is
	 * asymmetric (ignores {@code this.getNeg()}) and is relied on elsewhere with that semantics.
	 */
	private static boolean isSubCombination(ModuleRevision sub, ModuleRevision sup) {
		return Arrays.asList(sup.getPos()).containsAll(Arrays.asList(sub.getPos()))
				&& Arrays.asList(sup.getNeg()).containsAll(Arrays.asList(sub.getNeg()));
	}

	/**
	 * Drops any missing module revision that is redundant because a strictly-smaller-order missing
	 * module revision already explains it -- resolves the "trim set of missing modules" TODO in
	 * {@link at.jku.isse.ecco.repository.Repository.Op#compose}. A single lower-order match is sufficient: the repository's
	 * module space is closed under sub-combination up to {@code maxOrder} per association, so
	 * non-existence is monotonic -- if {@code (A,B)} was never recorded, {@code (A,B,C)} can't have
	 * been either.
	 */
	public static Set<ModuleRevision> trimRedundant(Set<ModuleRevision> missing) {
		Set<ModuleRevision> trimmed = new HashSet<>();
		for (ModuleRevision candidate : missing) {
			boolean redundant = false;
			for (ModuleRevision other : missing) {
				if (other.getOrder() < candidate.getOrder() && isSubCombination(other, candidate)) {
					redundant = true;
					break;
				}
			}
			if (!redundant) {
				trimmed.add(candidate);
			}
		}
		return trimmed;
	}

	/**
	 * Human-readable rendering using {@link Feature#getName()}, e.g. {@code "FeatureA + FeatureB"},
	 * or {@code "FeatureA + FeatureB (without FeatureC)"} when {@code getNeg()} is non-empty.
	 * Replaces the raw {@code d^N(FeatureA.abc1234, ...)} hash-suffixed format. Feature names are
	 * sorted -- {@link ModuleRevision#getPos()}/{@code getNeg()} array order isn't guaranteed, and
	 * this is also what {@link #RELEVANCE_ORDER} ties on, so the same underlying combination must
	 * always render identically.
	 */
	public static String describe(ModuleRevision moduleRevision) {
		String pos = Arrays.stream(moduleRevision.getPos())
				.map(featureRevision -> featureRevision.getFeature().getName())
				.sorted()
				.collect(Collectors.joining(" + "));
		String base = pos.isEmpty() ? "(no features)" : pos;
		if (moduleRevision.getNeg().length == 0) {
			return base;
		}
		String neg = Arrays.stream(moduleRevision.getNeg())
				.map(Feature::getName)
				.sorted()
				.collect(Collectors.joining(", "));
		return base + " (without " + neg + ")";
	}

	/**
	 * Primary key: {@link ModuleRevision#getOrder()} ascending -- fewer required features is a more
	 * fundamental, more actionable gap. Secondary key: {@link #describe}, for a deterministic
	 * display order (raw {@code Set} iteration is not).
	 */
	public static final Comparator<ModuleRevision> RELEVANCE_ORDER =
			Comparator.comparingInt(ModuleRevision::getOrder).thenComparing(ModuleRevisions::describe);

	/**
	 * For each positive {@link FeatureRevision} in {@code moduleRevision}, scans {@code associations}
	 * (the full, unfiltered collection -- a missing combination by definition isn't part of any
	 * association selected for the requested configuration) for one whose {@link
	 * Association#computeCondition()} contains a {@link Module} with that feature in its positive
	 * array, and reports the first match's association id (full id, matching the SURPLUS
	 * diagnostic's existing convention). Notes per-feature when a feature was never committed at
	 * all, e.g. {@code "FeatureA in association <id>; FeatureB not found in any association"}.
	 */
	public static String describeLocation(ModuleRevision moduleRevision, Collection<? extends Association> associations) {
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

	/**
	 * The exact configuration string a fix for {@code moduleRevision} should be committed under:
	 * the checkout's *entire* requested configuration -- not just the handful of features
	 * {@code moduleRevision} itself names. Committing only those (and omitting the checkout's other
	 * requested features) would likely just leave a different combination missing next time.
	 * Renders each feature with its exact revision id (not {@link Feature#getName()} alone, which
	 * would resolve to whichever revision happens to be latest at commit time -- not necessarily the
	 * one actually being checked out), so the string is round-trippable through
	 * {@code EccoService#parseConfigurationString}. Falls back to {@code moduleRevision}'s own
	 * positive features if {@code configuration} is null (defensive, mirroring the existing
	 * null-check around {@code Checkout#getConfiguration()} in {@code CheckoutDetailView}).
	 */
	public static String suggestedConfigurationString(ModuleRevision moduleRevision, Configuration configuration) {
		FeatureRevision[] featureRevisions = configuration != null ? configuration.getFeatureRevisions() : moduleRevision.getPos();
		return Arrays.stream(featureRevisions)
				.map(featureRevision -> featureRevision.getFeature().getName() + "." + featureRevision.getId())
				.sorted()
				.collect(Collectors.joining(","));
	}

	/**
	 * A ready-to-use suggestion for resolving a MISSING combination: since it just means nobody has
	 * committed content under the checkout's requested configuration yet, the fix is to commit
	 * content under {@link #suggestedConfigurationString}.
	 */
	public static String suggestFix(ModuleRevision moduleRevision, Configuration configuration) {
		return "commit content under the full checkout configuration to add " + describe(moduleRevision)
				+ " (e.g. `ecco commit -c \"" + suggestedConfigurationString(moduleRevision, configuration) + "\" -m \"...\"`)";
	}

}
