package at.jku.isse.ecco.featuretrace;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.logic.FormulaFactoryProvider;
import at.jku.isse.ecco.logic.LogicUtils;
import at.jku.isse.ecco.tree.ArtifactDiagnostics;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.util.Location;
import org.logicng.datastructures.Assignment;
import org.logicng.formulas.Formula;
import org.logicng.formulas.FormulaFactory;

import java.util.*;

/**
 * Checks proactive feature traces against the commit history before they are used (and boosted):
 * every commit is a whole variant with a known configuration, so an artifact's trace must be true
 * for every commit that contains the artifact, and false for every commit that does not. A trace
 * that contradicts a commit is provably wrong - it is removed (the artifact keeps its retroactive
 * condition, and the trace is not boosted to the rest of its association) and reported.
 * <p>
 * Measured on VEVOS ground truth for five C systems (libssh, irssi, openvpn, busybox, berkeley-db):
 * results with correct traces are unchanged; with wrong traces it catches 86-92% of swapped
 * features, 76-84% of swapped conditions and 51-61% of added conjunctions, rejecting at most 0.2%
 * of correct traces, and boosting no longer spreads what it catches.
 */
public class ProactiveTraceCheck {

	private final List<Commit> commits = new ArrayList<>();
	private final List<Assignment> assignments = new ArrayList<>();
	// each commit's selected features, by the names proactive conditions use (sanitized like them)
	private final List<Set<String>> features = new ArrayList<>();
	private final List<RejectedTrace> rejected = new ArrayList<>();

	public ProactiveTraceCheck(Collection<? extends Commit> commits) {
		for (Commit commit : commits) {
			if (commit.getConfiguration() == null)
				continue;
			this.commits.add(commit);
			this.assignments.add(commit.getConfiguration().toAssignment());
			Set<String> names = new TreeSet<>();
			for (FeatureRevision revision : commit.getConfiguration().getFeatureRevisions())
				names.add(sanitize(revision.getFeature().getName()));
			this.features.add(names);
		}
	}

	/**
	 * Checks the proactive conditions in {@code tree}, a copy of {@code association}'s tree about to
	 * go into the main tree, and removes those that contradict the history.
	 */
	public void check(Association association, Node.Op tree) {
		if (this.commits.isEmpty())
			return;
		boolean[] present = new boolean[this.commits.size()];
		for (int i = 0; i < present.length; i++)
			present[i] = this.commits.get(i).containsAssociation(association);
		tree.traverse((Node.Op.NodeVisitor) node -> this.check(node, present));
	}

	public List<RejectedTrace> getRejected() {
		return Collections.unmodifiableList(this.rejected);
	}

	private void check(Node.Op node, boolean[] present) {
		FeatureTrace trace = node.getFeatureTrace();
		if (trace == null || !node.isUnique() || trace.getProactiveConditionString() == null)
			return;
		String condition = trace.getProactiveConditionString();
		int contradicting = this.firstContradiction(LogicUtils.parseString(condition), present);
		if (contradicting < 0)
			return;
		trace.removeProactiveCondition();
		Commit commit = this.commits.get(contradicting);
		this.rejected.add(new RejectedTrace(
				describeLocation(node),
				String.valueOf(node.getArtifact()),
				condition,
				present[contradicting] ? RejectedTrace.Direction.TOO_NARROW : RejectedTrace.Direction.TOO_BROAD,
				"\"" + commit.getCommitMessage() + "\" (" + commit.getConfiguration() + ")",
				this.suggest(present)));
	}

	/** The index of the first commit {@code formula} contradicts, or -1. */
	private int firstContradiction(Formula formula, boolean[] present) {
		for (int i = 0; i < present.length; i++)
			if (formula.evaluate(this.assignments.get(i)) != present[i])
				return i;
		return -1;
	}

	/**
	 * A condition consistent with the history: the first single feature that is, else the
	 * conjunction of all features every commit containing the artifact had, if that is - or null.
	 */
	private String suggest(boolean[] present) {
		Set<String> common = null;
		for (int i = 0; i < present.length; i++) {
			if (!present[i])
				continue;
			if (common == null)
				common = new TreeSet<>(this.features.get(i));
			else
				common.retainAll(this.features.get(i));
		}
		if (common == null || common.isEmpty())
			return null;
		FormulaFactory f = FormulaFactoryProvider.getFormulaFactory();
		for (String feature : common)
			if (this.firstContradiction(f.variable(feature), present) < 0)
				return feature;
		String conjunction = String.join(" & ", common);
		return this.firstContradiction(LogicUtils.parseString(conjunction), present) < 0 ? conjunction : null;
	}

	private static String describeLocation(Node node) {
		Optional<Location> location = node.getProperty("Location");
		if (location.isPresent())
			return location.get().getFilePath() + ":" + location.get().getStartLine();
		return ArtifactDiagnostics.describePath(node);
	}

	// like SerFeatureTrace does to condition strings - '.' and '-' can't be parsed
	private static String sanitize(String name) {
		return name.replace(".", "_").replace("-", "_");
	}
}
