package at.jku.isse.ecco.gui.view.graph;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.module.Condition;
import at.jku.isse.ecco.repository.Repository;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Where an association's artifacts come from, for people to read: the condition of features it
 * traces to - or, when it traces to none, the commits that have it. A Git import's old lines,
 * replaced since, trace to no feature: their condition is empty, and showed as "false", as if they
 * had never been there. One per drawing of an order graph; the commits are looked through once, when
 * first needed, in the background.
 */
final class ArtifactOrigins {

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());
	/** Characters of a commit message quoted. */
	static final int MESSAGE_TEXT = 40;

	private final Repository repository;
	private Map<Association, List<Commit>> commitsOf;

	ArtifactOrigins(Repository repository) {
		this.repository = repository;
	}

	/** "A | B & !C", or "in no feature; 37 commits, the last 2009-05-11 'Fix the ...'". */
	String describe(Association association) {
		String condition = condition(association);
		if (condition != null)
			return condition;
		List<Commit> commits = this.commits().getOrDefault(association, List.of());
		if (commits.isEmpty())
			return "in no feature, no commit";
		Commit last = commits.get(0);
		for (Commit commit : commits)
			if (commit.getDate() != null && (last.getDate() == null || commit.getDate().after(last.getDate())))
				last = commit;
		String message = last.getCommitMessage() == null ? "" : last.getCommitMessage().lines().findFirst().orElse("").trim();
		if (message.length() > MESSAGE_TEXT)
			message = message.substring(0, MESSAGE_TEXT - 1) + "…";
		return "in no feature; " + (commits.size() == 1 ? "1 commit" : commits.size() + " commits, the last")
				+ (last.getDate() != null ? " " + DATE.format(last.getDate().toInstant()) : "")
				+ (message.isEmpty() ? "" : " '" + message + "'");
	}

	/** The association's condition by features - its minimized one where stored - or null if it traces to none. */
	static String condition(Association association) {
		if (association instanceof Association.Op op && op.getMinimizedCondition() != null) {
			String minimized = op.getMinimizedCondition().trim();
			if (!minimized.isEmpty() && !minimized.equals("false"))
				return minimized;
		}
		Condition condition = association.computeCondition();
		return condition.getModules().isEmpty() ? null : condition.toDisplayString(false);
	}

	private Map<Association, List<Commit>> commits() {
		if (this.commitsOf == null) {
			Map<Association, List<Commit>> commitsOf = new IdentityHashMap<>();
			for (Commit commit : this.repository.getCommits())
				for (Association association : commit.getAssociations())
					commitsOf.computeIfAbsent(association, a -> new ArrayList<>()).add(commit);
			this.commitsOf = commitsOf;
		}
		return this.commitsOf;
	}
}
