package at.jku.isse.ecco.service.git;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.SourcePosition;
import at.jku.isse.ecco.adapter.dispatch.DispatchReader;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.FileMode;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads commit history and content from a LOCAL git clone (no fetch/clone of its own - the
 * repository at {@code repoDir} must already exist on disk) via JGit, for the "Import from Git"
 * feature. Deliberately never touches the clone's own working directory or index - every method
 * either reads history metadata or writes a commit's tree snapshot out to a caller-supplied,
 * separate directory, since the clone may be in active use by the user while an import is
 * running.
 */
public final class GitHistoryReader {

	private static final java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger(GitHistoryReader.class.getName());

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
	 * The tags of the clone by the commit each names (an annotated tag peeled to its commit), the
	 * names of a commit's tags sorted - a tag of a tree or blob is passed over.
	 */
	public Map<String, List<String>> listTags(Path repoDir) {
		try (Repository repository = openRepository(repoDir);
			 RevWalk revWalk = new RevWalk(repository)) {
			Map<String, List<String>> tags = new LinkedHashMap<>();
			for (org.eclipse.jgit.lib.Ref ref : repository.getRefDatabase().getRefsByPrefix(org.eclipse.jgit.lib.Constants.R_TAGS)) {
				org.eclipse.jgit.revwalk.RevObject target = revWalk.peel(revWalk.parseAny(ref.getObjectId()));
				if (target instanceof RevCommit commit)
					tags.computeIfAbsent(commit.getId().name(), id -> new ArrayList<>()).add(ref.getName().substring(org.eclipse.jgit.lib.Constants.R_TAGS.length()));
			}
			tags.values().forEach(names -> names.sort(null));
			return tags;
		} catch (IOException e) {
			throw new EccoException("Error reading the tags of " + repoDir, e);
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
						// a submodule is a gitlink: it points at a commit of ANOTHER repository, which
						// isn't in this object database - reading it failed the whole extraction. Its
						// content isn't part of this repository, so it is skipped (.gitmodules stays).
						if (treeWalk.getFileMode(0) == FileMode.GITLINK) {
							LOGGER.info("Skipping submodule " + treeWalk.getPathString() + " in commit " + commitId);
							continue;
						}
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
			AbstractTreeIterator[] trees = parentAndCommitTrees(revWalk, reader, commitId);
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			try (DiffFormatter formatter = new DiffFormatter(out)) {
				formatter.setRepository(repository);
				formatter.format(trees[0], trees[1]);
			}
			return truncate(out.toString(StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new EccoException("Error diffing commit " + commitId + " from " + repoDir, e);
		}
	}

	/** Every file the commit adds, changes or deletes (relative paths, with '/'), against its first parent - unlike {@link #getDiff}, never truncated. */
	public List<String> getChangedFiles(Path repoDir, String commitId) {
		try (Repository repository = openRepository(repoDir);
			 RevWalk revWalk = new RevWalk(repository);
			 ObjectReader reader = repository.newObjectReader()) {
			AbstractTreeIterator[] trees = parentAndCommitTrees(revWalk, reader, commitId);
			List<String> paths = new ArrayList<>();
			try (DiffFormatter formatter = new DiffFormatter(OutputStream.nullOutputStream())) {
				formatter.setRepository(repository);
				for (DiffEntry entry : formatter.scan(trees[0], trees[1]))
					paths.add(entry.getChangeType() == DiffEntry.ChangeType.DELETE ? entry.getOldPath() : entry.getNewPath());
			}
			return paths;
		} catch (IOException e) {
			throw new EccoException("Error diffing commit " + commitId + " from " + repoDir, e);
		}
	}

	/** Files larger than this are located by file only: reading them for positions costs more than it tells. */
	private static final long MAX_POSITIONS_FILE_SIZE = 1 << 20;

	/**
	 * The positions (see {@link SourcePosition}) each changed file of the commit has changes in,
	 * against its first parent: the innermost position around every changed line, of the old version
	 * for lines removed or replaced and of the new one for lines added. Blank lines are passed over:
	 * they locate nothing (one between two sections is the enclosing section's). Positions are named by the
	 * adapter that reads the file in the repository {@code reader} belongs to; a file whose adapter
	 * names none, or a binary or very large one, has no positions - it is located by file only.
	 *
	 * Renames are detected: a renamed file is one change, under its new path. The commit a shallow
	 * clone was cut at has its files as modified, not added - it only looks as if it added them.
	 *
	 * @return each changed file (its new path, the old one if deleted) with its lines added and removed and its positions
	 */
	public Map<String, ChangedFile> getChangedPositions(Path repoDir, String commitId, DispatchReader dispatchReader) {
		Map<String, ChangedFile> positions = new LinkedHashMap<>();
		Path tmp = null;
		try (Repository repository = openRepository(repoDir);
			 RevWalk revWalk = new RevWalk(repository);
			 ObjectReader reader = repository.newObjectReader()) {
			tmp = Files.createTempDirectory("ecco-positions");
			AbstractTreeIterator[] trees = parentAndCommitTrees(revWalk, reader, commitId);
			boolean shallowBoundary = repository.getObjectDatabase().getShallowCommits().contains(ObjectId.fromString(commitId));
			try (DiffFormatter formatter = new DiffFormatter(OutputStream.nullOutputStream())) {
				formatter.setRepository(repository);
				formatter.setDetectRenames(true);
				for (DiffEntry entry : formatter.scan(trees[0], trees[1])) {
					boolean deleted = entry.getChangeType() == DiffEntry.ChangeType.DELETE;
					ChangeKind kind = switch (entry.getChangeType()) {
						case ADD -> shallowBoundary ? ChangeKind.MODIFIED : ChangeKind.ADDED;
						case DELETE -> ChangeKind.DELETED;
						case RENAME -> ChangeKind.RENAMED;
						default -> ChangeKind.MODIFIED;
					};
					String oldPath = entry.getChangeType() == DiffEntry.ChangeType.RENAME ? entry.getOldPath() : null;
					Set<String> names = new LinkedHashSet<>();
					String path = deleted ? entry.getOldPath() : entry.getNewPath();
					EditList edits = formatter.toFileHeader(entry).toEditList();
					int added = 0, removed = 0;
					for (Edit edit : edits) {
						added += edit.getLengthB();
						removed += edit.getLengthA();
					}
					positions.put(path, new ChangedFile(path, added, removed, names, kind, oldPath));
					if (edits.isEmpty())
						continue;
					Located oldVersion = entry.getChangeType() == DiffEntry.ChangeType.ADD ? Located.NONE
							: positionsOf(reader, entry.getOldId().toObjectId(), entry.getOldPath(), tmp.resolve("old"), dispatchReader);
					Located newVersion = deleted ? Located.NONE
							: positionsOf(reader, entry.getNewId().toObjectId(), entry.getNewPath(), tmp.resolve("new"), dispatchReader);
					for (Edit edit : edits) {
						for (int line = edit.getBeginA() + 1; line <= edit.getEndA(); line++)
							oldVersion.addInnermost(line, names);
						for (int line = edit.getBeginB() + 1; line <= edit.getEndB(); line++)
							newVersion.addInnermost(line, names);
					}
				}
			}
			return positions;
		} catch (IOException e) {
			throw new EccoException("Error locating the changes of commit " + commitId + " from " + repoDir, e);
		} finally {
			if (tmp != null)
				deleteQuietly(tmp);
		}
	}

	/** How a commit changed a file. */
	public enum ChangeKind {ADDED, MODIFIED, DELETED, RENAMED}

	/**
	 * A file a commit changed: its lines added and removed, the positions it changed (see
	 * {@link #getChangedPositions}), how, and for a rename, its old path (else null).
	 */
	public record ChangedFile(String path, int added, int removed, Set<String> positions, ChangeKind kind, String oldPath) {

		public ChangedFile(String path, int added, int removed, Set<String> positions) {
			this(path, added, removed, positions, ChangeKind.MODIFIED, null);
		}
	}

	/** One version of a file: its positions and its lines. */
	private record Located(List<SourcePosition> positions, RawText text) {

		static final Located NONE = new Located(List.of(), null);

		/** Adds the name of the innermost position around (1-based) {@code line}, unless it is blank. */
		void addInnermost(int line, Set<String> names) {
			if (this.positions.isEmpty() || this.text == null || line > this.text.size() || this.text.getString(line - 1).isBlank())
				return;
			SourcePosition innermost = SourcePosition.innermost(this.positions, line);
			if (innermost != null)
				names.add(innermost.name());
		}
	}

	/** One version of a file, written under {@code base} at its own path (so the repository's adapter mapping applies), and read for its positions. */
	private static Located positionsOf(ObjectReader reader, ObjectId blob, String path, Path base, DispatchReader dispatchReader) throws IOException {
		if (blob == null || ObjectId.zeroId().equals(blob) || reader.getObjectSize(blob, ObjectReader.OBJ_ANY) > MAX_POSITIONS_FILE_SIZE)
			return Located.NONE;
		byte[] content = reader.open(blob).getBytes();
		if (RawText.isBinary(content))
			return Located.NONE;
		Path file = base.resolve(path).normalize();
		if (!file.startsWith(base))
			return Located.NONE;
		Files.createDirectories(file.getParent());
		Files.write(file, content);
		try {
			return new Located(dispatchReader.positions(base, Path.of(path)), new RawText(content));
		} finally {
			Files.deleteIfExists(file);
		}
	}

	private static void deleteQuietly(Path dir) {
		try (java.util.stream.Stream<Path> paths = Files.walk(dir)) {
			paths.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
				try {
					Files.deleteIfExists(p);
				} catch (IOException ignored) {
					// a temporary directory
				}
			});
		} catch (IOException ignored) {
			// a temporary directory
		}
	}

	/** The trees of the commit's first parent (empty for a root commit) and of the commit. */
	private static AbstractTreeIterator[] parentAndCommitTrees(RevWalk revWalk, ObjectReader reader, String commitId) throws IOException {
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
		return new AbstractTreeIterator[]{oldTreeIter, newTreeIter};
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
