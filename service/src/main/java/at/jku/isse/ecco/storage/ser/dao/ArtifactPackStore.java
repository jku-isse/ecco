package at.jku.isse.ecco.storage.ser.dao;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.storage.ser.artifact.SerArtifact;
import at.jku.isse.ecco.storage.ser.repository.SerRepository;
import at.jku.isse.ecco.tree.Node;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static at.jku.isse.ecco.storage.ser.dao.StorageFiles.DB_FILE_SUFFIX;
import static at.jku.isse.ecco.storage.ser.dao.StorageFiles.TMP_SUFFIX;
import static at.jku.isse.ecco.storage.ser.dao.StorageFiles.digest;
import static at.jku.isse.ecco.storage.ser.dao.StorageFiles.serialize;

/**
 * The artifacts' packs ({@code artifacts/pack-<writeId>.zip}, one per write, read in the order they
 * were written - a later pack's copy of an artifact is the current one): reading them, writing a
 * write's changed artifacts into a new one, compacting and deleting packs nothing is current in.
 */
final class ArtifactPackStore {

	private static final Logger LOGGER = Logger.getLogger(ArtifactPackStore.class.getName());

	/**
	 * Serialize the artifacts a write skips as unchanged anyway, and fail if one has changed - a
	 * change made without Artifact.Op#markModified() would otherwise not be stored. On in the tests
	 * (build.gradle); -Decco.verifyUnchangedArtifacts=true for a replay.
	 */
	static final boolean VERIFY_UNCHANGED_ARTIFACTS = Boolean.getBoolean("ecco.verifyUnchangedArtifacts");
	private static final String PACK_PREFIX = "pack-";
	private static final String PACK_SUFFIX = ".zip";
	/** Once a write would leave more packs than this, the smallest are merged down to half of it. */
	private static final int MAX_PACKS = 20;
	private static final String PACK_ARTIFACTS_ENTRY = "artifacts";
	private static final String PACK_DIGESTS_ENTRY = "digests";
	private static final int ARTIFACT_DIGEST_LENGTH = 16;

	private final Path dir;
	// the digest of each stored artifact, by its id - tells a write which artifacts it need not write
	// again (a path per artifact - built, hashed and compared for every artifact of every write - took
	// longer than the comparison it was for)
	private final Map<String, byte[]> artifactDigests = new HashMap<>();

	ArtifactPackStore(Path dir) {
		this.dir = dir;
	}

	/**
	 * Reads every current artifact of [repo] - before any association: the associations' nodes carry
	 * artifact ids, resolved against these. An artifact is read exactly once, however many
	 * associations reference it (the duplicated-shared-artifact bug: read per association, a shared
	 * artifact came back as copies with the same id).
	 */
	void load(SerRepository repo) throws IOException, ClassNotFoundException {
		Map<String, Artifact.Op<?>> loadedById = new HashMap<>();
		if (repo.hasLegacyArtifactIndex()) {
			// a core written before packs were ordered holds the index itself
			Map<String, List<String>> idsByPack = new LinkedHashMap<>();
			for (String artifactId : repo.legacyArtifactIds()) {
				String pack = repo.legacyArtifactPack(artifactId);
				if (pack == null) {
					StorageFiles.Read read = StorageFiles.readZipped(this.artifactFile(artifactId));
					loadedById.put(artifactId, (Artifact.Op<?>) read.object());
					this.artifactDigests.put(artifactId, Arrays.copyOf(read.digest(), ARTIFACT_DIGEST_LENGTH));
					repo.addLooseArtifact(artifactId);
				}
				else
					idsByPack.computeIfAbsent(pack, p -> new ArrayList<>()).add(artifactId);
			}
			for (Map.Entry<String, List<String>> pack : idsByPack.entrySet()) {
				this.readPack(this.dir.resolve(pack.getKey()), pack.getValue(), loadedById);
				for (String artifactId : pack.getValue())
					repo.setArtifactPack(artifactId, pack.getKey());
			}
		} else {
			// the packs in the order they were written: a later pack's copy is the current one
			for (String pack : repo.getPackOrder())
				this.readOrderedPack(pack, loadedById, repo);
		}
		List<Artifact.Op<?>> loadedArtifacts = new ArrayList<>(repo.getArtifactIds().size());
		for (String artifactId : repo.getArtifactIds())
			loadedArtifacts.add(loadedById.get(artifactId));
		repo.restoreArtifacts(loadedArtifacts);
	}

	/**
	 * Before a write: registers the artifacts of the associations it touched as dirty - artifacts
	 * have no add/remove choke point the way associations do, so the trees of the dirty associations
	 * are walked (more than changed, which the write's digests sort out).
	 */
	static void registerDirtyArtifacts(SerRepository repo) {
		for (Association association : repo.getDirtyAssociations())
			if (association.getRootNode() instanceof Node.Op rootNode)
				registerReachableArtifacts(rootNode, repo);
	}

	/** After the commit point: the artifacts just written are stored now (id -> digest). */
	void committed(Map<String, byte[]> staged) {
		this.artifactDigests.putAll(staged);
	}

	/** Forgets what was read and written - the next transaction reads everything again. */
	void clear() {
		this.artifactDigests.clear();
	}

	/**
	 * Reads every artifact of a pack (see {@link #writePack}) as the current one - the packs being read
	 * in the order they were written - recording where each is and its digest.
	 */
	private void readOrderedPack(String packName, Map<String, Artifact.Op<?>> loadedById, SerRepository repo) throws IOException, ClassNotFoundException {
		Path pack = this.dir.resolve(packName);
		if (!Files.exists(pack))
			throw new EccoException("The artifact pack " + packName + " is missing - the repository is damaged.");
		try (ZipFile zip = new ZipFile(pack.toFile())) {
			ZipEntry artifactsEntry = zip.getEntry(PACK_ARTIFACTS_ENTRY);
			if (artifactsEntry == null)
				throw new EccoException("The artifact pack " + packName + " has no " + PACK_ARTIFACTS_ENTRY + " - the repository is damaged.");
			byte[] digests = packDigests(zip, packName);
			try (ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(zip.getInputStream(artifactsEntry), 1 << 16))) {
				int count = ois.readInt();
				for (int i = 0; i < count; i++) {
					SerArtifact<?> artifact = (SerArtifact<?>) ois.readObject();
					String id = artifact.getStorageId();
					loadedById.put(id, artifact);
					repo.setArtifactPack(id, packName);
					this.recordDigest(id, digests, i);
				}
			}
		}
	}

	/**
	 * The digests of a pack's artifacts, or none if its {@value #PACK_DIGESTS_ENTRY} entry is missing.
	 * They only tell a later write which artifacts it need not write again: without them the
	 * artifacts are read all the same (a missing entry threw a NullPointerException), and written
	 * again by the next write.
	 */
	private static byte[] packDigests(ZipFile zip, String packName) throws IOException {
		ZipEntry entry = zip.getEntry(PACK_DIGESTS_ENTRY);
		if (entry == null) {
			LOGGER.warning("The artifact pack " + packName + " has no " + PACK_DIGESTS_ENTRY + " entry - its artifacts are written again by the next write.");
			return new byte[0];
		}
		try (InputStream in = zip.getInputStream(entry)) {
			return in.readAllBytes();
		}
	}

	/** Records the digest of the [index]th artifact of a pack, if [digests] has one for it. */
	private void recordDigest(String id, byte[] digests, int index) {
		int end = (index + 1) * ARTIFACT_DIGEST_LENGTH;
		if (end <= digests.length)
			this.artifactDigests.put(id, Arrays.copyOfRange(digests, end - ARTIFACT_DIGEST_LENGTH, end));
	}

	/** Reads the given artifacts from a pack (see {@link #writePack}), recording their digests. */
	private void readPack(Path pack, List<String> artifactIds, Map<String, Artifact.Op<?>> loadedById) throws IOException, ClassNotFoundException {
		if (!Files.exists(pack))
			throw new EccoException("The artifact pack " + pack.getFileName() + " is missing - the repository is damaged.");
		Set<String> wanted = new HashSet<>(artifactIds);
		try (ZipFile zip = new ZipFile(pack.toFile())) {
			ZipEntry artifactsEntry = zip.getEntry(PACK_ARTIFACTS_ENTRY);
			if (artifactsEntry != null) {
				byte[] digests = packDigests(zip, pack.getFileName().toString());
				try (ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(zip.getInputStream(artifactsEntry), 1 << 16))) {
					int count = ois.readInt();
					for (int i = 0; i < count; i++) {
						SerArtifact<?> artifact = (SerArtifact<?>) ois.readObject();
						String id = artifact.getStorageId();
						if (!wanted.contains(id))
							continue; // superseded by a later pack
						loadedById.put(id, artifact);
						this.recordDigest(id, digests, i);
					}
				}
			} else {
				// the first packs: one entry per artifact
				for (String id : artifactIds) {
					ZipEntry entry = zip.getEntry(id);
					if (entry == null)
						continue;
					byte[] bytes;
					try (InputStream in = zip.getInputStream(entry)) {
						bytes = in.readAllBytes();
					}
					this.artifactDigests.put(id, artifactDigest(bytes));
					try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
						loadedById.put(id, (Artifact.Op<?>) ois.readObject());
					}
				}
			}
		}
		for (String id : artifactIds)
			if (!loadedById.containsKey(id))
				throw new EccoException("Artifact " + id + " is missing from " + pack.getFileName() + " - the repository is damaged.");
	}

	/**
	 * Writes this transaction's changed artifacts into one new pack file - a zip holding one entry per
	 * artifact, named by its id, with the bytes its own file used to hold - and records them in the
	 * repository's pack index, which the core written afterwards persists. One file per artifact made
	 * a commit cost a file creation per artifact: 67 s for a 71,000 pixel image (142,000 artifacts),
	 * almost all of it file system calls, and half a second for a 1,000 line text file.
	 * <p>
	 * The pack gets its final name right away: until the id file swap nothing references it, so a
	 * crash leaves an unreferenced pack behind, which a later write deletes. Artifacts whose bytes did
	 * not change are not written (their digests: {@link #artifactDigests}). Packs of which less than
	 * half of the artifacts are still current are compacted: their current artifacts go into the new
	 * pack too, and the old pack is deleted once nothing references it; so are the smallest packs once
	 * there are more than {@link #MAX_PACKS}.
	 *
	 * @return the ids of artifacts with a file of their own, written into the new pack: their files go once committed
	 */
	Set<String> write(SerRepository repo, String newId, Map<String, byte[]> staged) throws IOException {
		if (!repo.getDirtyArtifacts().isEmpty())
			Files.createDirectories(this.dir);
		// the artifacts of the new pack, with the digest of each one's serialization - not the
		// serialization itself: held for every artifact of a repository converted to ordered packs
		// (below), that took as much memory as the artifacts' bytes, besides the artifacts
		Map<String, byte[]> entries = new LinkedHashMap<>();
		// whether each pack still exists, asked once per pack rather than once per artifact
		Map<String, Boolean> packExists = new HashMap<>();
		for (Artifact.Op<?> artifact : repo.getDirtyArtifacts()) {
			if (!(artifact instanceof SerArtifact<?> serArtifact)) continue;
			String id = serArtifact.getStorageId();
			// stored, not changed since it was loaded, and without an order graph (which changes
			// without the artifact knowing): cannot have changed - not serialized (gap #49)
			boolean unchanged = !serArtifact.isModified() && !serArtifact.isOrdered() && serArtifact.getPartialOrderGraph() == null
					&& this.artifactDigests.containsKey(id) && this.isArtifactStored(repo, id, packExists);
			if (unchanged && !VERIFY_UNCHANGED_ARTIFACTS)
				continue;
			byte[] bytes = serialize(artifact);
			byte[] digest = artifactDigest(bytes);
			if (Arrays.equals(digest, this.artifactDigests.get(id)) && this.isArtifactStored(repo, id, packExists))
				continue;
			if (unchanged)
				throw new IllegalStateException("An artifact changed without being marked modified (Artifact.Op#markModified): "
						+ artifact + " (" + artifact.getData().getClass().getName() + ")");
			entries.put(id, digest);
			staged.put(id, digest);
		}

		// a core written before packs were ordered: every current artifact goes into the new pack, so
		// that its packs need no order (see SerRepository.hasLegacyArtifactIndex()); the files of
		// artifacts that had one of their own go too
		Set<String> looseFiles = new HashSet<>();
		boolean legacy = repo.hasLegacyArtifactIndex();
		if (legacy) {
			for (String id : repo.getArtifactIds()) {
				if (repo.getArtifactPack(id) == null)
					looseFiles.add(id);
				if (!entries.containsKey(id) && repo.getArtifact(id) != null) {
					byte[] digest = artifactDigest(serialize(repo.getArtifact(id)));
					entries.put(id, digest);
					staged.put(id, digest);
				}
			}
		}

		// how many current artifacts each pack keeps once the new one supersedes those it holds
		Map<String, Integer> superseded = new HashMap<>();
		for (String id : entries.keySet()) {
			String pack = repo.getArtifactPack(id);
			if (pack != null)
				superseded.merge(pack, 1, Integer::sum);
		}
		Map<String, Integer> currentAfter = new HashMap<>();
		for (String pack : repo.getPackOrder())
			currentAfter.put(pack, repo.currentArtifacts(pack) - superseded.getOrDefault(pack, 0));
		List<String> kept = new ArrayList<>();
		for (String pack : repo.getPackOrder()) {
			int current = currentAfter.get(pack);
			if (current <= 0)
				continue;
			if (current * 2 >= repo.getPackSizes().getOrDefault(pack, current) || !this.repack(repo, this.stillCurrent(repo, pack, entries), entries, staged))
				kept.add(pack);
		}
		// every write adds a pack: once there are too many, the smallest are merged into the new one
		if (kept.size() + 1 > MAX_PACKS) {
			kept.sort(Comparator.comparingInt(currentAfter::get));
			for (String pack : kept.subList(0, kept.size() + 1 - MAX_PACKS / 2))
				this.repack(repo, this.stillCurrent(repo, pack, entries), entries, staged);
		}

		if (legacy)
			repo.dropLegacyArtifactIndex();
		if (!entries.isEmpty()) {
			String packName = PACK_PREFIX + newId + PACK_SUFFIX;
			writePack(repo, entries, this.dir.resolve(packName));
			for (String id : entries.keySet())
				repo.setArtifactPack(id, packName);
			repo.addPack(packName);
			repo.getPackSizes().put(packName, entries.size());
		}
		// packs nothing is current in anymore leave the order (their files go once committed)
		repo.dropEmptyPacks();
		return looseFiles;
	}

	/** The artifacts [pack] holds the current copy of that the new pack does not supersede. */
	private List<String> stillCurrent(SerRepository repo, String pack, Map<String, byte[]> entries) {
		List<String> ids = repo.currentArtifactsOf(pack);
		ids.removeIf(entries::containsKey);
		return ids;
	}

	/** Adds the given (current) artifacts of an old pack to the entries of the new one; false if one is not loaded. */
	private boolean repack(SerRepository repo, List<String> artifactIds, Map<String, byte[]> entries, Map<String, byte[]> staged) throws IOException {
		if (artifactIds.stream().anyMatch(id -> repo.getArtifact(id) == null))
			return false;
		for (String id : artifactIds) {
			byte[] digest = artifactDigest(serialize(repo.getArtifact(id)));
			entries.put(id, digest);
			staged.put(id, digest);
		}
		return true;
	}

	/**
	 * Writes a pack: entry {@value #PACK_ARTIFACTS_ENTRY} holds the artifacts in one serialization
	 * stream - class descriptions written once, not per artifact (4.6x smaller for pixels) - compressed
	 * at the fastest level (another 3-4x); entry {@value #PACK_DIGESTS_ENTRY} the digest of each
	 * artifact's own serialization in the same order, so that a later write can still tell unchanged
	 * artifacts without serializing everything it loaded. The first packs held one entry per artifact
	 * instead; they are still read.
	 */
	private static void writePack(SerRepository repo, Map<String, byte[]> entries, Path pack) throws IOException {
		Path tmpFile = pack.resolveSibling(pack.getFileName() + "." + UUID.randomUUID() + TMP_SUFFIX);
		try {
			try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(tmpFile, StandardOpenOption.CREATE_NEW), 1 << 16))) {
				zos.setLevel(Deflater.BEST_SPEED);
				zos.putNextEntry(new ZipEntry(PACK_ARTIFACTS_ENTRY));
				// serialized straight into the compressed entry: it was serialized into memory first, a
				// second copy of every artifact's bytes - of all of them when a repository was converted
				ObjectOutputStream out = new ObjectOutputStream(new FilterOutputStream(zos) {
					@Override
					public void write(byte[] b, int off, int len) throws IOException {
						this.out.write(b, off, len);
					}

					@Override
					public void close() throws IOException {
						this.flush(); // the zip entry stays open
					}
				});
				out.writeInt(entries.size());
				for (String id : entries.keySet())
					out.writeObject(repo.getArtifact(id));
				out.close();
				zos.closeEntry();
				zos.putNextEntry(new ZipEntry(PACK_DIGESTS_ENTRY));
				for (byte[] digest : entries.values())
					zos.write(digest);
				zos.closeEntry();
			}
			Files.move(tmpFile, pack, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} finally {
			// a write that failed (e.g. out of memory or disk space) left it behind for good
			Files.deleteIfExists(tmpFile);
		}
	}

	/** A digest of an artifact's own serialization - the first half of its SHA-256, to keep packs small. */
	private static byte[] artifactDigest(byte[] bytes) {
		return Arrays.copyOf(digest(bytes), ARTIFACT_DIGEST_LENGTH);
	}

	/** The file an artifact had of its own before packs - also the key of its digest. */
	private Path artifactFile(String artifactId) {
		return this.dir.resolve(artifactId + DB_FILE_SUFFIX);
	}

	private boolean isArtifactStored(SerRepository repo, String artifactId, Map<String, Boolean> packExists) {
		String pack = repo.getArtifactPack(artifactId);
		if (pack == null)
			return Files.exists(this.artifactFile(artifactId));
		return packExists.computeIfAbsent(pack, p -> Files.exists(this.dir.resolve(p)));
	}

	/**
	 * After a committed write: deletes the files of the artifacts just packed that still had a file
	 * of their own, and every pack nothing is in anymore (also one left by a crashed write). Only
	 * called when no reader holds the previous core, which may still reference them.
	 */
	void deleteSupersededArtifactFiles(SerRepository repo, Set<String> looseFiles) throws IOException {
		for (String id : looseFiles)
			Files.deleteIfExists(this.artifactFile(id));
		if (!Files.isDirectory(this.dir))
			return;
		Set<String> referenced = new HashSet<>(repo.getPackOrder());
		try (DirectoryStream<Path> packs = Files.newDirectoryStream(this.dir, PACK_PREFIX + "*" + PACK_SUFFIX)) {
			for (Path pack : packs) {
				if (!referenced.contains(pack.getFileName().toString()))
					Files.deleteIfExists(pack);
			}
		}
		// packs being written when a write crashed (none is being written now: this write holds the lock)
		try (DirectoryStream<Path> unfinished = Files.newDirectoryStream(this.dir, PACK_PREFIX + "*" + PACK_SUFFIX + ".*" + TMP_SUFFIX)) {
			for (Path pack : unfinished)
				Files.deleteIfExists(pack);
		}
	}

	private static void registerReachableArtifacts(Node.Op node, SerRepository repo) {
		if (node.getArtifact() != null) {
			repo.registerArtifact(node.getArtifact());
		}
		for (Node.Op child : node.getChildren()) {
			registerReachableArtifacts(child, repo);
		}
	}
}
