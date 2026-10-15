package at.jku.isse.ecco.module;

import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * How conditions read for people, the same everywhere they are shown (views, command line, REST,
 * the .warnings file): a disjunction of terms, each a conjunction of literals - "A & !B | C" -,
 * "true" and "false". A literal is a feature, with its displayed revision ("A.1a2b3c4") or without,
 * or "!" and a feature for its absence. Terms another term absorbs (X | X & Y = X) are left out;
 * terms are sorted, and literals with the features first, then the absent ones.
 * <p>
 * The logic strings conditions are stored and evaluated as are {@link Condition#toLogicString()}.
 */
public final class ConditionText {

	/** Present features before absent ones ("A & !B"), each alphabetically. */
	private static final Comparator<String> LITERAL_ORDER = Comparator.comparing((String literal) -> literal.startsWith("!")).thenComparing(Comparator.naturalOrder());

	private ConditionText() {
	}

	/** The literals of [revision]: its features (with revisions, or names only) and "!" its negative ones. */
	public static SortedSet<String> literals(ModuleRevision revision, boolean withRevisions) {
		SortedSet<String> literals = new TreeSet<>(LITERAL_ORDER);
		for (FeatureRevision featureRevision : revision.getPos())
			literals.add(withRevisions ? featureRevision.getFeatureRevisionString() : featureRevision.getFeature().getName());
		for (Feature feature : revision.getNeg())
			literals.add("!" + feature.getName());
		return literals;
	}

	/** One module revision as a term, "A.1a2b3c4 & !B" - "true" if it has no literals. */
	public static String term(ModuleRevision revision) {
		return join(List.of(literals(revision, true)));
	}

	/** The disjunction of [terms], each a set of literals, absorbed and sorted. */
	public static String join(Collection<? extends Collection<String>> terms) {
		List<SortedSet<String>> sorted = new ArrayList<>();
		for (Collection<String> term : terms) {
			SortedSet<String> literals = new TreeSet<>(LITERAL_ORDER);
			literals.addAll(term);
			sorted.add(literals);
		}
		// smallest first: a term can only be absorbed by one with at most as many literals
		sorted.sort(Comparator.comparingInt(SortedSet::size));
		List<SortedSet<String>> kept = new ArrayList<>();
		for (SortedSet<String> candidate : sorted) {
			if (candidate.isEmpty())
				return "true"; // an empty conjunction always holds
			if (kept.stream().noneMatch(candidate::containsAll))
				kept.add(candidate);
		}
		if (kept.isEmpty())
			return "false";
		List<String> rendered = kept.stream().map(term -> String.join(" & ", term)).sorted().collect(Collectors.toList());
		return String.join(" | ", rendered);
	}
}
