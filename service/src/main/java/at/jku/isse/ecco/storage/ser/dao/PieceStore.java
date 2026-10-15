package at.jku.isse.ecco.storage.ser.dao;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.storage.ser.tree.TreePieces;
import at.jku.isse.ecco.tree.Node;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static at.jku.isse.ecco.storage.ser.dao.StorageFiles.TMP_SUFFIX;

/**
 * The pieces of the associations' trees ({@code pieces/pack-<writeId>.zip}, see {@link TreePieces}):
 * each piece an entry named by its digest, the new pieces of a write in one pack. A piece is
 * content-addressed - written once, whichever association refers to it, whatever its id.
 * <p>
 * Which pack holds a piece is found by listing the packs when a repository is read; a pack nothing
 * refers to anymore (also one a crashed write left) is deleted after a committed write, once no
 * reader holds the previous core; one of which less than half is still referred to is compacted -
 * its pieces still referred to go into the new pack.
 */
final class PieceStore {

	private static final String PACK_PREFIX = "pack-";
	private static final String PACK_SUFFIX = ".zip";

	private final Path dir;
	// the pack of each stored piece, by its digest
	private final Map<String, String> packOf = new HashMap<>();
	// the pieces each association refers to, by its id - which pieces are still referred to
	private final Map<String, Set<String>> piecesOf = new HashMap<>();
	// the pieces a write stores, by digest, until its pack is written
	private final Map<String, byte[]> staged = new LinkedHashMap<>();
	// while a repository is read: the packs opened, and the bytes of the pieces read (a piece several
	// associations refer to is read once - each gets its own nodes)
	private final Map<String, ZipFile> openPacks = new HashMap<>();
	private final Map<String, byte[]> readBytes = new HashMap<>();

	PieceStore(Path dir) {
		this.dir = dir;
	}

	/** Lists the stored pieces - before the associations, whose trees refer to them, are read. */
	void load() throws IOException {
		this.packOf.clear();
		this.piecesOf.clear();
		if (!Files.isDirectory(this.dir))
			return;
		try (DirectoryStream<Path> packs = Files.newDirectoryStream(this.dir, PACK_PREFIX + "*" + PACK_SUFFIX)) {
			for (Path pack : packs) {
				try (ZipFile zip = new ZipFile(pack.toFile())) {
					for (ZipEntry entry : java.util.Collections.list(zip.entries()))
						this.packOf.put(entry.getName(), pack.getFileName().toString());
				}
			}
		}
	}

	/** Association [associationId] is read: the pieces it refers to are those read for it (none, for a file written before pieces). */
	void reading(String associationId) {
		this.piecesOf.put(associationId, new HashSet<>());
	}

	/** The piece [digest] association [associationId] refers to, read into new nodes. */
	List<Node.Op> read(String associationId, String digest) throws IOException, ClassNotFoundException {
		this.piecesOf.computeIfAbsent(associationId, id -> new HashSet<>()).add(digest);
		byte[] bytes = this.readBytes.get(digest);
		if (bytes == null) {
			bytes = this.staged.get(digest);
			if (bytes == null) {
				String pack = this.packOf.get(digest);
				if (pack == null)
					throw new EccoException("The tree piece " + digest + " is missing - the repository is damaged.");
				ZipFile zip = this.openPacks.get(pack);
				if (zip == null) {
					zip = new ZipFile(this.dir.resolve(pack).toFile());
					this.openPacks.put(pack, zip);
				}
				try (InputStream in = zip.getInputStream(zip.getEntry(digest))) {
					bytes = in.readAllBytes();
				}
			}
			this.readBytes.put(digest, bytes);
		}
		return TreePieces.read(bytes);
	}

	/** After the associations were read: what was opened for them is let go. */
	void loaded() throws IOException {
		for (ZipFile zip : this.openPacks.values())
			zip.close();
		this.openPacks.clear();
		this.readBytes.clear();
	}

	/** Before association [associationId] is written: the pieces it refers to are those it is written with. */
	void writing(String associationId) {
		this.piecesOf.put(associationId, new HashSet<>());
	}

	/** Stores the piece below [pieceRoot] of association [associationId] - unless it is stored already; its digest. */
	String stage(String associationId, Node.Op pieceRoot) throws IOException {
		byte[] bytes = TreePieces.write(pieceRoot);
		String digest = TreePieces.digest(bytes);
		this.piecesOf.computeIfAbsent(associationId, id -> new HashSet<>()).add(digest);
		if (!this.packOf.containsKey(digest))
			this.staged.putIfAbsent(digest, bytes);
		return digest;
	}

	/** The associations [removedIds] are gone: the pieces they referred to may be too. */
	void removed(Collection<String> removedIds) {
		removedIds.forEach(this.piecesOf::remove);
	}

	/**
	 * Writes the staged pieces, and those of packs less than half referred to anymore, into the pack
	 * {@code pack-<newId>.zip} - its final name right away: until the core naming the write is in
	 * place nothing refers to it.
	 */
	void write(String newId) throws IOException {
		Set<String> referenced = this.referenced();
		Map<String, Integer> packSize = new HashMap<>(), packReferenced = new HashMap<>();
		for (Map.Entry<String, String> stored : this.packOf.entrySet()) {
			packSize.merge(stored.getValue(), 1, Integer::sum);
			if (referenced.contains(stored.getKey()))
				packReferenced.merge(stored.getValue(), 1, Integer::sum);
		}
		Map<String, byte[]> entries = new LinkedHashMap<>(this.staged);
		for (Map.Entry<String, Integer> pack : packSize.entrySet()) {
			int stillReferred = packReferenced.getOrDefault(pack.getKey(), 0);
			if (stillReferred == 0 || stillReferred * 2 >= pack.getValue())
				continue; // nothing to keep (deleted once committed), or worth keeping
			try (ZipFile zip = new ZipFile(this.dir.resolve(pack.getKey()).toFile())) {
				for (ZipEntry entry : java.util.Collections.list(zip.entries()))
					if (referenced.contains(entry.getName()) && !entries.containsKey(entry.getName()))
						try (InputStream in = zip.getInputStream(entry)) {
							entries.put(entry.getName(), in.readAllBytes());
						}
			}
		}
		if (entries.isEmpty())
			return;
		Files.createDirectories(this.dir);
		String packName = PACK_PREFIX + newId + PACK_SUFFIX;
		Path pack = this.dir.resolve(packName);
		Path tmpFile = pack.resolveSibling(packName + "." + UUID.randomUUID() + TMP_SUFFIX);
		try {
			try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(tmpFile, StandardOpenOption.CREATE_NEW), 1 << 16))) {
				zos.setLevel(Deflater.BEST_SPEED);
				for (Map.Entry<String, byte[]> piece : entries.entrySet()) {
					zos.putNextEntry(new ZipEntry(piece.getKey()));
					zos.write(piece.getValue());
					zos.closeEntry();
				}
			}
			Files.move(tmpFile, pack, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} finally {
			Files.deleteIfExists(tmpFile);
		}
		for (String digest : entries.keySet())
			this.packOf.put(digest, packName);
		this.staged.clear();
	}

	/**
	 * After a committed write, when no reader holds the previous core: deletes the packs no piece of
	 * which is referred to anymore, and packs left half-written by a crash.
	 */
	void deleteUnreferenced(Collection<String> associationIds) throws IOException {
		if (!Files.isDirectory(this.dir))
			return;
		// which pieces are referred to is only known if every association was read or written: else
		// nothing is deleted (a later write will)
		if (!this.piecesOf.keySet().containsAll(associationIds)) {
			java.util.logging.Logger.getLogger(PieceStore.class.getName()).warning("Not every association's pieces are known - no piece pack deleted.");
			return;
		}
		Set<String> referenced = this.referenced();
		Set<String> keep = new HashSet<>();
		for (String digest : referenced) {
			String pack = this.packOf.get(digest);
			if (pack != null)
				keep.add(pack);
		}
		List<Path> gone = new ArrayList<>();
		try (DirectoryStream<Path> packs = Files.newDirectoryStream(this.dir, PACK_PREFIX + "*" + PACK_SUFFIX)) {
			for (Path pack : packs)
				if (!keep.contains(pack.getFileName().toString()))
					gone.add(pack);
		}
		for (Path pack : gone) {
			Files.deleteIfExists(pack);
			this.packOf.values().removeIf(pack.getFileName().toString()::equals);
		}
		try (DirectoryStream<Path> unfinished = Files.newDirectoryStream(this.dir, PACK_PREFIX + "*" + PACK_SUFFIX + ".*" + TMP_SUFFIX)) {
			for (Path pack : unfinished)
				Files.deleteIfExists(pack);
		}
	}

	/** Forgets everything - the next transaction reads the repository again. */
	void clear() {
		this.packOf.clear();
		this.piecesOf.clear();
		this.staged.clear();
		this.readBytes.clear();
		for (ZipFile zip : this.openPacks.values())
			try {
				zip.close();
			} catch (IOException e) {
				// nothing held on to
			}
		this.openPacks.clear();
	}

	private Set<String> referenced() {
		Set<String> referenced = new HashSet<>();
		for (Set<String> pieces : this.piecesOf.values())
			referenced.addAll(pieces);
		return referenced;
	}
}
