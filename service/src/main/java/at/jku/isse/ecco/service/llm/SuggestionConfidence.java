package at.jku.isse.ecco.service.llm;

import at.jku.isse.ecco.feature.ConfigurationText;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * How far a feature suggestion can be trusted: whether it agrees with where the commit changed
 * things (the overlap with the known features' earlier changes, see
 * {@link ImportedFeatureTraces#overlap}). A known feature that changed the same positions before
 * is likely right; one that changed none of these places, or a new name where a known feature
 * changed exactly these positions, likely wrong. In a replay of 120 Vite commits only 4 of 42
 * suggestions below {@value #UNCERTAIN} were right, 24 of 26 at 0.9; reviewing the least confident
 * 30% would have shown 60% of the new features a suggestion missed, where reviewing every third
 * commit shows a third (experiments/llm-feature-suggestion/confidence.py, same rules).
 *
 * @param value  0 to 1
 * @param reason why, in words - for the review screen
 */
public record SuggestionConfidence(double value, String reason) {

	/** Below this, a suggestion is uncertain: the import stops for review. */
	public static final double UNCERTAIN = 0.5;

	private static final Set<String> GENERIC = LlmFeatureSuggestionClient.GENERIC;

	public boolean uncertain() {
		return this.value < UNCERTAIN;
	}

	/**
	 * The confidence of {@code suggestion} (an LLM reply: features, "-name" removals aside) for a
	 * commit with {@code overlap}, given the {@code known} features.
	 */
	public static SuggestionConfidence of(String suggestion, Collection<String> known, List<ImportedFeatureTraces.Overlap> overlap) {
		if (known.isEmpty())
			return new SuggestionConfidence(0.5, "no features known yet to compare with");
		Map<String, Integer> strength = new HashMap<>();
		for (ImportedFeatureTraces.Overlap o : overlap)
			if (known.contains(o.feature()))
				strength.put(o.feature(), !o.samePositions().isEmpty() ? 3 : !o.sameEnclosing().isEmpty() ? 2 : o.sameFiles() > 0 ? 1 : 0);
		int top = strength.values().stream().max(Integer::compare).orElse(0);
		String strongest = strength.entrySet().stream().filter(e -> e.getValue() == top).map(Map.Entry::getKey).sorted().findFirst().orElse(null);

		List<String> predicted = new ArrayList<>();
		for (String token : ConfigurationText.tokens(suggestion))
			if (!token.startsWith("-"))
				predicted.add(token);
		if (predicted.isEmpty())
			predicted.add("");

		double value = 1;
		String reason = null;
		for (String feature : predicted) {
			double part;
			String why;
			if (feature.isEmpty() || GENERIC.contains(feature)) {
				part = top < 3 ? 0.3 : 0.15;
				why = top == 3 ? "no feature suggested, but " + strongest + " changed exactly these positions before"
						: "no feature suggested (" + (feature.isEmpty() ? "nothing" : feature) + ")";
			} else if (known.contains(feature)) {
				int s = strength.getOrDefault(feature, 0);
				part = switch (s) {
					case 3 -> 0.9;
					case 2 -> 0.75;
					case 1 -> 0.6;
					default -> 0.3;
				} - (s < top ? 0.15 : 0);
				why = switch (s) {
					case 3 -> feature + " changed these positions before";
					case 2 -> feature + " changed around these positions before";
					case 1 -> feature + " changed these files before";
					default -> feature + " changed none of these places before";
				} + (s < top ? ", but " + strongest + " changed them more closely" : "");
			} else {
				part = switch (top) {
					case 0 -> 0.8;
					case 1 -> 0.6;
					case 2 -> 0.4;
					default -> 0.2;
				};
				why = top == 0 ? "new feature " + feature + ": no known feature changed these places"
						: "new feature " + feature + ", but " + strongest + " changed " + (top == 3 ? "exactly these positions" : top == 2 ? "around these positions" : "these files") + " before";
			}
			if (part < value) {
				value = part;
				reason = why;
			}
		}
		if (predicted.size() > 1)
			value -= 0.05;
		return new SuggestionConfidence(Math.max(0, Math.round(value * 100) / 100.0), reason);
	}
}
