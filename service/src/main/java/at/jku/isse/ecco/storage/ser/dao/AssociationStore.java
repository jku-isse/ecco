package at.jku.isse.ecco.storage.ser.dao;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.storage.ser.repository.SerRepository;
import at.jku.isse.ecco.storage.ser.tree.TreePieces;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
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
import static at.jku.isse.ecco.storage.ser.dao.StorageFiles.digest;
import static at.jku.isse.ecco.storage.ser.dao.StorageFiles.serializeAssociation;

/**
 * The associations ({@code associations/pack-<writeId>.zip}): the associations a write changed go
 * into one pack of its own, an entry each, named by the association's id - one file per write
 * rather than one per association (on a repository in a synchronized folder every file a commit
 * writes is a file the service picks up). The core keeps the order the packs were written in, and
 * a later pack's copy of an association is the current one; an association whose bytes did not
 * change is not written. Packs of which less than half is current are compacted, the smallest
 * merged once there are too many; packs no longer in the order are deleted once no reader holds the
 * previous core.
 */
final class AssociationStore {

	/**
	 * Serialize the associations a write leaves alone (not added again since they were read), and
	 * fail if one differs from what is stored - a change made without Repository.Op#addAssociation()
	 * would otherwise not be stored, as the retroactive conditions of untouched associations were
	 * not (ReopenEquivalenceTest). On in the tests (build.gradle); -Decco.verifyUnchangedAssociations=true for a replay.
	 */
	static final boolean VERIFY_UNCHANGED_ASSOCIATIONS = Boolean.getBoolean("ecco.verifyUnchangedAssociations");

	private static final String PACK_PREFIX = "pack-";
	private static final String PACK_SUFFIX = ".zip";
	/** Once a write would leave more packs than this, the smallest are merged down to half of it. */
	private static final int MAX_PACKS = 20;

	private final Path dir;
	// the pieces of the associations' trees (see TreePieces)
	private final PieceStore pieces;
	// the repository being written or read - its counted module revisions (see CompactCounters)
	private SerRepository repository;
	// each stored association's digest (of its stored bytes) and pack (null: a file of its own) -
	// what a write need not write again, and where a compaction finds it
	private final Map<String, byte[]> storedDigests = new HashMap<>();
	private final Map<String, String> packOf = new HashMap<>();
	// the number of entries of each pack
	private final Map<String, Integer> packSizes = new HashMap<>();
	/**
	 * Only with {@link #VERIFY_UNCHANGED_ASSOCIATIONS}: the digest of each association as serialized
	 * when it was last read or written. Not the stored bytes': an association read back serializes to
	 * the same content in different bytes (a boxed property value its file shares by reference is its
	 * own object after reading).
	 */
	private final Map<String, byte[]> verifiedAssociationDigests = new HashMap<>();

	/** What a write stored: the associations it wrote (id -> digest) into [pack] (null: none). */
	record Staged(String pack, Map<String, byte[]> digests) {
	}

	AssociationStore(Path dir, PieceStore pieces) {
		this.dir = dir;
		this.pieces = pieces;
	}

	/** Reads every association of [repo]: each from the latest pack holding it, or its own file. */
	List<Association.Op> readAll(SerRepository repo) throws IOException, ClassNotFoundException {
		Map<String, String> latest = new HashMap<>();
		Map<String, ZipFile> zips = new LinkedHashMap<>();
		try {
			for (String pack : repo.getAssociationPackOrder()) {
				Path file = this.dir.resolve(pack);
				if (!Files.exists(file))
					throw new EccoException("The association pack " + pack + " is missing - the repository is damaged.");
				ZipFile zip = new ZipFile(file.toFile());
				zips.put(pack, zip);
				int size = 0;
				for (ZipEntry entry : Collections.list(zip.entries())) {
					latest.put(entry.getName(), pack);
					size++;
				}
				this.packSizes.put(pack, size);
			}
			List<Association.Op> associations = new ArrayList<>(repo.getAssociationIds().size());
			for (String id : repo.getAssociationIds()) {
				String pack = latest.get(id);
				if (pack == null)
					throw new EccoException("The association " + id + " is in no association pack - the repository is damaged.");
				byte[] bytes;
				ZipFile zip = zips.get(pack);
				try (InputStream in = zip.getInputStream(zip.getEntry(id))) {
					bytes = in.readAllBytes();
				}
				this.storedDigests.put(id, digest(bytes));
				this.packOf.put(id, pack);
				this.pieces.reading(id);
				try (ObjectInputStream ois = new StorageFiles.AssociationInput(new ByteArrayInputStream(bytes), digest -> this.pieces.read(id, digest))) {
					associations.add((Association.Op) ois.readObject());
				}
			}
			return associations;
		} finally {
			for (ZipFile zip : zips.values())
				zip.close();
		}
	}

	/** After [repo] was read: with {@link #VERIFY_UNCHANGED_ASSOCIATIONS}, records its associations as they serialize now. */
	void loaded(SerRepository repo) throws IOException {
		this.repository = repo;
		if (!VERIFY_UNCHANGED_ASSOCIATIONS)
			return;
		this.verifiedAssociationDigests.clear();
		Set<Association.Op> dirty = Collections.newSetFromMap(new IdentityHashMap<>());
		dirty.addAll(repo.getDirtyAssociations());
		this.recordVerifiedDigests(repo.getAssociations().stream().filter(a -> !dirty.contains(a)).toList());
	}

	/**
	 * Writes the associations a write touched whose bytes changed - only those: most associations are
	 * untouched by any given commit (see SerCommit for why commits and the repository hold association
	 * ids, which makes it safe to leave everything else out of the core) - into the pack
	 * {@code pack-<newId>.zip}, with those compaction moves there, and sets [repo]'s pack order for the
	 * core. The pack gets its final name right away: until the core naming the write is in place,
	 * nothing refers to it.
	 */
	Staged stage(SerRepository repo, String newId) throws IOException {
		this.repository = repo;
		if (VERIFY_UNCHANGED_ASSOCIATIONS)
			this.verifyUnchangedAssociations(repo);
		this.pieces.removed(repo.getRemovedAssociationIds());
		Map<String, byte[]> entries = new LinkedHashMap<>();
		Map<String, byte[]> digests = new HashMap<>();
		for (Association.Op association : repo.getDirtyAssociations()) {
			String id = association.getId();
			this.pieces.writing(id);
			byte[] bytes = serializeAssociation(association, pieceRoot -> this.pieces.stage(id, pieceRoot), this.repository);
			byte[] digest = digest(bytes);
			if (this.storedDigests.containsKey(id) && Arrays.equals(digest, this.storedDigests.get(id)))
				continue;
			entries.put(id, bytes);
			digests.put(id, digest);
		}
		if (VERIFY_UNCHANGED_ASSOCIATIONS) {
			this.recordVerifiedDigests(repo.getDirtyAssociations());
			this.verifiedAssociationDigests.keySet().removeAll(repo.getRemovedAssociationIds());
		}

		// the current associations each pack keeps once this write is stored
		Set<String> ids = new HashSet<>(repo.getAssociationIds());
		Map<String, List<String>> current = new LinkedHashMap<>();
		for (String pack : repo.getAssociationPackOrder())
			current.put(pack, new ArrayList<>());
		for (Map.Entry<String, String> stored : this.packOf.entrySet())
			if (stored.getValue() != null && ids.contains(stored.getKey()) && !entries.containsKey(stored.getKey()) && current.containsKey(stored.getValue()))
				current.get(stored.getValue()).add(stored.getKey());
		List<String> kept = new ArrayList<>();
		List<String> compacted = new ArrayList<>();
		for (Map.Entry<String, List<String>> pack : current.entrySet()) {
			int stillCurrent = pack.getValue().size();
			if (stillCurrent == 0)
				continue;
			if (stillCurrent * 2 < this.packSizes.getOrDefault(pack.getKey(), stillCurrent))
				compacted.add(pack.getKey());
			else
				kept.add(pack.getKey());
		}
		// every write adds a pack: once there are too many, the smallest are merged into the new one
		if (kept.size() + 1 > MAX_PACKS) {
			kept.sort(Comparator.comparingInt(pack -> current.get(pack).size()));
			List<String> merged = new ArrayList<>(kept.subList(0, kept.size() + 1 - MAX_PACKS / 2));
			kept.removeAll(merged);
			compacted.addAll(merged);
		}
		// an association with a file of its own the write leaves alone stays there: no need to move it
		for (String pack : compacted)
			this.moveInto(pack, current.get(pack), entries, digests);

		List<String> order = new ArrayList<>(repo.getAssociationPackOrder());
		order.retainAll(kept);
		String packName = null;
		if (!entries.isEmpty()) {
			Files.createDirectories(this.dir);
			packName = PACK_PREFIX + newId + PACK_SUFFIX;
			this.writePack(entries, this.dir.resolve(packName));
			order.add(packName);
			this.packSizes.put(packName, entries.size());
		}
		repo.setAssociationPackOrder(order);
		return new Staged(packName, digests);
	}

	/**
	 * After the commit point: the associations written are stored now. When no reader holds the
	 * previous core ([oldCoreReleased]), the packs no longer in the order go.
	 */
	void committed(SerRepository repo, Staged staged, boolean oldCoreReleased) throws IOException {
		for (Map.Entry<String, byte[]> written : staged.digests().entrySet()) {
			this.storedDigests.put(written.getKey(), written.getValue());
			this.packOf.put(written.getKey(), staged.pack());
		}
		for (String removed : repo.getRemovedAssociationIds()) {
			this.storedDigests.remove(removed);
			this.packOf.remove(removed);
		}
		if (!oldCoreReleased || !Files.isDirectory(this.dir))
			return;
		Set<String> order = new HashSet<>(repo.getAssociationPackOrder());
		this.packSizes.keySet().retainAll(order);
		List<Path> gone = new ArrayList<>();
		try (DirectoryStream<Path> files = Files.newDirectoryStream(this.dir)) {
			for (Path file : files) {
				String name = file.getFileName().toString();
				if (name.startsWith(PACK_PREFIX) && name.endsWith(PACK_SUFFIX)) {
					if (!order.contains(name))
						gone.add(file); // superseded, or left by a crashed write
				} else if (name.startsWith(PACK_PREFIX) && name.endsWith(TMP_SUFFIX)) {
					gone.add(file); // being written when a write crashed (none is now: this one holds the lock)
				}
			}
		}
		for (Path file : gone)
			Files.deleteIfExists(file);
	}

	/** Forgets what was read and written - the next transaction reads everything again. */
	void clear() {
		this.repository = null;
		this.storedDigests.clear();
		this.packOf.clear();
		this.packSizes.clear();
		this.verifiedAssociationDigests.clear();
	}

	/** Adds the current associations [ids] of [pack] to the new pack's [entries], as they are stored. */
	private void moveInto(String pack, List<String> ids, Map<String, byte[]> entries, Map<String, byte[]> digests) throws IOException {
		try (ZipFile zip = new ZipFile(this.dir.resolve(pack).toFile())) {
			for (String id : ids) {
				try (InputStream in = zip.getInputStream(zip.getEntry(id))) {
					byte[] bytes = in.readAllBytes();
					entries.put(id, bytes);
					digests.put(id, this.storedDigests.get(id));
				}
			}
		}
	}

	private void writePack(Map<String, byte[]> entries, Path pack) throws IOException {
		Path tmpFile = pack.resolveSibling(pack.getFileName() + "." + UUID.randomUUID() + TMP_SUFFIX);
		try {
			try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(tmpFile, StandardOpenOption.CREATE_NEW), 1 << 16))) {
				zos.setLevel(Deflater.BEST_SPEED);
				for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
					zos.putNextEntry(new ZipEntry(entry.getKey()));
					zos.write(entry.getValue());
					zos.closeEntry();
				}
			}
			Files.move(tmpFile, pack, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} finally {
			Files.deleteIfExists(tmpFile);
		}
	}

	/** See {@link #VERIFY_UNCHANGED_ASSOCIATIONS}: before anything is written, so a failure leaves the repository as it was. */
	private void verifyUnchangedAssociations(SerRepository repo) throws IOException {
		Set<Association.Op> dirty = Collections.newSetFromMap(new IdentityHashMap<>());
		dirty.addAll(repo.getDirtyAssociations());
		for (Association.Op association : repo.getAssociations()) {
			if (dirty.contains(association))
				continue;
			byte[] stored = this.verifiedAssociationDigests.get(association.getId());
			if (stored == null)
				throw new IllegalStateException("Association " + association.getId() + " is neither stored nor marked to be written (Repository.Op#addAssociation).");
			if (!Arrays.equals(stored, digest(serializeAssociation(association, TreePieces.DIGEST_ONLY, this.repository))))
				throw new IllegalStateException("Association " + association.getId() + " changed without being marked to be written (Repository.Op#addAssociation) - the change would not be stored.");
		}
	}

	/** See {@link #verifiedAssociationDigests}: records [associations] as they serialize now. */
	private void recordVerifiedDigests(Collection<? extends Association> associations) throws IOException {
		for (Association association : associations)
			this.verifiedAssociationDigests.put(association.getId(), digest(serializeAssociation(association, TreePieces.DIGEST_ONLY, this.repository)));
	}
}
