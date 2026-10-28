package at.jku.isse.ecco.storage.ser.dao;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.storage.ser.counter.CompactCounters;
import at.jku.isse.ecco.storage.ser.tree.PieceSink;
import at.jku.isse.ecco.storage.ser.tree.PieceSource;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * The files of a repository's storage and how one object is written to and read from one: Java
 * serialization in the single entry of a zip file, written to a temporary file and renamed into
 * place.
 */
final class StorageFiles {

	/** The suffix of the core and of each association's file. */
	static final String DB_FILE_SUFFIX = ".ser.zip";
	/** The suffix of a file being written, renamed into place when complete. */
	static final String TMP_SUFFIX = ".tmp";

	/** A pack's name: {@code pack-<id of the write>.zip} - artifacts, associations and pieces each in a directory of their own. */
	static final String PACK_PREFIX = "pack-";
	static final String PACK_SUFFIX = ".zip";
	/** Packs at most: every write adds one; once there are more, the smallest are merged into the new one. */
	static final int MAX_PACKS = 20;

	/** What a zip written by {@link #writeZip} holds. */
	@FunctionalInterface
	interface ZipContent {
		void writeTo(ZipOutputStream zip) throws IOException;
	}

	/**
	 * Writes [file] - a zip, compressed at the fastest level, holding what [content] writes - into a
	 * temporary file first, moved to its place at once when complete: a failed write (out of memory
	 * or disk space) leaves nothing behind, a reader never sees half a file. The three pack stores and
	 * the core each had their own copy of this.
	 */
	static void writeZip(Path file, ZipContent content) throws IOException {
		Path tmpFile = file.resolveSibling(file.getFileName() + "." + UUID.randomUUID() + TMP_SUFFIX);
		try {
			try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(tmpFile, StandardOpenOption.CREATE_NEW), 1 << 16))) {
				zos.setLevel(Deflater.BEST_SPEED);
				content.writeTo(zos);
			}
			Files.move(tmpFile, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} finally {
			Files.deleteIfExists(tmpFile);
		}
	}

	/** Writes [file] as a zip of [entries], by name - see {@link #writeZip}. */
	static void writeZipEntries(Path file, Map<String, byte[]> entries) throws IOException {
		writeZip(file, zos -> {
			for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
				zos.putNextEntry(new ZipEntry(entry.getKey()));
				zos.write(entry.getValue());
				zos.closeEntry();
			}
		});
	}

	/** Whether a pack with [current] of its [size] entries still current is kept: else they are moved into the new pack. */
	static boolean keepsPack(long current, long size) {
		return current * 2 >= size;
	}

	/**
	 * Of the packs [kept], the smallest by [current] to merge into the new pack when they would be more
	 * than {@link #MAX_PACKS} with it - none otherwise. They are taken off [kept].
	 */
	static List<String> packsToMerge(List<String> kept, java.util.function.ToIntFunction<String> current) {
		if (kept.size() + 1 <= MAX_PACKS)
			return List.of();
		kept.sort(java.util.Comparator.comparingInt(current));
		List<String> merged = new java.util.ArrayList<>(kept.subList(0, kept.size() + 1 - MAX_PACKS / 2));
		kept.removeAll(merged);
		return merged;
	}
	/** The single entry of a core or association file. */
	static final String ZIP_ENTRY_NAME = "ecco.ser";

	private StorageFiles() {
	}

	/**
	 * Serializes object as the single zip entry of file, compressed at the fastest level (see
	 * writeStoredBytes()).
	 * <p>
	 * Writes fully to a temp file in {@code file}'s own directory first, then atomically renames
	 * it into place - writing directly to {@code file} (as this used to do, via
	 * {@code Files.newOutputStream(file, CREATE)}, which truncates an already-existing file before
	 * writing the new bytes) is not crash-safe when {@code file} already exists and is already
	 * referenced by the current, not-yet-swapped core (true for any dirty association/artifact
	 * that already existed before this transaction, not just brand new ones): a crash between the
	 * truncate and the write completing would leave that file empty/partial, corrupting state the
	 * still-current core expects to load successfully on the next open. Same directory guarantees
	 * the rename is on the same filesystem, a precondition for {@link StandardCopyOption#ATOMIC_MOVE}.
	 */
	static void writeStored(Object object, Path file) throws IOException {
		writeStoredBytes(serialize(object), file);
	}

	static byte[] serialize(Object object) throws IOException {
		ByteArrayOutputStream serialized = new ByteArrayOutputStream();
		try (ObjectOutputStream oos = new ObjectOutputStream(serialized)) {
			oos.writeObject(object);
		}
		return serialized.toByteArray();
	}

	/**
	 * Like {@link #serialize}, with the association's counters in compact form (see
	 * {@link CompactCounters}, referring to [repository]'s list of counted module revisions) and its
	 * files' subtrees given to [pieces] (see TreePieces).
	 */
	static byte[] serializeAssociation(Object association, PieceSink pieces, at.jku.isse.ecco.storage.ser.repository.SerRepository repository) throws IOException {
		ByteArrayOutputStream serialized = new ByteArrayOutputStream();
		try (ObjectOutputStream oos = new AssociationOutput(serialized, pieces, repository)) {
			oos.writeObject(association);
		}
		return serialized.toByteArray();
	}

	/** The stream an association is written to: counters compact, files' subtrees as pieces. */
	private static final class AssociationOutput extends CompactCounters.Stream implements PieceSink {
		private final PieceSink pieces;

		AssociationOutput(java.io.OutputStream out, PieceSink pieces, at.jku.isse.ecco.storage.ser.repository.SerRepository repository) throws IOException {
			super(out, repository);
			this.pieces = pieces;
		}

		@Override
		public String piece(at.jku.isse.ecco.tree.Node.Op pieceRoot) throws IOException {
			return this.pieces.piece(pieceRoot);
		}
	}

	/** The stream an association is read from: the pieces its tree refers to from [pieces]. */
	static final class AssociationInput extends ObjectInputStream implements PieceSource {
		private final PieceSource pieces;

		AssociationInput(java.io.InputStream in, PieceSource pieces) throws IOException {
			super(in);
			this.pieces = pieces;
		}

		@Override
		public java.util.List<at.jku.isse.ecco.tree.Node.Op> piece(String digest) throws IOException, ClassNotFoundException {
			return this.pieces.piece(digest);
		}
	}

	static byte[] digest(byte[] bytes) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(bytes);
		} catch (NoSuchAlgorithmException e) {
			throw new EccoException("SHA-256 not available.", e);
		}
	}

	static void writeStoredBytes(byte[] serializedBytes, Path file) throws IOException {
		// compressed at the fastest level, the whole array at once, into a buffered stream: 2-3.6x
		// smaller for about 0.1 s per 15 MB. (Compressing used to cost 10-15 s per commit and was
		// turned off - streamed through ZipOutputStream's 512 byte buffer into an unbuffered file.)
		writeZip(file, zos -> {
			zos.putNextEntry(new ZipEntry(ZIP_ENTRY_NAME));
			zos.write(serializedBytes);
		});
	}

}
