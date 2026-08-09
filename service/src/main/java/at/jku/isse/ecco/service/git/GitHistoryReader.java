package at.jku.isse.ecco.service.git;

import at.jku.isse.ecco.EccoException;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Reads commit history and content from a LOCAL git clone (no fetch/clone of its own - the
 * repository at {@code repoDir} must already exist on disk) via JGit, for the "Import from Git"
 * feature. Deliberately never touches the clone's own working directory or index - every method
 * either reads history metadata or writes a commit's tree snapshot out to a caller-supplied,
 * separate directory, since the clone may be in active use by the user while an import is
 * running.
 */
public final class GitHistoryReader {

	private static final int MAX_DIFF_LINES = 400;

	/**
	 * Commits reachable from HEAD, newest first (matching normal {@code git log} order) - the
	 * import dialog's range picker shows them in this order and lets the user select a
	 * contiguous range by row index; callers that need to actually apply commits should process
	 * that selection oldest-first (reverse this list), since later ecco commits must build on
	 * earlier ones.
	 */
	public List<GitCommitInfo> listCommits(Path repoDir) {
		try (Repository repository = openRepository(repoDir);
			 Git git = new Git(repository)) {
			List<GitCommitInfo> commits = new ArrayList<>();
			for (RevCommit commit : git.log().call()) {
				commits.add(toCommitInfo(commit));
			}
			return commits;
		} catch (IOException | org.eclipse.jgit.api.errors.GitAPIException e) {
			throw new EccoException("Error reading commit history from " + repoDir, e);
		}
	}

	/**
	 * Writes {@code commitId}'s full tree snapshot into {@code targetDir} (which must already
	 * exist and be empty) - the equivalent of {@code git archive <commit> | tar -x -C targetDir},
	 * without needing a working-tree checkout that would disturb the clone itself.
	 */
	public void extractCommitTree(Path repoDir, String commitId, Path targetDir) {
		Path normalizedTargetDir = targetDir.normalize();
		try (Repository repository = openRepository(repoDir)) {
			try (RevWalk revWalk = new RevWalk(repository)) {
				RevCommit commit = revWalk.parseCommit(ObjectId.fromString(commitId));
				RevTree tree = commit.getTree();
				try (TreeWalk treeWalk = new TreeWalk(repository)) {
					treeWalk.addTree(tree);
					treeWalk.setRecursive(true);
					ObjectReader reader = treeWalk.getObjectReader();
					while (treeWalk.next()) {
						// a tree entry name is a single path component and can legally be ".." at the
						// raw-object level even though porcelain git refuses to create one - reject
						// anything that would resolve outside targetDir instead of trusting it blindly
						// (the same class of bug as zip-slip for archive extraction).
						Path outFile = targetDir.resolve(treeWalk.getPathString()).normalize();
						if (!outFile.startsWith(normalizedTargetDir)) {
							throw new EccoException("Refusing to extract entry outside target directory: " + treeWalk.getPathString());
						}
						Files.createDirectories(outFile.getParent());
						ObjectLoader loader = reader.open(treeWalk.getObjectId(0));
						try (OutputStream out = Files.newOutputStream(outFile)) {
							loader.copyTo(out);
						}
					}
				}
			}
		} catch (IOException e) {
			throw new EccoException("Error extracting commit " + commitId + " from " + repoDir, e);
		}
	}

	/**
	 * Unified diff for {@code commitId} against its first parent (or against an empty tree for a
	 * root commit), truncated to {@value #MAX_DIFF_LINES} lines - a full diff for a large commit
	 * would blow an LLM prompt's context budget for little added feature-classification signal.
	 */
	public String getDiff(Path repoDir, String commitId) {
		try (Repository repository = openRepository(repoDir);
			 RevWalk revWalk = new RevWalk(repository);
			 ObjectReader reader = repository.newObjectReader()) {
			RevCommit commit = revWalk.parseCommit(ObjectId.fromString(commitId));
			revWalk.parseHeaders(commit);

			AbstractTreeIterator oldTreeIter;
			if (commit.getParentCount() > 0) {
				RevCommit parent = revWalk.parseCommit(commit.getParent(0).getId());
				CanonicalTreeParser parser = new CanonicalTreeParser();
				parser.reset(reader, parent.getTree());
				oldTreeIter = parser;
			} else {
				oldTreeIter = new EmptyTreeIterator();
			}
			CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
			newTreeIter.reset(reader, commit.getTree());

			ByteArrayOutputStream out = new ByteArrayOutputStream();
			try (DiffFormatter formatter = new DiffFormatter(out)) {
				formatter.setRepository(repository);
				formatter.format(oldTreeIter, newTreeIter);
			}
			return truncate(out.toString(StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new EccoException("Error diffing commit " + commitId + " from " + repoDir, e);
		}
	}

	private static String truncate(String diff) {
		String[] lines = diff.split("\n", -1);
		if (lines.length <= MAX_DIFF_LINES) {
			return diff;
		}
		return String.join("\n", Arrays.asList(lines).subList(0, MAX_DIFF_LINES))
				+ "\n... (diff truncated, " + (lines.length - MAX_DIFF_LINES) + " more lines)";
	}

	private static Repository openRepository(Path repoDir) throws IOException {
		Path gitDir = repoDir.resolve(".git");
		if (!Files.exists(gitDir)) {
			throw new EccoException("Not a git repository (no .git found): " + repoDir);
		}
		return new FileRepositoryBuilder()
				.setGitDir(gitDir.toFile())
				.readEnvironment()
				.findGitDir()
				.build();
	}

	private static GitCommitInfo toCommitInfo(RevCommit commit) {
		PersonIdent author = commit.getAuthorIdent();
		return new GitCommitInfo(
				commit.getId().name(),
				commit.getShortMessage(),
				author != null ? author.getName() : "",
				Instant.ofEpochSecond(commit.getCommitTime()));
	}

}
