package at.jku.isse.ecco.service.git;

import at.jku.isse.ecco.service.DirectoryFingerprint;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Git import reads the user's own clone (FR-G1a): everything it asks of the clone - the history,
 * the tags, each commit's tree, diff and changed files - leaves the clone as it was, its working
 * tree, its index and the rest of .git, also with uncommitted, staged and untracked changes in it.
 */
public class GitImportLeavesCloneUntouchedTest {

	private final GitHistoryReader reader = new GitHistoryReader();

	@Test
	@Timeout(60)
	public void readingTheHistoryLeavesTheCloneAsItWas(@TempDir Path tmp) throws Exception {
		Path clone = Files.createDirectories(tmp.resolve("clone"));
		try (Git git = Git.init().setDirectory(clone.toFile()).call()) {
			Files.writeString(clone.resolve("a.txt"), "a1\n");
			git.add().addFilepattern(".").call();
			git.commit().setMessage("first").setAuthor("Test", "test@example.com").call();
			git.tag().setName("v1").call();

			Files.writeString(clone.resolve("a.txt"), "a2\n");
			Files.createDirectories(clone.resolve("dir"));
			Files.writeString(clone.resolve("dir/b.txt"), "b1\n");
			git.add().addFilepattern(".").call();
			git.commit().setMessage("second").setAuthor("Test", "test@example.com").call();

			// work in progress: a staged change, an unstaged one and an untracked file
			Files.writeString(clone.resolve("dir/b.txt"), "b staged\n");
			git.add().addFilepattern("dir/b.txt").call();
			Files.writeString(clone.resolve("a.txt"), "a unstaged\n");
			Files.writeString(clone.resolve("untracked.txt"), "untracked\n");
		}
		Map<String, String> before = DirectoryFingerprint.of(clone);

		List<GitCommitInfo> commits = reader.listCommits(clone);
		assertEquals(2, commits.size());
		reader.listTags(clone);
		for (GitCommitInfo commit : commits) {
			Path tree = Files.createDirectories(tmp.resolve("tree-" + commit.getShortId()));
			reader.extractCommitTree(clone, commit.getId(), tree);
			reader.getDiff(clone, commit.getId());
			reader.getChangedFiles(clone, commit.getId());
		}

		assertEquals(before, DirectoryFingerprint.of(clone));
	}
}
