package at.jku.isse.ecco.service.gitimport;

import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.service.git.GitCommitInfo;
import at.jku.isse.ecco.service.git.GitHistoryReader;
import at.jku.isse.ecco.service.llm.FeatureSuggestionLog;
import at.jku.isse.ecco.service.llm.LlmFeatureSuggestionClient;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A Git import run without any user interface: driven step by step as the Import from Git dialog
 * drives it ({@link GitImport#next} and what each step says), with the LLM's suggestion replaced.
 */
public class GitImportTest {

	private String logLocation;

	@BeforeEach
	public void ownSuggestionLog(@TempDir Path tmp) {
		this.logLocation = System.getProperty(FeatureSuggestionLog.LOCATION_PROPERTY);
		System.setProperty(FeatureSuggestionLog.LOCATION_PROPERTY, tmp.resolve("suggestions.jsonl").toString());
	}

	@AfterEach
	public void restoreSuggestionLog() {
		if (this.logLocation == null)
			System.clearProperty(FeatureSuggestionLog.LOCATION_PROPERTY);
		else
			System.setProperty(FeatureSuggestionLog.LOCATION_PROPERTY, this.logLocation);
	}

	/**
	 * Unattended, every commit is imported with its suggestion: the first, into an empty repository,
	 * as BASE without asking; each later one with the features so far and the one suggested.
	 */
	@Test
	@Timeout(60)
	public void anUnattendedRunImportsEveryCommitWithItsSuggestion(@TempDir Path tmp) throws Exception {
		Path clone = gitHistory(tmp.resolve("clone"), "first", "second", "third");
		try (EccoService service = repository(tmp.resolve("repo"))) {
			List<String> log = new ArrayList<>();
			List<String> asked = new ArrayList<>();
			GitImport run = new GitImport(service, new GitHistoryReader(), clone, Map.of(), false, oldestFirst(clone), true, false, true, log::add);
			run.start();

			drive(run, commit -> {
				asked.add(commit.getMessage());
				return suggesting(commit.getMessage().toUpperCase());
			});

			assertEquals(List.of("second", "third"), asked, "not asked for the first commit");
			assertEquals(List.of(Set.of("BASE"), Set.of("BASE", "SECOND"), Set.of("BASE", "SECOND", "THIRD")), committedFeatures(service));
			assertEquals(3, run.importedCount());
			assertTrue(run.summary(false).startsWith("Imported 3 of 3 commits."), run.summary(false));
			assertEquals(3, log.stream().filter(line -> line.startsWith("Imported ")).count(), String.join("", log));
		}
	}

	/** A failed suggestion stops an unattended run for review, says so in the log and the summary; the commit can be skipped. */
	@Test
	@Timeout(60)
	public void aFailedSuggestionStopsForReview(@TempDir Path tmp) throws Exception {
		Path clone = gitHistory(tmp.resolve("clone"), "first", "second", "third");
		try (EccoService service = repository(tmp.resolve("repo"))) {
			List<String> log = new ArrayList<>();
			GitImport run = new GitImport(service, new GitHistoryReader(), clone, Map.of(), false, oldestFirst(clone), true, false, true, log::add);
			run.start();

			GitImport.Step step = importAll(run, run.next(0), commit -> suggesting(commit.getMessage().toUpperCase()));
			GitImport.Unattended second = assertInstanceOf(GitImport.Unattended.class, step);
			GitImport.Suggested failed = new GitImport.Suggested(new LlmFeatureSuggestionClient.SuggestionBatch(List.of(""), "no model", List.of(), List.of()), "stub", 1);
			GitImport.Review review = assertInstanceOf(GitImport.Review.class, run.afterUnattendedSuggestion(second.index(), failed));
			assertEquals("no model", review.suggestionFailure());
			assertEquals("BASE", review.configuration(), "the features so far, nothing suggested");
			assertTrue(log.stream().anyMatch(line -> line.startsWith("*** LLM SUGGESTION FAILED")), String.join("", log));

			run.skip(review.index());
			drive(run, run.next(review.index() + 1), commit -> suggesting(commit.getMessage().toUpperCase()));

			assertEquals(List.of(Set.of("BASE"), Set.of("BASE", "THIRD")), committedFeatures(service));
			String summary = run.summary(false);
			assertTrue(summary.startsWith("Imported 2 of 3 commits, skipped 1."), summary);
			assertTrue(summary.contains("no model"), summary);
		}
	}

	/** Runs [run] from its first step to the end, suggesting with [suggester]; a review fails the test. */
	private static void drive(GitImport run, Function<GitCommitInfo, GitImport.Suggested> suggester) throws Exception {
		drive(run, run.next(0), suggester);
	}

	private static void drive(GitImport run, GitImport.Step step, Function<GitCommitInfo, GitImport.Suggested> suggester) throws Exception {
		while (!(step instanceof GitImport.Finished)) {
			step = importAll(run, step, suggester);
			if (step instanceof GitImport.Unattended unattended)
				step = run.afterUnattendedSuggestion(unattended.index(), suggester.apply(run.commit(unattended.index())));
			else if (step instanceof GitImport.Review review)
				throw new AssertionError("stopped for review at " + run.commit(review.index()).getMessage());
		}
	}

	/** Imports while [step] says to, returning the first step that does not. */
	private static GitImport.Step importAll(GitImport run, GitImport.Step step, Function<GitCommitInfo, GitImport.Suggested> suggester) throws Exception {
		while (step instanceof GitImport.Import anImport) {
			GitImport.Imported imported = run.commit(anImport.index(), anImport.configuration());
			run.imported(anImport.index(), anImport.configuration(), imported);
			step = run.next(anImport.index() + 1);
		}
		return step;
	}

	/** The LLM's reply naming [feature], as a new feature under BASE. */
	private static GitImport.Suggested suggesting(String feature) {
		return new GitImport.Suggested(new LlmFeatureSuggestionClient.SuggestionBatch(List.of(feature), null, List.of(), List.of(Map.of(feature, "BASE"))), "stub", 1);
	}

	/** The feature names of each commit's configuration, in the order of the clone's commits (first, second, third). */
	private static List<Set<String>> committedFeatures(EccoService service) {
		List<String> order = List.of("first", "second", "third");
		List<Commit> commits = new ArrayList<>(service.getCommits());
		commits.sort(Comparator.comparing(commit -> order.indexOf(commit.getCommitMessage())));
		return commits.stream().map(commit -> java.util.Arrays.stream(commit.getConfiguration().getFeatureRevisions())
				.map(FeatureRevision::getFeature).map(feature -> feature.getName())
				.collect(Collectors.toCollection(TreeSet::new))).map(set -> (Set<String>) set).toList();
	}

	private static List<GitCommitInfo> oldestFirst(Path clone) {
		List<GitCommitInfo> commits = new ArrayList<>(new GitHistoryReader().listCommits(clone));
		Collections.reverse(commits);
		return commits;
	}

	private static Path gitHistory(Path dir, String... messages) throws Exception {
		Files.createDirectories(dir);
		try (Git git = Git.init().setDirectory(dir.toFile()).call()) {
			for (String message : messages) {
				Files.writeString(dir.resolve(message + ".txt"), message + "\n");
				git.add().addFilepattern(".").call();
				git.commit().setMessage(message).setAuthor("t", "t@example.com").setCommitter("t", "t@example.com").call();
			}
		}
		return dir;
	}

	private static EccoService repository(Path dir) throws Exception {
		Files.createDirectories(dir);
		EccoService service = new EccoService();
		service.setRepositoryDir(dir.resolve(".ecco"));
		service.init();
		return service;
	}
}
