package at.jku.isse.ecco.module;

import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
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
				if (other.getOrder() < candidate.getOrder() && other.isImpliedBy(candidate)) {
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
		return at.jku.isse.ecco.feature.ConfigurationText.print(Arrays.asList(featureRevisions), true);
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
