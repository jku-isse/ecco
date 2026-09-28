package at.jku.isse.ecco.featuretrace;

/**
 * A proactive feature trace that contradicts the commit history, found by {@link ProactiveTraceCheck}:
 * it was not used, and its artifact kept its retroactive condition.
 *
 * @param location   where the artifact is: file and line where the adapter recorded one, else its path in the tree
 * @param artifact   the artifact the trace is on
 * @param condition  the trace's condition
 * @param direction  how it contradicts the history
 * @param commit     a commit that disproves it: its message and configuration
 * @param suggestion a condition that fits the whole history (a single feature, or all features the artifact always came with), or null
 */
public record RejectedTrace(String location, String artifact, String condition, Direction direction, String commit, String suggestion) {

	public enum Direction {
		/** false for a commit that contains the artifact */
		TOO_NARROW,
		/** true for a commit that does not contain the artifact */
		TOO_BROAD
	}

	/** One line for the user, e.g. for the warnings file. */
	public String describe() {
		String reason = this.direction == Direction.TOO_NARROW
				? "commit " + this.commit + " contains it, but the trace is false there"
				: "the trace is true for commit " + this.commit + ", which does not contain it";
		return this.location + ": trace \"" + this.condition + "\" is "
				+ (this.direction == Direction.TOO_NARROW ? "too narrow" : "too broad") + " - " + reason
				+ (this.suggestion == null ? "" : ". Suggested: " + this.suggestion);
	}
}
