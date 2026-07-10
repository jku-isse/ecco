package at.jku.isse.ecco.service.git;

import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link GitHistoryReader} needs no external fixture - JGit can create a throwaway repository
 * with real commits in a temp directory just as easily as it can read one, so these tests build
 * their own small history rather than depending on a checked-in `.git` directory.
 */
public class GitHistoryReaderTest {

	private final GitHistoryReader reader = new GitHistoryReader();

	@Test
	@Timeout(30)
	public void listCommits_returnsNewestFirstWithCorrectMessages() throws Exception {
		Path repoDir = Files.createTempDirectory("git-history-reader-test");

		try (Git git = Git.init().setDirectory(repoDir.toFile()).call()) {
			Files.writeString(repoDir.resolve("file.txt"), "first version\n");
			git.add().addFilepattern(".").call();
			git.commit().setMessage("first commit").setAuthor("Test", "test@example.com").call();

			Files.writeString(repoDir.resolve("file.txt"), "second version\n");
			git.add().addFilepattern(".").call();
			git.commit().setMessage("second commit").setAuthor("Test", "test@example.com").call();
		}

		List<GitCommitInfo> commits = reader.listCommits(repoDir);

		assertEquals(2, commits.size());
		// newest first
		assertEquals("second commit", commits.get(0).getMessage());
		assertEquals("first commit", commits.get(1).getMessage());
		assertEquals(7, commits.get(0).getShortId().length());
		assertTrue(commits.get(0).getId().startsWith(commits.get(0).getShortId()));
	}

	@Test
	@Timeout(30)
	public void extractCommitTree_reproducesExactContentAtThatCommit_notLaterChanges() throws Exception {
		Path repoDir = Files.createTempDirectory("git-history-reader-test");

		try (Git git = Git.init().setDirectory(repoDir.toFile()).call()) {
			Files.writeString(repoDir.resolve("a.txt"), "a v1\n");
			git.add().addFilepattern(".").call();
			git.commit().setMessage("commit 1").setAuthor("Test", "test@example.com").call();

			// second commit adds a new file AND changes the first - extractCommitTree for commit
			// 1 must show neither of these
			Files.writeString(repoDir.resolve("a.txt"), "a v2\n");
			Files.writeString(repoDir.resolve("b.txt"), "b v1\n");
			git.add().addFilepattern(".").call();
			git.commit().setMessage("commit 2").setAuthor("Test", "test@example.com").call();
		}

		List<GitCommitInfo> commits = reader.listCommits(repoDir);
		GitCommitInfo firstCommit = commits.get(commits.size() - 1); // oldest, per newest-first ordering

		Path targetDir = Files.createTempDirectory("git-history-reader-extract");
		reader.extractCommitTree(repoDir, firstCommit.getId(), targetDir);

		assertEquals("a v1\n", Files.readString(targetDir.resolve("a.txt")));
		assertFalse(Files.exists(targetDir.resolve("b.txt")), "b.txt was only added in the second commit");
	}

	@Test
	@Timeout(30)
	public void getDiff_forRootCommit_showsAddedContent() throws Exception {
		Path repoDir = Files.createTempDirectory("git-history-reader-test");
		String commitId;

		try (Git git = Git.init().setDirectory(repoDir.toFile()).call()) {
			Files.writeString(repoDir.resolve("a.txt"), "hello world\n");
			git.add().addFilepattern(".").call();
			commitId = git.commit().setMessage("root commit").setAuthor("Test", "test@example.com").call().getId().name();
		}

		String diff = reader.getDiff(repoDir, commitId);

		assertTrue(diff.contains("a.txt"), "diff should mention the added file");
		assertTrue(diff.contains("hello world"), "diff should show the added content");
	}

	@Test
	@Timeout(30)
	public void getDiff_forNonRootCommit_showsOnlyTheChange() throws Exception {
		Path repoDir = Files.createTempDirectory("git-history-reader-test");
		String secondCommitId;

		try (Git git = Git.init().setDirectory(repoDir.toFile()).call()) {
			Files.writeString(repoDir.resolve("a.txt"), "line one\n");
			git.add().addFilepattern(".").call();
			git.commit().setMessage("commit 1").setAuthor("Test", "test@example.com").call();

			Files.writeString(repoDir.resolve("a.txt"), "line one\nline two\n");
			git.add().addFilepattern(".").call();
			secondCommitId = git.commit().setMessage("commit 2").setAuthor("Test", "test@example.com").call().getId().name();
		}

		String diff = reader.getDiff(repoDir, secondCommitId);

		assertTrue(diff.contains("line two"), "diff should show the newly added line");
	}

	@Test
	@Timeout(30)
	public void listCommits_rejectsNonGitDirectory() throws IOException {
		Path notARepo = Files.createTempDirectory("not-a-git-repo");
		org.junit.jupiter.api.Assertions.assertThrows(at.jku.isse.ecco.EccoException.class,
				() -> reader.listCommits(notARepo));
	}
}
