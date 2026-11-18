package at.jku.isse.ecco.service.gitimport;

import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.service.git.GitCommitInfo;
import at.jku.isse.ecco.service.git.GitHistoryReader;
import at.jku.isse.ecco.service.llm.FeatureSuggestionLog;
import at.jku.isse.ecco.service.llm.ImportedFeatureTraces;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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

	/**
	 * A commit whose configuration was imported before stops for review (FR-M4d): imported with it
	 * again, the lines it changes checked out for it no more - an import without suggestions proposed
	 * BASE for every commit, and BASE checked out only the lines all of them share. The person names
	 * the features; with a new revision (A'), the newest configuration checks out the newest code.
	 */
	@Test
	@Timeout(60)
	public void aConfigurationImportedBeforeStopsForReview(@TempDir Path tmp) throws Exception {
		Path clone = Files.createDirectories(tmp.resolve("clone"));
		try (Git git = Git.init().setDirectory(clone.toFile()).call()) {
			for (String version : List.of("v1", "v2", "v3")) {
				Files.writeString(clone.resolve("a.txt"), "head\n" + version + "\ntail\n");
				git.add().addFilepattern(".").call();
				git.commit().setMessage(version).setAuthor("t", "t@example.com").setCommitter("t", "t@example.com").call();
			}
		}
		try (EccoService service = repository(tmp.resolve("repo"))) {
			List<String> log = new ArrayList<>();
			GitImport run = new GitImport(service, new GitHistoryReader(), clone, Map.of(), false, oldestFirst(clone), false, false, true, log::add);
			run.start();

			GitImport.Step step = importAll(run, run.next(0), commit -> null);
			GitImport.Review second = assertInstanceOf(GitImport.Review.class, run.unattendedWithoutSuggestion(assertInstanceOf(GitImport.Unattended.class, step).index()));
			assertEquals("BASE", second.configuration());
			assertTrue(second.repeated() != null && second.repeated().contains("imported before"), String.valueOf(second.repeated()));
			assertTrue(log.stream().anyMatch(line -> line.startsWith("*** CONFIGURATION IMPORTED BEFORE")), String.join("", log));
			assertNull(run.repeats(second.index(), "BASE, V2"), "a new feature");
			assertNull(run.repeats(second.index(), "BASE'"), "a new revision");
			run.imported(second.index(), "BASE, V2", run.commit(second.index(), "BASE, V2"));

			// V2 alone changed a.txt since BASE: its new revision, unattended
			GitImport.Import third = assertInstanceOf(GitImport.Import.class, run.unattendedWithoutSuggestion(assertInstanceOf(GitImport.Unattended.class, run.next(second.index() + 1)).index()));
			assertEquals("BASE, V2'", third.configuration());
			assertTrue(log.stream().anyMatch(line -> line.startsWith("*** REVISED BY OVERLAP") && line.contains("V2'")), String.join("", log));
			run.imported(third.index(), third.configuration(), run.commit(third.index(), third.configuration()));
			// its changes count for V2 in the log, read again by the next run
			assertTrue(Files.readString(Path.of(System.getProperty(FeatureSuggestionLog.LOCATION_PROPERTY))).contains("\"owner\":\"V2\""));
			assertInstanceOf(GitImport.Finished.class, run.next(third.index() + 1));
			assertTrue(run.summary(false).contains("Configurations imported before, each stopped for review (1)"), run.summary(false));
			assertTrue(run.summary(false).contains("given a new revision of the feature whose places their changes lie in (1)"), run.summary(false));

			Path out = Files.createDirectories(tmp.resolve("out"));
			service.setBaseDir(out);
			service.checkout("BASE, V2");
			assertEquals("head\nv3\ntail\n", Files.readString(out.resolve("a.txt")));
		}
	}

	/**
	 * A commit whose configuration was imported before stops for review when its changes lie where
	 * two features' did - neither owns them - and when they lie where no feature but BASE's did.
	 */
	@Test
	public void aConfigurationImportedBeforeWithoutOneOwnerStopsForReview(@TempDir Path tmp) throws Exception {
		Path clone = Files.createDirectories(tmp.resolve("clone"));
		try (Git git = Git.init().setDirectory(clone.toFile()).call()) {
			for (String version : List.of("v1", "v2", "v3", "v4")) {
				Files.writeString(clone.resolve("a.txt"), "head\n" + version + "\ntail\n");
				if (version.equals("v4"))
					Files.writeString(clone.resolve("new.txt"), "new\n");
				git.add().addFilepattern(".").call();
				git.commit().setMessage(version).setAuthor("t", "t@example.com").setCommitter("t", "t@example.com").call();
			}
		}
		try (EccoService service = repository(tmp.resolve("repo"))) {
			List<String> log = new ArrayList<>();
			GitImport run = new GitImport(service, new GitHistoryReader(), clone, Map.of(), false, oldestFirst(clone), false, false, true, log::add);
			run.start();
			GitImport.Step step = importAll(run, run.next(0), commit -> null);
			// BASE only: no feature but BASE changed a.txt
			GitImport.Review second = assertInstanceOf(GitImport.Review.class, run.unattendedWithoutSuggestion(assertInstanceOf(GitImport.Unattended.class, step).index()));
			assertNotNull(second.repeated());
			run.imported(second.index(), "BASE, A", run.commit(second.index(), "BASE, A"));
			// A alone changed a.txt: its new revision - here B, new, is committed instead, as by a person
			GitImport.Import third = assertInstanceOf(GitImport.Import.class, run.unattendedWithoutSuggestion(assertInstanceOf(GitImport.Unattended.class, run.next(second.index() + 1)).index()));
			assertEquals("BASE, A'", third.configuration());
			log.clear();
			run.imported(third.index(), "BASE, A, B", run.commit(third.index(), "BASE, A, B"));
			// A and B both changed a.txt
			GitImport.Review fourth = assertInstanceOf(GitImport.Review.class, run.unattendedWithoutSuggestion(assertInstanceOf(GitImport.Unattended.class, run.next(third.index() + 1)).index()));
			assertEquals("BASE, A, B", fourth.configuration());
			assertNotNull(fourth.repeated());
			assertTrue(log.stream().noneMatch(line -> line.startsWith("*** REVISED BY OVERLAP")), String.join("", log));
		}
	}

	@Test
	public void aRevisedFeatureCountsForTheChangesOfItsCommit() {
		assertEquals(Set.of("A"), ImportedFeatureTraces.labels("BASE, A", null, "BASE, A'", "A"));
		assertEquals(Set.of(), ImportedFeatureTraces.labels("BASE, A", null, "BASE, A'"), "a tagged commit's mark: its folded commits changed A");
		assertEquals(Set.of(), ImportedFeatureTraces.labels("BASE, A", null, "BASE", "A"), "not kept");
	}

	@Test
	public void theOwnerIsTheOnlyFeatureAtTheStrongestOverlap() {
		ImportedFeatureTraces.Overlap a = new ImportedFeatureTraces.Overlap("A", List.of("f#m"), List.of(), 1);
		ImportedFeatureTraces.Overlap b = new ImportedFeatureTraces.Overlap("B", List.of(), List.of("f#C"), 1);
		ImportedFeatureTraces.Overlap c = new ImportedFeatureTraces.Overlap("C", List.of("f#n"), List.of(), 1);
		assertEquals("A", ImportedFeatureTraces.owner(List.of(a, b), Set.of("A", "B")).feature());
		assertNull(ImportedFeatureTraces.owner(List.of(a, c, b), Set.of("A", "B", "C")), "two at the same positions");
		assertEquals("B", ImportedFeatureTraces.owner(List.of(a, c, b), Set.of("B")).feature(), "only those of the configuration");
		assertNull(ImportedFeatureTraces.owner(List.of(), Set.of("A")));
		assertEquals("BASE, A', B", GitImport.revise("BASE, A, B", "A"));
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
