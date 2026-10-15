package at.jku.isse.ecco.storage.ser.dao;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * The roll-forward journal of a write (SerTransactionStrategy#endReadWrite()): the files a write
 * stages under a name tagged with its new core id, and how they are moved into place once the id
 * file names that core - or discarded, if it never did.
 */
final class CommitJournal {

	private static final String PENDING_SUFFIX = ".pending";

	// the directories files are staged in
	private final List<Path> directories;

	CommitJournal(Path... directories) {
		this.directories = List.of(directories);
	}

	/**
	 * Suffix of a dirty association/artifact file staged by SerTransactionStrategy#endReadWrite() for the
	 * transaction that will make {@code txId} the current core id - see SerTransactionStrategy#endReadWrite().
	 */
	static String pendingSuffix(String txId) {
		return "." + txId + PENDING_SUFFIX;
	}

	/**
	 * Resolves the staged files of an interrupted-but-committed transaction and discards those of
	 * aborted ones, in both per-entity directories - see SerTransactionStrategy#endReadWrite() for the protocol.
	 * <p>
	 * Pending files tagged with {@code currentId} belong to a transaction whose id-file swap (its
	 * commit point) already happened, so they are the authoritative versions and are renamed over
	 * their stable names (roll forward). Any other pending file belongs to a transaction that never
	 * reached its swap; those are only deleted when {@code discardAborted} is set, i.e. when the
	 * caller holds the exclusive write lock - otherwise they might be the in-flight files of a
	 * concurrent writer in another process, which must not be touched.
	 */
	void recoverPendingFiles(String currentId, boolean discardAborted) throws IOException {
		for (Path dir : this.directories) {
			if (!Files.isDirectory(dir)) continue;
			List<Path> pendingFiles;
			try (var files = Files.list(dir)) {
				pendingFiles = files.filter(f -> f.getFileName().toString().endsWith(PENDING_SUFFIX)).toList();
			}
			String committedSuffix = currentId == null ? null : pendingSuffix(currentId);
			for (Path pending : pendingFiles) {
				String name = pending.getFileName().toString();
				if (committedSuffix != null && name.endsWith(committedSuffix)) {
					Path stable = pending.resolveSibling(name.substring(0, name.length() - committedSuffix.length()));
					try {
						Files.move(pending, stable, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
					} catch (NoSuchFileException e) {
						// another process rolled this one forward concurrently - nothing left to do
					}
				} else if (discardAborted) {
					Files.deleteIfExists(pending);
				}
			}
		}
	}
}
