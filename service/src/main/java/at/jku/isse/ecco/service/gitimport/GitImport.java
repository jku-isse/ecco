package at.jku.isse.ecco.service.gitimport;

import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.ConfigurationText;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.mining.FeatureModelTree;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.service.LlmPreferences;
import at.jku.isse.ecco.service.git.GitCommitInfo;
import at.jku.isse.ecco.service.git.GitHistoryReader;
import at.jku.isse.ecco.service.llm.FeatureSuggestionLog;
import at.jku.isse.ecco.service.llm.ImportedFeatureTraces;
import at.jku.isse.ecco.service.llm.LlmFeatureSuggestionClient;
import at.jku.isse.ecco.service.llm.SuggestionConfidence;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * One import of a range of commits of a local Git clone into the open repository, one ECCO commit
 * per Git commit, oldest first: what is done with each commit, its LLM feature suggestion, its
 * commit, and the state carried from one to the next - the running configuration, the features
 * known, the log entries, the counts. Free of any user interface: the Import from Git dialog
 * ({@code ImportGitView}) asks {@link #next} what to do with a commit, shows a review where that
 * says so, and reports back.
 * <p>
 * Not thread-safe: the steps run one after another, the blocking ones ({@link #requestSuggestion},
 * {@link #commit}, {@link #foldChanges}) in the background and the others where the caller decides
 * (the dialog: the FX thread), as when this was part of the dialog. Log lines go to the consumer
 * given, from the thread that writes them.
 */
public final class GitImport {

	private static final Logger LOGGER = Logger.getLogger(GitImport.class.getName());

	/** What to do with a commit next - see {@link #next}. Each says which commit, by its index (oldest first). */
	public sealed interface Step permits Finished, Review, Import, Unattended, SuggestForReview {
	}

	/** Every commit is done. */
	public record Finished() implements Step {
	}

	/**
	 * A person reviews commit [index], its configuration proposed as [configuration]; [suggestionFailure]
	 * says why there is no suggestion, if one was asked for and failed.
	 */
	public record Review(int index, String configuration, String suggestionFailure) implements Step {
	}

	/** Commit [index] is imported with [configuration], unattended. */
	public record Import(int index, String configuration) implements Step {
	}

	/**
	 * Commit [index] is imported unattended: without suggestions, with {@link #unattendedConfiguration};
	 * with them, after {@link #requestSuggestion}, as {@link #afterUnattendedSuggestion} says.
	 */
	public record Unattended(int index) implements Step {
	}

	/** Commit [index] is reviewed after its suggestion ({@link #requestSuggestion}, then {@link #afterSuggestionForReview}). */
	public record SuggestForReview(int index) implements Step {
	}

	/** A commit's suggestion: the reply, the model asked, and the size of the commit's diff. */
	public record Suggested(LlmFeatureSuggestionClient.SuggestionBatch batch, String model, int diffLines, SuggestionConfidence confidence) {

		public Suggested(LlmFeatureSuggestionClient.SuggestionBatch batch, String model, int diffLines) {
			this(batch, model, diffLines, null);
		}
	}

	/** What a suggestion request for a commit sends: the commit with its evidence, and the known features with their traces. */
	public record SuggestionInput(LlmFeatureSuggestionClient.CommitForSuggestion commit, List<LlmFeatureSuggestionClient.KnownFeature> knownFeatures) {
	}

	/** A commit imported: the ECCO commit, and what the Git commit changed (null if that could not be found). */
	public record Imported(Commit commit, ImportedFeatureTraces.Changes changes) {
	}

	private final EccoService service;
	private final GitHistoryReader gitHistoryReader;
	private final Path repoDir;
	private final List<GitCommitInfo> commits;
	private final boolean useLlm;
	private final boolean reviewEach;
	/** Whether an unattended commit whose suggestion is uncertain (see SuggestionConfidence) stops for review. */
	private final boolean reviewUncertain;
	/** The clone's tags by the commit each names (see {@link GitHistoryReader#listTags}). */
	private final Map<String, List<String>> tags;
	/**
	 * Whether only the tagged commits of the range are imported - its releases, fewer and bigger
	 * steps. The commits between are still suggested, one at a time, and their features go into the
	 * next tagged one (see {@link #folds}): on zip4j all of its code was traced at maxOrder 2, with
	 * 25 commits imported of 275.
	 */
	private final boolean taggedOnly;
	private final Consumer<String> log;

	private LinkedHashSet<String> runningFeatures = new LinkedHashSet<>();
	/** The repository the run imports into - closed, or another one opened, the run stops. */
	private Path runRepositoryDir;
	/** The names of the features the repository has, for this run - named again, one gets a new revision. */
	private Set<String> knownFeatures = new HashSet<>();
	private final List<String> allConstraintWarnings = new ArrayList<>();
	// suggestion failures of unattended commits, for the summary: a single log line during an
	// unattended stretch is easy to miss - a review screen shows its own failure prominently
	private final List<String> llmSuggestionFailures = new ArrayList<>();
	// the commit being reviewed or imported, with its suggestion if one was asked for, recorded with
	// what became of it once it is imported or skipped (see FeatureSuggestionLog)
	private FeatureSuggestionLog.Entry pendingSuggestion;
	// where each feature's imported commits changed things, for the suggestions: read from the log
	// at the first suggestion request, then kept up to date by each import
	private volatile ImportedFeatureTraces importedTraces;
	private Commit lastCommit;
	private int importedCount;
	/** Commits left out on their review screen, for the summary. */
	private int skippedCount;
	/** In an import of tagged commits only: the features a commit since the last one imported revised. */
	private final LinkedHashSet<String> revisedSinceImport = new LinkedHashSet<>();
	/** Commits suggested but not imported, their features gone into the next tagged one - for the summary. */
	private int foldedCount;
	/**
	 * The log entries of the commits folded since the last import, written with the next tagged
	 * commit once it is imported: written at once, those of a run stopped (or failing, or gone
	 * back from) before it stayed in the log and counted for features never committed, and a run
	 * again over them wrote them again.
	 */
	private final List<FeatureSuggestionLog.Entry> foldedEntries = new ArrayList<>();
	/**
	 * Whether the repository has no commit yet - read once when the run starts, cleared by its first
	 * import. Asking the service for each commit, on the FX thread, waited for its lock while a view
	 * read the repository in the background (the Charts' absorbed modules took minutes on a zip4j
	 * import at maxOrder 2) and froze the window.
	 */
	private boolean repositoryEmpty;

	/**
	 * A run importing [commitsOldestFirst] of the clone at [repoDir] into [service]'s open
	 * repository; {@link #start} begins it.
	 *
	 * @param taggedOnly      whether only the commits with one of [tags] (commit id to tag names) are imported
	 * @param useLlm          whether an LLM suggests each commit's features (see {@link LlmPreferences})
	 * @param reviewEach      whether each commit is reviewed before it is imported; otherwise they are
	 *                        imported unattended, stopping only for a failure or an uncertain suggestion
	 * @param reviewUncertain whether an unattended commit stops for review when its suggestion is uncertain
	 * @param log             takes the lines of the import log, each ending with a line separator
	 */
	public GitImport(EccoService service, GitHistoryReader gitHistoryReader, Path repoDir, Map<String, List<String>> tags, boolean taggedOnly,
					 List<GitCommitInfo> commitsOldestFirst, boolean useLlm, boolean reviewEach, boolean reviewUncertain, Consumer<String> log) {
		this.service = service;
		this.gitHistoryReader = gitHistoryReader;
		this.repoDir = repoDir;
		this.tags = tags;
		this.taggedOnly = taggedOnly;
		this.commits = List.copyOf(commitsOldestFirst);
		this.useLlm = useLlm;
		this.reviewEach = reviewEach;
		this.reviewUncertain = reviewUncertain;
		this.log = log;
	}

	/** Reads what the run starts from: the repository it imports into, the features it has, whether it has a commit. */
	public void start() {
		this.runRepositoryDir = this.service.getRepositoryDir();
		if (this.service.isInitialized())
			for (Feature feature : this.service.getRepository().getFeatures())
				this.knownFeatures.add(feature.getName());
		this.repositoryEmpty = this.service.getCommits().isEmpty();
	}

	/**
	 * After {@link #start}: the first commit of the run builds on the features of the repository's
	 * latest commit, as each later one builds on the one before - an import going on where an earlier
	 * one stopped ({@code ecco git-import --from}). Without, a run into a repository with commits
	 * starts from no feature: unattended and without suggestions, each commit is committed with its
	 * tree's {@code .config} (and refused without one), with suggestions only with those suggested.
	 * Nothing to build on in an empty repository.
	 */
	public void continueFromLatestCommit() {
		Commit latest = null;
		for (Commit commit : this.service.getCommits())
			if (latest == null || (commit.getDate() != null && (latest.getDate() == null || !commit.getDate().before(latest.getDate()))))
				latest = commit;
		if (latest != null)
			for (FeatureRevision revision : latest.getConfiguration().getFeatureRevisions())
				this.runningFeatures.add(revision.getFeature().getName());
	}

	/**
	 * What to do with commit [index], or the first after it to be done: finished if all are, the
	 * commits before the first tagged one into an empty repository folded into it (logged), the first
	 * commit into an empty repository BASE - the root of the feature model, without asking the LLM -
	 * then each reviewed (after its suggestion, if suggestions are on) or imported unattended.
	 */
	public Step next(int index) {
		while (index < this.commits.size() && this.repositoryEmpty && this.folds(this.commits.get(index))) {
			// before the first tagged commit, which is BASE: all of their code is in it
			this.foldedCount++;
			this.log.accept(String.format("%s (%s): before the first tagged commit - part of %s.%n",
					this.commits.get(index).getShortId(), this.commits.get(index).getMessage(), LlmFeatureSuggestionClient.BASE));
			index++;
		}
		if (index >= this.commits.size())
			return new Finished();
		GitCommitInfo commit = this.commits.get(index);
		if (this.repositoryEmpty) {
			// the root of the feature model: the code base as first imported, all of it, before any
			// feature is told apart in it - the LLM named three features for a first snapshot, which
			// then owned all of its code alike and could not be told apart
			String config = LlmFeatureSuggestionClient.BASE;
			if (this.reviewEach) {
				this.pendingSuggestion = this.suggestionEntry(commit, null, null, config, FeatureSuggestionLog.Outcome.REVIEWED);
				return new Review(index, config, null);
			}
			this.pendingSuggestion = this.suggestionEntry(commit, null, null, config, FeatureSuggestionLog.Outcome.AUTO);
			return new Import(index, config);
		}
		if (!this.reviewEach || this.folds(commit))
			// a commit between tagged ones is reviewed only when its suggestion failed or is uncertain
			return new Unattended(index);
		if (this.useLlm)
			return new SuggestForReview(index);
		String defaultConfig = this.proposedConfiguration(commit, "");
		this.pendingSuggestion = this.suggestionEntry(commit, null, null, defaultConfig, FeatureSuggestionLog.Outcome.REVIEWED);
		return new Review(index, defaultConfig, null);
	}

	/** The configuration an unattended commit [index] is imported with when there are no suggestions. */
	public String unattendedConfiguration(int index) {
		GitCommitInfo commit = this.commits.get(index);
		String config = this.proposedConfiguration(commit, "");
		this.pendingSuggestion = this.suggestionEntry(commit, null, null, config, FeatureSuggestionLog.Outcome.AUTO);
		return config;
	}

	/**
	 * Unattended commit [index] got its suggestion: imported with it - or reviewed if it failed
	 * (logged and counted for the summary, see {@link #stopForFailedSuggestion}) or is uncertain.
	 */
	public Step afterUnattendedSuggestion(int index, Suggested suggested) {
		GitCommitInfo commit = this.commits.get(index);
		LlmFeatureSuggestionClient.SuggestionBatch batch = suggested.batch();
		String config = this.proposedConfiguration(commit, batch.configurations().get(0));
		if (batch.failureReason() != null)
			return this.stopForFailedSuggestion(index, suggested, config, batch.failureReason());
		SuggestionConfidence confidence = suggested.confidence();
		if (this.reviewUncertain && confidence != null && confidence.uncertain()) {
			// not trusted: a person decides, as on a review commit
			this.pendingSuggestion = this.suggestionEntry(commit, suggested, null, config, FeatureSuggestionLog.Outcome.REVIEWED);
			return new Review(index, config, null);
		}
		this.pendingSuggestion = this.suggestionEntry(commit, suggested, batch.failureReason(), config, FeatureSuggestionLog.Outcome.AUTO);
		return new Import(index, config);
	}

	/** Asking for unattended commit [index]'s suggestion failed with [failure]: it is reviewed. */
	public Review afterUnattendedSuggestionFailed(int index, Throwable failure) {
		String config = this.proposedConfiguration(this.commits.get(index), "");
		return this.stopForFailedSuggestion(index, null, config, String.valueOf(failure));
	}

	/**
	 * A suggestion failed for an unattended commit: it stops for review and says why. It was imported
	 * with the configuration of the commit before - tinycc's import had the LLM unreachable for 1,122
	 * of its 1,139 commits, all of them got the same features, and its code could be traced to none.
	 * The failure is logged with a marker that stands out from the "Imported ..." lines, and kept for
	 * the summary.
	 */
	private Review stopForFailedSuggestion(int index, Suggested suggested, String config, String reason) {
		GitCommitInfo commit = this.commits.get(index);
		this.llmSuggestionFailures.add(commit.getShortId() + ": " + reason);
		this.log.accept("*** LLM SUGGESTION FAILED for " + commit.getShortId() + " (stopped for review): " + reason + System.lineSeparator());
		this.pendingSuggestion = this.suggestionEntry(commit, suggested, reason, config, FeatureSuggestionLog.Outcome.REVIEWED);
		return new Review(index, config, reason);
	}

	/** Commit [index] got its suggestion: reviewed, with the configuration it proposes. */
	public Review afterSuggestionForReview(int index, Suggested suggested) {
		GitCommitInfo commit = this.commits.get(index);
		LlmFeatureSuggestionClient.SuggestionBatch batch = suggested.batch();
		String defaultConfig = this.proposedConfiguration(commit, batch.configurations().get(0));
		this.pendingSuggestion = this.suggestionEntry(commit, suggested, batch.failureReason(), defaultConfig, FeatureSuggestionLog.Outcome.REVIEWED);
		return new Review(index, defaultConfig, batch.failureReason());
	}

	/**
	 * Asking for commit [index]'s suggestion failed with [failure] - LlmFeatureSuggestionClient itself
	 * never throws, but gathering its inputs can (a commit JGit cannot diff, a repository access
	 * problem): reviewed, with the configuration filled in by hand.
	 */
	public Review afterSuggestionForReviewFailed(int index, Throwable failure) {
		GitCommitInfo commit = this.commits.get(index);
		String defaultConfig = this.proposedConfiguration(commit, "");
		this.pendingSuggestion = this.suggestionEntry(commit, null, String.valueOf(failure), defaultConfig, FeatureSuggestionLog.Outcome.REVIEWED);
		return new Review(index, defaultConfig, String.valueOf(failure));
	}

	/** Commit [index] was left out on its review screen. */
	public void skip(int index) {
		this.skippedCount++;
		this.recordSuggestionOutcome(this.commits.get(index), null, null);
	}

	/** Whether [commit] is not imported, only suggested: its features go into the next tagged commit (see {@link #taggedOnly}). */
	public boolean folds(GitCommitInfo commit) {
		return this.taggedOnly && !this.tags.containsKey(commit.getId());
	}

	/** What the run imports of its commits: all, or the tagged ones. */
	public int toImport() {
		return this.taggedOnly ? (int) this.commits.stream().filter(commit -> !this.folds(commit)).count() : this.commits.size();
	}

	/**
	 * The configuration proposed for [commit] from [suggestion] (see {@link #defaultConfigurationText}) -
	 * for a tagged commit of an import of tagged commits only, with a new revision of each feature a
	 * commit since the last import revised too.
	 */
	private String proposedConfiguration(GitCommitInfo commit, String suggestion) {
		String proposed = defaultConfigurationText(this.runningFeatures, this.knownFeatures, suggestion);
		return this.taggedOnly && !this.folds(commit) ? releaseConfigurationText(proposed, this.revisedSinceImport, this.knownFeatures) : proposed;
	}

	/** The one-commit LLM suggestion request - blocks on the HTTP call, so never on the FX thread. */
	public Suggested requestSuggestion(GitCommitInfo commit) {
		SuggestionInput input = this.suggestionInput(commit);
		String model = LlmPreferences.getModelName();
		LlmFeatureSuggestionClient client = new LlmFeatureSuggestionClient(LlmPreferences.getEndpointUrl(), model);
		LlmFeatureSuggestionClient.SuggestionBatch batch = client.suggestConfigurations(List.of(input.commit()), input.knownFeatures(), null);
		SuggestionConfidence confidence = batch.failureReason() != null ? null : SuggestionConfidence.of(batch.configurations().get(0),
				input.knownFeatures().stream().map(LlmFeatureSuggestionClient.KnownFeature::name).toList(), input.commit().overlap());
		return new Suggested(batch, model, diffLines(input.commit().diff()), confidence);
	}

	/** The input of {@link #requestSuggestion} - not on the FX thread. */
	public SuggestionInput suggestionInput(GitCommitInfo commit) {
		String diff = this.gitHistoryReader.getDiff(this.repoDir, commit.getId());
		// where the commit changed things, and where the known features' earlier commits did
		ImportedFeatureTraces traces = this.importedTraces();
		Map<String, GitHistoryReader.ChangedFile> changes = this.gitHistoryReader.getChangedPositions(this.repoDir, commit.getId(), this.service.getReader());
		Map<String, Set<String>> changedPositions = new LinkedHashMap<>();
		changes.forEach((path, file) -> changedPositions.put(path, file.positions()));
		LlmFeatureSuggestionClient.CommitForSuggestion commitForSuggestion =
				new LlmFeatureSuggestionClient.CommitForSuggestion(commit.getShortId(), commit.getMessage(), diff, changes, traces.overlap(changedPositions));

		// the feature hierarchy inferred so far from the commits actually imported in this run
		// (see FeatureModelTree, also what drives the Feature Model tab's tree) - not just a flat
		// list of names, so the model can tell a genuinely new, narrower capability built on an
		// existing feature from a change to that feature itself - with the files and positions each
		// one's imported commits changed
		Map<String, Set<String>> featureFiles = traces.files();
		Map<String, Set<String>> featurePositions = traces.positions();
		// the parent the LLM named when a feature was new, where there is one: in an import every
		// configuration has all features so far, and the tree made each the child of the one before
		Map<String, String> llmParents = traces.llmParents();
		List<LlmFeatureSuggestionClient.KnownFeature> knownFeatures = FeatureModelTree.compute(this.service.getRepository()).stream()
				.map(placement -> new LlmFeatureSuggestionClient.KnownFeature(
						placement.feature.getName(),
						llmParents.getOrDefault(placement.feature.getName(), placement.parent == null ? null : placement.parent.getName()),
						featureFiles.getOrDefault(placement.feature.getName(), Set.of()),
						featurePositions.getOrDefault(placement.feature.getName(), Set.<String>of()).stream()
								.map(position -> position.substring(position.indexOf('#') + 1))
								.collect(Collectors.toCollection(LinkedHashSet::new))))
				.collect(Collectors.toList());
		// in an import of tagged commits only, those named since the last one imported, not in the
		// repository yet: else the next commit named them anew, by another name
		Set<String> listed = knownFeatures.stream().map(LlmFeatureSuggestionClient.KnownFeature::name).collect(Collectors.toSet());
		for (String name : new ArrayList<>(this.runningFeatures))
			if (listed.add(name))
				knownFeatures.add(new LlmFeatureSuggestionClient.KnownFeature(name, llmParents.get(name),
						featureFiles.getOrDefault(name, Set.of()),
						featurePositions.getOrDefault(name, Set.<String>of()).stream()
								.map(position -> position.substring(position.indexOf('#') + 1))
								.collect(Collectors.toCollection(LinkedHashSet::new))));
		return new SuggestionInput(commitForSuggestion, knownFeatures);
	}

	/** Where each feature's imported commits changed things, read from the log the first time - not on the FX thread. */
	private ImportedFeatureTraces importedTraces() {
		ImportedFeatureTraces traces = this.importedTraces;
		if (traces == null) {
			traces = ImportedFeatureTraces.fromLog(FeatureSuggestionLog.location(), String.valueOf(this.service.getRepositoryDir()), this.repoDir,
					this.gitHistoryReader, this.service.getReader());
			this.importedTraces = traces;
		}
		return traces;
	}

	/**
	 * The log entry for a commit before its outcome is known: with its suggestion, or with
	 * {@code suggested} null if the request failed ({@code failure} says why) or none was asked
	 * for (both null).
	 */
	private FeatureSuggestionLog.Entry suggestionEntry(GitCommitInfo commit, Suggested suggested, String failure, String proposed, FeatureSuggestionLog.Outcome outcome) {
		LlmFeatureSuggestionClient.SuggestionBatch batch = suggested == null ? null : suggested.batch();
		String model = suggested != null ? suggested.model() : failure != null ? LlmPreferences.getModelName() : null;
		return new FeatureSuggestionLog.Entry(String.valueOf(this.service.getRepositoryDir()), String.valueOf(this.repoDir), commit.getId(), commit.getMessage(),
				suggested == null ? -1 : suggested.diffLines(), model,
				String.join(", ", this.runningFeatures),
				batch == null || batch.configurations().isEmpty() ? null : batch.configurations().get(0),
				proposed, failure,
				batch == null || batch.usages().isEmpty() ? null : batch.usages().get(0),
				suggested == null ? null : suggested.confidence(), outcome, null, null,
				batch == null ? Map.of() : batch.parentsAt(0));
	}

	/**
	 * Records the pending suggestion, if it is {@code commit}'s, as committed with {@code configuration}
	 * and what it changed - or skipped if the configuration is null.
	 */
	private void recordSuggestionOutcome(GitCommitInfo commit, String configuration, ImportedFeatureTraces.Changes changes) {
		FeatureSuggestionLog.Entry entry = this.pendingSuggestion;
		if (entry == null || !entry.commitId().equals(commit.getId()))
			return;
		this.pendingSuggestion = null;
		FeatureSuggestionLog.append(configuration == null ? entry.skipped() : entry.committedAs(configuration).withChanges(changes));
	}

	/**
	 * Commits Git commit [index] into the repository with [configurationText]: its tree extracted to
	 * a fresh temporary directory - never the clone's working directory, which may be in use - and
	 * committed from there; the constraints it violates and what it changed are logged. Blocks - not
	 * on the FX thread. Followed by {@link #imported} once it succeeded; on failure the commit rolled
	 * back, so it can be corrected and tried again.
	 */
	public Imported commit(int index, String configurationText) throws IOException {
		GitCommitInfo commit = this.commits.get(index);
		String before = String.join(", ", this.runningFeatures);
		FeatureSuggestionLog.Entry suggestion = this.pendingSuggestion;
		String suggested = suggestion != null && suggestion.commitId().equals(commit.getId()) ? suggestion.suggestion() : null;
		Set<String> labels = ImportedFeatureTraces.labels(before, suggested, configurationText);
		Set<String> relationLabels = ImportedFeatureTraces.relationLabels(before, suggested, configurationText);
		Map<String, String> parents = suggestion != null && suggestion.commitId().equals(commit.getId()) ? suggestion.parents() : Map.of();

		Path tempDir = Files.createTempDirectory("ecco-git-import");
		try {
			this.gitHistoryReader.extractCommitTree(this.repoDir, commit.getId(), tempDir);
			// checked before committing: only exception-free rules count, and the committed
			// configuration is the exception to every rule it violates - afterwards it violates none
			List<String> violations = this.constraintViolations(
					(configurationText != null && !configurationText.isEmpty())
							? configurationText : this.service.getConfigStringFromFile(tempDir));
			long startMillis = System.currentTimeMillis();
			// the temporary directory, not the working one: that was set to it, and never back
			Commit result = this.service.commitDirectory(tempDir, commit.getMessage(), configurationText);
			double durationSeconds = (System.currentTimeMillis() - startMillis) / 1000.0;
			ImportedFeatureTraces.Changes changes = null;
			try {
				changes = ImportedFeatureTraces.Changes.of(
						this.gitHistoryReader.getChangedPositions(this.repoDir, commit.getId(), this.service.getReader()));
				// if they are not read yet, this commit is logged before they are, and read from there
				ImportedFeatureTraces traces = this.importedTraces;
				if (traces != null) {
					traces.record(String.valueOf(this.repoDir), labels, relationLabels, changes);
					traces.recordParents(ImportedFeatureTraces.keptParents(parents, configurationText));
				}
			} catch (RuntimeException e) {
				// the commit is in; only its trace is lost
				LOGGER.log(Level.WARNING, "Could not locate the changes of " + commit.getShortId(), e);
			}
			String configLabel = (configurationText == null || configurationText.isEmpty()) ? "(no configuration)" : configurationText;
			this.log.accept(String.format("Imported %s (%s) -> [%s] in %.2f seconds.%n", commit.getShortId(), commit.getMessage(), configLabel, durationSeconds));

			if (!violations.isEmpty()) {
				for (String violation : violations)
					this.allConstraintWarnings.add(commit.getShortId() + ": " + violation);
				StringBuilder lines = new StringBuilder();
				for (String violation : violations)
					lines.append("CONSTRAINT: ").append(commit.getShortId()).append(": ").append(violation).append(System.lineSeparator());
				this.log.accept(lines.toString());
			}
			return new Imported(result, changes);
		} finally {
			deleteRecursively(tempDir);
		}
	}

	/** Git commit [index] was committed with [configurationText] ({@link #commit}): the run goes on from it. */
	public void imported(int index, String configurationText, Imported imported) {
		this.lastCommit = imported.commit();
		this.importedCount++;
		this.repositoryEmpty = false;
		this.revisedSinceImport.clear();
		this.foldedEntries.forEach(FeatureSuggestionLog::append);
		this.foldedEntries.clear();
		this.recordSuggestionOutcome(this.commits.get(index), configurationText, imported.changes());
		applyConfigurationToRunning(this.runningFeatures, configurationText);
		this.knownFeatures.addAll(this.runningFeatures);
	}

	/**
	 * A commit between tagged ones, in an import of tagged commits only, is not committed, but its
	 * [configurationText] is used: what it changed counts for the features it names as an imported
	 * commit's does (recorded here; null if it could not be found - its features still go into the
	 * next tagged commit). Blocks - not on the FX thread. Followed by {@link #folded}.
	 */
	public ImportedFeatureTraces.Changes foldChanges(int index, String configurationText) {
		GitCommitInfo commit = this.commits.get(index);
		String before = String.join(", ", this.runningFeatures);
		FeatureSuggestionLog.Entry suggestion = this.pendingSuggestion;
		String suggested = suggestion != null && suggestion.commitId().equals(commit.getId()) ? suggestion.suggestion() : null;
		Set<String> labels = ImportedFeatureTraces.labels(before, suggested, configurationText);
		Set<String> relationLabels = ImportedFeatureTraces.relationLabels(before, suggested, configurationText);
		Map<String, String> parents = suggestion != null && suggestion.commitId().equals(commit.getId()) ? suggestion.parents() : Map.of();
		try {
			ImportedFeatureTraces.Changes changes = ImportedFeatureTraces.Changes.of(
					this.gitHistoryReader.getChangedPositions(this.repoDir, commit.getId(), this.service.getReader()));
			ImportedFeatureTraces traces = this.importedTraces;
			if (traces != null) {
				traces.record(String.valueOf(this.repoDir), labels, relationLabels, changes);
				traces.recordParents(ImportedFeatureTraces.keptParents(parents, configurationText));
			}
			return changes;
		} catch (RuntimeException e) {
			// its features still go into the next tagged commit; only its trace is lost
			LOGGER.log(Level.WARNING, "Could not locate the changes of " + commit.getShortId(), e);
			return null;
		}
	}

	/**
	 * Commit [index] is kept for the next tagged commit with [configurationText] ({@link #foldChanges}
	 * found [changes], null if not): logged with it (see {@link #foldedEntries}), its revised features
	 * revised there too.
	 */
	public void folded(int index, String configurationText, ImportedFeatureTraces.Changes changes) {
		GitCommitInfo commit = this.commits.get(index);
		FeatureSuggestionLog.Entry entry = this.pendingSuggestion;
		if (entry != null && entry.commitId().equals(commit.getId())) {
			this.pendingSuggestion = null;
			this.foldedEntries.add(entry.folded().committedAs(configurationText).withChanges(changes));
		}
		for (String token : markedTokens(configurationText))
			if (token.endsWith("'"))
				this.revisedSinceImport.add(plainName(token));
		applyConfigurationToRunning(this.runningFeatures, configurationText);
		this.foldedCount++;
		this.log.accept(String.format("Suggested %s (%s) -> [%s], for the next tagged commit.%n", commit.getShortId(), commit.getMessage(),
				configurationText == null || configurationText.isEmpty() ? "(no configuration)" : configurationText));
	}

	/** The files [commit] changed, for its review. Blocks - not on the FX thread. */
	public List<String> changedFiles(GitCommitInfo commit) {
		return this.gitHistoryReader.getChangedFiles(this.repoDir, commit.getId());
	}

	/** The summary of the run: what was imported, skipped and folded, the constraints violated and the suggestions failed. */
	public String summary(boolean stoppedEarly) {
		return summary(this.importedCount, this.toImport(), this.skippedCount, stoppedEarly, this.allConstraintWarnings, this.llmSuggestionFailures)
				+ (this.foldedCount == 0 ? "" : System.lineSeparator() + this.foldedCount + " commit(s) between the tagged ones were not imported: their features went into the next tagged one.");
	}

	/**
	 * The accepted constraints the configuration about to be committed violates. None if it cannot
	 * be parsed - the commit reports that; a failing check is reported as a violation instead of
	 * thrown, since constraints are advisory and must not stop the import.
	 */
	private List<String> constraintViolations(String configurationString) {
		Configuration configuration;
		try {
			configuration = this.service.parseConfigurationString(configurationString);
		} catch (RuntimeException e) {
			return List.of();
		}
		try {
			return this.service.checkConstraintViolations(configuration);
		} catch (RuntimeException e) {
			return List.of("constraint check failed: " + e.getMessage());
		}
	}

	public GitCommitInfo commit(int index) {
		return this.commits.get(index);
	}

	public int size() {
		return this.commits.size();
	}

	public boolean useLlm() {
		return this.useLlm;
	}

	public boolean reviewEach() {
		return this.reviewEach;
	}

	public boolean taggedOnly() {
		return this.taggedOnly;
	}

	/** The tags naming [commit], none if none does. */
	public List<String> tagsOf(GitCommitInfo commit) {
		return this.tags.getOrDefault(commit.getId(), List.of());
	}

	/** In an import of tagged commits only: the features a commit since the last one imported revised. */
	public Set<String> revisedSinceImport() {
		return Collections.unmodifiableSet(this.revisedSinceImport);
	}

	public boolean repositoryEmpty() {
		return this.repositoryEmpty;
	}

	/** The confidence of [commit]'s pending suggestion, null if it has none. */
	public SuggestionConfidence pendingConfidence(GitCommitInfo commit) {
		FeatureSuggestionLog.Entry pending = this.pendingSuggestion;
		return pending != null && pending.commitId().equals(commit.getId()) ? pending.confidence() : null;
	}

	public int importedCount() {
		return this.importedCount;
	}

	/** The last commit imported, null if none was. */
	public Commit lastCommit() {
		return this.lastCommit;
	}

	/** The repository the run imports into. */
	public Path runRepositoryDir() {
		return this.runRepositoryDir;
	}

	/**
	 * "Imported 3 of 4 commits, skipped 1." and, where there are any, the constraint violations and
	 * the failed suggestions, each with its commit.
	 */
	public static String summary(int imported, int total, int skipped, boolean stoppedEarly, List<String> violations, List<String> suggestionFailures) {
		StringBuilder text = new StringBuilder("Imported " + imported + " of " + total + " commits"
				+ (skipped > 0 ? ", skipped " + skipped : "") + "." + (stoppedEarly ? " Stopped before the end." : ""));
		if (!violations.isEmpty())
			text.append("\n\nAccepted constraints violated (").append(violations.size()).append("):\n").append(String.join("\n", violations));
		if (!suggestionFailures.isEmpty())
			text.append("\n\nLLM suggestions that failed, each stopped for review (").append(suggestionFailures.size()).append("):\n")
					.append(String.join("\n", suggestionFailures));
		return text.toString();
	}

	/** The messages of {@code exception} and its causes, without repeats, e.g. "Error during commit. - caused by: ...". */
	public static String describeFailure(Throwable exception) {
		List<String> messages = new ArrayList<>();
		for (Throwable t = exception; t != null; t = t.getCause()) {
			String message = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
			if (!messages.contains(message))
				messages.add(message);
		}
		return String.join(" - caused by: ", messages);
	}

	/** Lines of a diff before {@link GitHistoryReader#getDiff} truncated it. */
	public static int diffLines(String diff) {
		String[] lines = diff.split("\n", -1);
		Matcher truncated = Pattern.compile("^\\.\\.\\. \\(diff truncated, (\\d+) more lines\\)$").matcher(lines[lines.length - 1]);
		return truncated.matches() ? lines.length - 1 + Integer.parseInt(truncated.group(1)) : lines.length;
	}

	/** The index of the last of [commits] with a tag in [tags], -1 if none has. */
	public static int lastTagged(List<GitCommitInfo> commits, Map<String, List<String>> tags) {
		for (int i = commits.size() - 1; i >= 0; i--)
			if (tags.containsKey(commits.get(i).getId()))
				return i;
		return -1;
	}

	/**
	 * [configuration] of a tagged commit, its features marked with a new revision ("A'") if it or a
	 * commit since the last import ([revised]) changed them - only those [imported] already: one new
	 * since is new in this commit anyway - nor the generic ones ({@link LlmFeatureSuggestionClient#GENERIC}).
	 */
	public static String releaseConfigurationText(String configuration, Set<String> revised, Set<String> imported) {
		List<String> text = new ArrayList<>();
		for (String token : markedTokens(configuration)) {
			String name = plainName(token);
			boolean newRevision = imported.contains(name) && !LlmFeatureSuggestionClient.GENERIC.contains(name)
					&& (token.endsWith("'") || revised.contains(name));
			text.add(name.equals(token) || token.endsWith("'") ? (newRevision ? name + "'" : name) : token);
		}
		return String.join(", ", text);
	}

	/**
	 * Parses a comma-separated configuration string into its feature-name tokens, trimmed and with
	 * blanks dropped, preserving first-seen order.
	 */
	public static LinkedHashSet<String> parseFeatureTokens(String configurationString) {
		return ConfigurationText.tokens(configurationString);
	}

	/**
	 * The default configuration text to pre-fill a commit's review field with: whatever has
	 * actually been imported so far ({@code runningFeatures}), plus/minus whatever {@code suggestion}
	 * says - accumulated forward rather than replaced, since ECCO's Configuration represents the full
	 * variant at that point, not a delta, so "no new feature detected" should never look like "no
	 * features at all". A plain token in {@code suggestion} is an addition; a "-name" token (see
	 * {@link LlmFeatureSuggestionClient}'s SYSTEM_PROMPT) is the LLM judging that KNOWN feature's own
	 * implementation to be clearly, unambiguously removed by this commit's diff, and drops it from
	 * the accumulated set - the one place a real removal has ANY chance of happening automatically,
	 * since without it every feature ever introduced stayed in every later commit's configuration
	 * forever (confirmed directly against a real 62-commit import: 0 of 62 configurations ever
	 * dropped a feature, which is what produced a near-complete, pathologically slow constraint
	 * graph downstream - see FeaturesView's force-directed-freeze fix for the symptom this caused).
	 * Removal is necessarily best-effort (an LLM can only catch a diff that visibly deletes a
	 * feature's own code, not slower obsolescence) - a human can still notice and edit anything this
	 * misses by hand on the review screen, same as before. Removing a feature name not currently in
	 * {@code runningFeatures} is a safe no-op, never an error.
	 */
	public static String defaultConfigurationText(LinkedHashSet<String> runningFeatures, String suggestion) {
		return defaultConfigurationText(runningFeatures, Set.of(), suggestion);
	}

	/**
	 * As above; a feature [suggestion] names that is known already - running, or one of the
	 * repository's [knownFeatures] - gets a new revision ("A'"): the commit changes it. With one
	 * revision per feature, and nearly the same features on every commit, code that came or changed
	 * later could be traced to no feature: tinycc's import 95% of its associations, zip4j's first
	 * 275 commits 28% - 1.3% with a new revision for each feature the suggestions named. The generic
	 * features ({@link LlmFeatureSuggestionClient#GENERIC}) keep one revision.
	 */
	public static String defaultConfigurationText(LinkedHashSet<String> runningFeatures, Set<String> knownFeatures, String suggestion) {
		LinkedHashSet<String> combined = new LinkedHashSet<>(runningFeatures);
		Set<String> revised = new HashSet<>();
		if (suggestion != null) {
			for (String token : suggestion.split(",")) {
				String trimmed = token.trim();
				if (trimmed.isEmpty()) {
					continue;
				}
				if (trimmed.startsWith("-") && trimmed.length() > 1) {
					combined.remove(trimmed.substring(1).trim());
				} else {
					String name = plainName(trimmed);
					if ((runningFeatures.contains(name) || knownFeatures.contains(name)) && !LlmFeatureSuggestionClient.GENERIC.contains(name))
						revised.add(name);
					combined.add(name);
				}
			}
		}
		List<String> text = new ArrayList<>();
		for (String name : combined)
			text.add(revised.contains(name) ? name + "'" : name);
		return String.join(", ", text);
	}

	/** The features of [configuration] as written, with their new revision marks "'" - {@link #parseFeatureTokens} drops them. */
	private static List<String> markedTokens(String configuration) {
		List<String> tokens = new ArrayList<>();
		if (configuration != null)
			for (String token : configuration.split(","))
				if (!token.isBlank())
					tokens.add(token.trim());
		return tokens;
	}

	/** A feature's name without the new revision mark "'". */
	private static String plainName(String token) {
		return token.endsWith("'") ? token.substring(0, token.length() - 1).trim() : token;
	}

	/**
	 * Updates {@code runningFeatures} to reflect a commit that was actually imported with
	 * {@code configurationString} (the user's final, possibly hand-edited text) - for an imported
	 * commit only, never a skipped one, so later commits' defaults build on what's actually in the
	 * repository rather than what was merely suggested.
	 */
	public static void applyConfigurationToRunning(LinkedHashSet<String> runningFeatures, String configurationString) {
		runningFeatures.clear();
		for (String token : parseFeatureTokens(configurationString))
			runningFeatures.add(plainName(token));
	}

	private static void deleteRecursively(Path directory) throws IOException {
		Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				Files.delete(file);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
				Files.delete(dir);
				return FileVisitResult.CONTINUE;
			}
		});
	}
}
