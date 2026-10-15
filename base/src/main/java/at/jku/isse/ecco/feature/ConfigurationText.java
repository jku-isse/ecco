package at.jku.isse.ecco.feature;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The text of a configuration, in one place: what may be written ("A, B.2, C', [id].3") and how
 * one is printed. It was read by two regular expressions and written in six places, in different
 * orders and with ids cut to 7 characters or not.
 * <p>
 * An entry is a feature - by name, or by id in brackets - optionally with a revision: ".id" a
 * given one (at least 7 characters of an id find it, as configurations are displayed), "'" a new
 * one; without, the latest. Entries are separated by commas; spaces around them do not count.
 */
public final class ConfigurationText {

	private static final String NAME = "[a-zA-Z0-9_-]+";
	private static final String FEATURE = "((\\[" + NAME + "\\])|(" + NAME + "))";
	private static final String SEPARATOR = "\\s*,\\s*";

	/** A configuration: entries with a revision, a new revision ("'") or none. */
	public static final String REGULAR_EXPRESSION = "(" + FEATURE + "('|(\\.(" + NAME + ")))?(" + SEPARATOR + FEATURE + "('|(\\.(" + NAME + ")))?)*)?";

	/** Feature revisions: entries that each name a revision. */
	public static final String REVISIONS_REGULAR_EXPRESSION = "(" + FEATURE + "(\\.(" + NAME + "))(" + SEPARATOR + FEATURE + "(\\.(" + NAME + ")))*)?";

	/**
	 * One entry of a configuration text.
	 *
	 * @param feature     the feature's name, or its id if {@code byId}
	 * @param byId        whether the feature is given by id ("[id]")
	 * @param revision    the revision's id (or the start of it), null if none is given
	 * @param newRevision whether a new revision is meant ("'")
	 */
	public record Entry(String feature, boolean byId, String revision, boolean newRevision) {
	}

	private ConfigurationText() {
	}

	/** The entries of a configuration; spaces around the whole text do not count. */
	public static List<Entry> parse(String text) {
		return entries(text, REGULAR_EXPRESSION, "configuration");
	}

	/** The entries of a feature revisions text, each with its revision. */
	public static List<Entry> parseRevisions(String text) {
		return entries(text, REVISIONS_REGULAR_EXPRESSION, "feature revisions");
	}

	private static List<Entry> entries(String text, String regularExpression, String what) {
		String stripped = text.strip();
		if (!stripped.matches(regularExpression))
			throw new IllegalArgumentException("Invalid " + what + " string provided: " + text);
		List<Entry> entries = new ArrayList<>();
		if (stripped.isEmpty())
			return entries;
		for (String part : stripped.split(",")) {
			String entry = part.trim();
			boolean newRevision = entry.endsWith("'");
			if (newRevision)
				entry = entry.substring(0, entry.length() - 1);
			String revision = null;
			int dot = entry.indexOf('.');
			if (dot >= 0) {
				revision = entry.substring(dot + 1);
				entry = entry.substring(0, dot);
			}
			boolean byId = entry.startsWith("[") && entry.endsWith("]");
			entries.add(new Entry(byId ? entry.substring(1, entry.length() - 1) : entry, byId, revision, newRevision));
		}
		return entries;
	}

	/**
	 * [revisions] as a configuration text, sorted: with full ids - to be parsed again - or with the
	 * ids cut to 7 characters, as configurations are displayed ({@link FeatureRevision#getFeatureRevisionString()}).
	 */
	public static String print(Collection<? extends FeatureRevision> revisions, boolean fullIds) {
		return revisions.stream()
				.sorted(Comparator.comparing((FeatureRevision r) -> r.getFeature().getName()).thenComparing(FeatureRevision::getId))
				.map(r -> fullIds ? r.getFeature().getName() + "." + r.getId() : r.getFeatureRevisionString())
				.collect(Collectors.joining(", "));
	}

	/** [text] with [more] entries added - no stray comma when either is empty. */
	public static String with(String text, String more) {
		String a = text == null ? "" : text.strip(), b = more == null ? "" : more.strip();
		return a.isEmpty() ? b : b.isEmpty() ? a : a + ", " + b;
	}

	/**
	 * The entries of a text as written, trimmed, blanks dropped, in first-seen order - leniently,
	 * for texts that are not configurations yet (an LLM's suggestion with "-name" removals).
	 */
	public static LinkedHashSet<String> tokens(String text) {
		LinkedHashSet<String> tokens = new LinkedHashSet<>();
		if (text != null)
			for (String token : text.split(","))
				if (!token.isBlank())
					tokens.add(token.trim());
		return tokens;
	}
}
