package at.jku.isse.ecco.storage.ser.dao;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.artifact.ArtifactReference;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.counter.ModuleCounter;
import at.jku.isse.ecco.counter.ModuleRevisionCounter;
import at.jku.isse.ecco.dao.TransactionStrategy;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.module.ModuleRevision;
import at.jku.isse.ecco.pog.PartialOrderGraph;
import at.jku.isse.ecco.storage.common.dao.Database;
import at.jku.isse.ecco.storage.ser.artifact.SerArtifact;
import at.jku.isse.ecco.storage.ser.artifact.SerArtifactReference;
import at.jku.isse.ecco.storage.ser.core.SerCommit;
import at.jku.isse.ecco.storage.ser.counter.CompactCounters;
import at.jku.isse.ecco.storage.ser.counter.SerAssociationCounter;
import at.jku.isse.ecco.storage.ser.counter.SerModuleCounter;
import at.jku.isse.ecco.storage.ser.counter.SerModuleRevisionCounter;
import at.jku.isse.ecco.storage.ser.module.SerModule;
import at.jku.isse.ecco.storage.ser.module.SerModuleRevision;
import at.jku.isse.ecco.storage.ser.pog.SerPartialOrderGraphNode;
import at.jku.isse.ecco.storage.ser.repository.SerRepository;
import at.jku.isse.ecco.storage.ser.tree.SerNode;
import at.jku.isse.ecco.tree.Node;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import java.util.Comparator;
import java.util.Set;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.nio.file.DirectoryStream;
import java.util.zip.ZipFile;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static com.google.common.base.Preconditions.checkNotNull;

@Singleton
public class SerTransactionStrategy implements TransactionStrategy {

	private static final boolean DELETE_OLD_DB_FILES = true;
	private static final boolean REUSE_DB_ACROSS_TRANSACTIONS = true;

	private static final String ID_FILENAME = "id";
	private static final String WRITELOCK_FILENAME = "write";
	private static final String DB_FILE_SUFFIX = ".ser.zip";
	private static final String ASSOCIATIONS_DIRNAME = "associations";
	private static final String ARTIFACTS_DIRNAME = "artifacts";
	private static final String PACK_PREFIX = "pack-";
	private static final String PACK_SUFFIX = ".zip";
	/** Once a write would leave more packs than this, the smallest are merged down to half of it. */
	private static final int MAX_PACKS = 20;
	private static final String ZIP_ENTRY_NAME = "ecco.ser";
	private static final String PENDING_SUFFIX = ".pending";

	// repository directory
	private final Path repositoryDir;
	// file containing the current database id
	private final Path idFile;
	// lock file for making sure there is onyl one write transaction going on at a time
	private final Path writeLockFile;
	// one file per association lives here - see the class javadoc on SerCommit for why this split exists
	private final Path associationsDir;
	// one file per artifact lives here - see SerNode.artifactId's javadoc for why this split exists
	private final Path artifactsDir;

	// id of currently loaded database file
	private String id;
	// database file
	private Path dbFile;
	// currently loaded database object
	private Database database;
	// type of current transaction
	private TRANSACTION transaction;
	// number of begin transaction calls
	private int transactionCounter;
	// write file channel
	private FileChannel writeFileChannel;
	// write file lock
	private FileLock writeFileLock;
	// SHA-256 of the serialized bytes of every stable artifact/association file as last read or
	// written by this process - lets endReadWrite() skip entities whose bytes didn't change. Cleared
	// by reset(); rebuilt by the full reload that follows.
	private final Map<Path, byte[]> persistedDigests = new HashMap<>();


	@Inject
	public SerTransactionStrategy(@Named("repositoryDir") final Path repositoryDir) {
		checkNotNull(repositoryDir);
		this.repositoryDir = repositoryDir;
		this.idFile = repositoryDir.resolve(ID_FILENAME);
		this.writeLockFile = repositoryDir.resolve(WRITELOCK_FILENAME);
		this.associationsDir = repositoryDir.resolve(ASSOCIATIONS_DIRNAME);
		this.artifactsDir = repositoryDir.resolve(ARTIFACTS_DIRNAME);
		this.reset();
	}

	/**
	 * Serializes object as a STORED (uncompressed) zip entry at file - see the comment in
	 * endReadWrite() for why STORED rather than the default DEFLATE compression.
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
	private static void writeStored(Object object, Path file) throws IOException {
		writeStoredBytes(serialize(object), file);
	}

	private static byte[] serialize(Object object) throws IOException {
		ByteArrayOutputStream serialized = new ByteArrayOutputStream();
		try (ObjectOutputStream oos = new ObjectOutputStream(serialized)) {
			oos.writeObject(object);
		}
		return serialized.toByteArray();
	}

	/** Like {@link #serialize}, with the association's counters in compact form - see {@link CompactCounters}. */
	private static byte[] serializeAssociation(Object association) throws IOException {
		ByteArrayOutputStream serialized = new ByteArrayOutputStream();
		try (ObjectOutputStream oos = new CompactCounters.Stream(serialized)) {
			oos.writeObject(association);
		}
		return serialized.toByteArray();
	}

	private static byte[] digest(byte[] bytes) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(bytes);
		} catch (NoSuchAlgorithmException e) {
			throw new EccoException("SHA-256 not available.", e);
		}
	}

	/**
	 * Stages a dirty entity's file for the transaction tagged {@code pendingSuffix} (see
	 * {@link #endReadWrite()}) - unless its serialized bytes are identical to the stable file already
	 * on disk (known from {@link #persistedDigests}), in which case nothing is written at all. Most
	 * "dirty" entities of a commit are unchanged: Trees.slice() re-slices every touched association
	 * against the whole working tree, so far more is marked dirty than actually differs, and writing
	 * one file per artifact dominated commit time (see UnchangedArtifactRewriteTest).
	 */
	private void stageIfChanged(Object entity, Path stableFile, String pendingSuffix, Map<Path, byte[]> staged) throws IOException {
		byte[] bytes = entity instanceof Association ? serializeAssociation(entity) : serialize(entity);
		byte[] digest = digest(bytes);
		if (Arrays.equals(digest, this.persistedDigests.get(stableFile)) && Files.exists(stableFile))
			return;
		writeStoredBytes(bytes, stableFile.resolveSibling(stableFile.getFileName() + pendingSuffix));
		staged.put(stableFile, digest);
	}

	private static void writeStoredBytes(byte[] serializedBytes, Path file) throws IOException {
		CRC32 crc32 = new CRC32();
		crc32.update(serializedBytes);
		Path tmpFile = file.resolveSibling(file.getFileName() + "." + UUID.randomUUID() + ".tmp");
		try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(tmpFile, StandardOpenOption.CREATE))) {
			ZipEntry entry = new ZipEntry(ZIP_ENTRY_NAME);
			entry.setMethod(ZipEntry.STORED);
			entry.setSize(serializedBytes.length);
			entry.setCompressedSize(serializedBytes.length);
			entry.setCrc(crc32.getValue());
			zos.putNextEntry(entry);
			zos.write(serializedBytes);
		}
		try {
			Files.move(tmpFile, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			Files.deleteIfExists(tmpFile);
			throw e;
		}
	}

	/**
	 * Suffix of a dirty association/artifact file staged by {@link #endReadWrite()} for the
	 * transaction that will make {@code txId} the current core id - see {@link #endReadWrite()}.
	 */
	private static String pendingSuffix(String txId) {
		return "." + txId + PENDING_SUFFIX;
	}

	/**
	 * Resolves the staged files of an interrupted-but-committed transaction and discards those of
	 * aborted ones, in both per-entity directories - see {@link #endReadWrite()} for the protocol.
	 * <p>
	 * Pending files tagged with {@code currentId} belong to a transaction whose id-file swap (its
	 * commit point) already happened, so they are the authoritative versions and are renamed over
	 * their stable names (roll forward). Any other pending file belongs to a transaction that never
	 * reached its swap; those are only deleted when {@code discardAborted} is set, i.e. when the
	 * caller holds the exclusive write lock - otherwise they might be the in-flight files of a
	 * concurrent writer in another process, which must not be touched.
	 */
	private void recoverPendingFiles(String currentId, boolean discardAborted) throws IOException {
		for (Path dir : List.of(this.associationsDir, this.artifactsDir)) {
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

	/**
	 * Like {@link #readZipped}, and remembers the digest of the serialized bytes, so an unchanged
	 * entity isn't written again (see {@link #stageIfChanged}).
	 */
	private Object readZippedTracked(Path file) throws IOException, ClassNotFoundException {
		try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
			ZipEntry e;
			while ((e = zis.getNextEntry()) != null) {
				if (e.getName().equals(ZIP_ENTRY_NAME)) {
					byte[] bytes = zis.readAllBytes();
					this.persistedDigests.put(file, digest(bytes));
					try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
						return ois.readObject();
					}
				}
			}
		}
		throw new EccoException("No " + ZIP_ENTRY_NAME + " entry found in " + file);
	}

	/** Reads the given artifacts from a pack (see {@link #writeArtifactPack}), recording their digests. */
	private void readPack(Path pack, List<String> artifactIds, Map<String, Artifact.Op<?>> loadedById) throws IOException, ClassNotFoundException {
		if (!Files.exists(pack))
			throw new EccoException("The artifact pack " + pack.getFileName() + " is missing - the repository is damaged.");
		try (ZipFile zip = new ZipFile(pack.toFile())) {
			for (String id : artifactIds) {
				ZipEntry entry = zip.getEntry(id);
				if (entry == null)
					throw new EccoException("Artifact " + id + " is missing from " + pack.getFileName() + " - the repository is damaged.");
				byte[] bytes;
				try (InputStream in = zip.getInputStream(entry)) {
					bytes = in.readAllBytes();
				}
				this.persistedDigests.put(this.artifactFile(id), digest(bytes));
				try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
					loadedById.put(id, (Artifact.Op<?>) ois.readObject());
				}
			}
		}
	}

	private static Object readZipped(Path file) throws IOException, ClassNotFoundException {
		try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
			ZipEntry e;
			while ((e = zis.getNextEntry()) != null) {
				if (e.getName().equals(ZIP_ENTRY_NAME)) {
					try (ObjectInputStream ois = new ObjectInputStream(zis)) {
						return ois.readObject();
					}
				}
			}
		}
		throw new EccoException("No " + ZIP_ENTRY_NAME + " entry found in " + file);
	}


	public Database getDatabase() {
		return this.database;
	}

	public TRANSACTION getTransaction() {
		return this.transaction;
	}


	@Override
	public synchronized void open() {
		this.reset();
	}

	/** The id file is written by init() and names the current core file - no id file, no repository. */
	@Override
	public boolean containsRepository() {
		return Files.exists(this.idFile);
	}

	@Override
	public synchronized void close() {
		if (this.transaction != null || this.transactionCounter != 0)
			throw new EccoException("Error closing connection: Not all transactions have been ended.");
		this.reset();
	}

	@Override
	public synchronized void rollback() {
		if (this.transaction == null && this.transactionCounter == 0)
			throw new EccoException("Error rolling back transaction: No transaction active.");
		this.reset();
	}


	@Override
	public synchronized void begin(TRANSACTION transaction) {
		try {
			if (transaction == TRANSACTION.READ_ONLY)
				this.beginReadOnly();
			else if (transaction == TRANSACTION.READ_WRITE)
				this.beginReadWrite();
			this.transactionCounter++;
		} catch (IOException | ClassNotFoundException e) {
			throw new EccoException("Error beginning transaction.", e);
		}
	}


	/**
	 * Ends a transaction.
	 */
	@Override
	public synchronized void end() {
		if (this.transaction == null || this.transactionCounter <= 0)
			throw new EccoException("There is no active transaction.");

		this.transactionCounter--;
		if (this.transactionCounter == 0) {
			try {
				if (this.transaction == TRANSACTION.READ_ONLY)
					this.endReadOnly();
				else if (this.transaction == TRANSACTION.READ_WRITE)
					this.endReadWrite();
			} catch (IOException e) {
				throw new EccoException("Error ending transaction.", e);
			}
		}
	}


	private void beginReadOnly() throws IOException, ClassNotFoundException {
		if (this.transaction == TRANSACTION.READ_ONLY) // nothing to do, we already have a read transaction going
			return;

		if (this.transaction == null)
			this.transaction = TRANSACTION.READ_ONLY;

		this.loadDatabase();
	}

	private void endReadOnly() {
		this.transaction = null;
	}


	private void beginReadWrite() throws IOException, ClassNotFoundException {
		if (this.transaction == TRANSACTION.READ_ONLY)
			throw new EccoException("Cannot elevate a read only transaction to a read write transaction.");

		if (this.transaction == TRANSACTION.READ_WRITE) // nothing to do, we already have a read/write transaction going
			return;

		// obtain exclusive write lock
		this.writeFileChannel = FileChannel.open(this.writeLockFile, StandardOpenOption.WRITE, StandardOpenOption.CREATE);
		this.writeFileLock = this.writeFileChannel.lock(0, Long.MAX_VALUE, false);
		if (!this.writeFileLock.isValid())
			throw new EccoException("Could not obtain exclusive lock on WRITE file.");

		this.transaction = TRANSACTION.READ_WRITE;

		this.loadDatabase();
	}

	/**
	 * Persists the transaction with a small roll-forward journal, so that a failure or crash at any
	 * point leaves either the old or the new state loadable, never a mix of both:
	 * <ol>
	 * <li>every dirty artifact/association is written to {@code <stable name>.<newId>.pending}, never
	 * over its stable file - those are still what the current core loads;</li>
	 * <li>the new core is written to {@code <newId>.ser.zip};</li>
	 * <li>the id file is switched to {@code newId} - the commit point;</li>
	 * <li>the pending files are renamed over their stable names.</li>
	 * </ol>
	 * Dirty entities that already existed used to be rewritten in place in step 1, so failing before
	 * step 3 left the old core loading some new files (e.g. an artifact whose containing node or POG
	 * now referenced things only the new core knows about) - an unopenable repository. See
	 * SerTransactionStrategyInterruptedCommitTest. A failure before step 3 now just leaves pending
	 * files behind, discarded by the next writer; a failure during step 4 is finished by the next
	 * {@link #loadDatabase()}.
	 */
	private void endReadWrite() throws IOException {
		// check if we still have exclusive write lock and take it
		if (!this.writeFileLock.isValid())
			throw new EccoException("Lost exclusive lock on WRITE file.");

		SerRepository repo = (SerRepository) this.database.getRepository();

		// discover this transaction's dirty artifacts by walking the trees of associations already
		// known to be dirty - artifacts have no add/remove choke point the way associations do
		// (SerEntityFactory.createArtifact() doesn't register with a repository at all), so this
		// tree walk is the closest equivalent, reusing work we're about to do anyway (writing those
		// same associations below). May register some artifacts that didn't actually change this
		// commit (anything reachable from a dirty association, not just what's new) - harmless,
		// same over-inclusive-but-safe tradeoff associations' own dirty-tracking already makes.
		for (Association association : repo.getDirtyAssociations()) {
			if (association.getRootNode() instanceof Node.Op rootNode) {
				this.registerReachableArtifacts(rootNode, repo);
			}
		}

		// write dirty artifacts before dirty associations: an association's nodes only carry
		// artifact IDs now (see SerNode.artifactId's javadoc), so on a fresh load the artifact files
		// need to already exist for the resolution pass to find - writing them first is not itself
		// required for crash-safety (nothing points at them until the id-file swap below, same as
		// associations), just keeps the two writes in the same order load reads them back in.
		if (!repo.getDirtyArtifacts().isEmpty()) {
			Files.createDirectories(this.artifactsDir);
		}
		// compute the new core id up front: every dirty file below is staged under a name tagged with
		// it, so that the id-file swap further down is the single commit point for the whole
		// transaction (see the javadoc on this method)
		String newId = UUID.randomUUID().toString();
		String pendingSuffix = pendingSuffix(newId);
		// discard leftovers of earlier transactions that failed before their swap - safe here, we
		// hold the exclusive write lock, so no other writer can have files in flight
		this.recoverPendingFiles(this.id, true);

		Map<Path, byte[]> staged = new HashMap<>();
		Set<String> packedArtifactIds = this.writeArtifactPack(repo, newId, staged);

		// write only the associations actually touched this transaction, one file each, rather
		// than the whole database - most associations are untouched by any given commit but were,
		// before this, being fully reserialized every single time anyway. See the class javadoc on
		// SerCommit for why commits/the repository hold association IDs rather than direct
		// references (that's what makes it safe to leave everything else out of the "core" write
		// below). Staged under pending names like the artifacts above, never over the stable files
		// the still-current core loads.
		if (!repo.getDirtyAssociations().isEmpty()) {
			Files.createDirectories(this.associationsDir);
		}
		for (Association association : repo.getDirtyAssociations()) {
			this.stageIfChanged(association, this.associationsDir.resolve(association.getId() + DB_FILE_SUFFIX), pendingSuffix, staged);
		}

		// serialize to new db file
		Path newDbFile = this.repositoryDir.resolve(newId + DB_FILE_SUFFIX);
		//this.serialize(this.database, newDbFile);
		//
		// serialized first to a byte array, then written as a STORED (uncompressed) zip entry,
		// rather than streaming directly into a DEFLATE-compressed entry as before: measured on a
		// real ~40MB repository, DEFLATE compression (even at level 0, which still runs the deflate
		// algorithm, just with minimal effort) was 10-15s of a ~16.6s total, vs. ~0.1s to write the
		// same (uncompressed, ~5x larger) bytes with no compression at all - raw disk I/O was never
		// the bottleneck, compression was. STORED entries require the size/CRC32 to be known
		// upfront, which is why this needs the intermediate byte array. Trades disk space (the
		// larger, uncompressed on-disk size) for a ~3x faster commit on large repositories. The read
		// path (loadDatabase() below) needs no changes - ZipInputStream decompresses transparently
		// regardless of which method an entry was written with, so older, DEFLATE-compressed
		// database files remain fully readable.
		//
		// this "core" write is now cheap regardless of repository size: SerRepository.associations
		// and SerCommit.associations are both ID-only now (see their javadocs), so the only things
		// actually reachable from `database` here are IDs, commit/feature/module metadata, and
		// similar - not the (large, POG-heavy) association trees themselves.
		writeStored(this.database, newDbFile);

		// whether no reader holds the previous core anymore - only then may files it references go
		boolean oldCoreReleased = false;
		// obtain exclusive lock on id file, write new id, update current id and db file, release lock
		try (FileChannel idFileChannel = FileChannel.open(this.idFile, StandardOpenOption.WRITE, StandardOpenOption.CREATE); FileLock idFileLock = idFileChannel.lock(0, Long.MAX_VALUE, false)) {
			if (!idFileLock.isValid())
				throw new EccoException("Could not obtain exclusive lock on ID file.");

			// write new id to id file
			idFileChannel.write(ByteBuffer.wrap(newId.getBytes(Charset.defaultCharset())));

			// delete old db file if nobody has a shared lock anymore (i.e. if we can get an exclusive lock on it)
			if (this.dbFile != null) {
				try (FileChannel oldDbFileChannel = FileChannel.open(this.dbFile, StandardOpenOption.WRITE); FileLock oldDbFileLock = oldDbFileChannel.lock(0, Long.MAX_VALUE, false)) {
					if (oldDbFileLock.isValid()) {
						Files.delete(this.dbFile);
						oldCoreReleased = true;
					}
				}
			} else {
				oldCoreReleased = true;
			}

			// update id and db file
			this.id = newId;
			this.dbFile = newDbFile;

			// release exclusive id lock automatically when exiting try block
		}

		// the swap above committed the transaction: move its staged files over their stable names.
		// If this fails or the process dies part way, the next load finishes it (loadDatabase()).
		this.recoverPendingFiles(newId, false);
		this.persistedDigests.putAll(staged);

		// best-effort cleanup of association files no longer referenced by the now-current core -
		// after the id-file swap above, so a failure here never leaves the repository in a state
		// where the current core references a file that got deleted
		for (String removedId : repo.getRemovedAssociationIds()) {
			Path removedFile = this.associationsDir.resolve(removedId + DB_FILE_SUFFIX);
			Files.deleteIfExists(removedFile);
			this.persistedDigests.remove(removedFile);
		}
		if (oldCoreReleased)
			this.deleteSupersededArtifactFiles(repo, packedArtifactIds);
		repo.clearDirtyTracking();

		// release exclusive write lock - via releaseWriteLock() rather than closing directly, so the
		// fields are nulled too; otherwise the next reset() (e.g. close()) tried to release the
		// already-closed lock again and printed "Error releasing write lock: null" after every write
		this.releaseWriteLock();

		this.transaction = null;
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
	 * not change are not written (as before, via {@link #persistedDigests}). Packs of which less than
	 * half of the artifacts are still current are compacted: their current artifacts go into the new
	 * pack too, and the old pack is deleted once nothing references it; so are the smallest packs once
	 * there are more than {@link #MAX_PACKS}.
	 *
	 * @return the ids of the artifacts written into the new pack
	 */
	private Set<String> writeArtifactPack(SerRepository repo, String newId, Map<Path, byte[]> staged) throws IOException {
		Map<String, byte[]> entries = new LinkedHashMap<>();
		for (Artifact.Op<?> artifact : repo.getDirtyArtifacts()) {
			if (!(artifact instanceof SerArtifact<?> serArtifact)) continue;
			String id = serArtifact.getStorageId();
			byte[] bytes = serialize(artifact);
			byte[] digest = digest(bytes);
			Path key = this.artifactFile(id);
			if (Arrays.equals(digest, this.persistedDigests.get(key)) && this.isArtifactStored(repo, id))
				continue;
			entries.put(id, bytes);
			staged.put(key, digest);
		}

		Map<String, List<String>> currentByPack = new HashMap<>();
		for (Map.Entry<String, String> packed : repo.getArtifactPacks().entrySet()) {
			if (!entries.containsKey(packed.getKey()))
				currentByPack.computeIfAbsent(packed.getValue(), pack -> new ArrayList<>()).add(packed.getKey());
		}
		List<String> kept = new ArrayList<>();
		for (Map.Entry<String, Integer> pack : repo.getPackSizes().entrySet()) {
			List<String> current = currentByPack.getOrDefault(pack.getKey(), List.of());
			if (current.isEmpty())
				continue;
			if (current.size() * 2 >= pack.getValue() || !this.repack(repo, current, entries, staged))
				kept.add(pack.getKey());
		}
		// every write adds a pack: once there are too many, the smallest are merged into the new one
		if (kept.size() + 1 > MAX_PACKS) {
			kept.sort(Comparator.comparingInt(pack -> currentByPack.get(pack).size()));
			for (String pack : kept.subList(0, kept.size() + 1 - MAX_PACKS / 2))
				this.repack(repo, currentByPack.get(pack), entries, staged);
		}

		if (!entries.isEmpty()) {
			String packName = PACK_PREFIX + newId + PACK_SUFFIX;
			writePack(entries, this.artifactsDir.resolve(packName));
			for (String id : entries.keySet())
				repo.getArtifactPacks().put(id, packName);
			repo.getPackSizes().put(packName, entries.size());
		}
		// forget the sizes of packs nothing is in anymore (their files go once committed)
		Set<String> referenced = new HashSet<>(repo.getArtifactPacks().values());
		repo.getPackSizes().keySet().retainAll(referenced);
		return entries.keySet();
	}

	/** Adds the given (current) artifacts of an old pack to the entries of the new one; false if one is not loaded. */
	private boolean repack(SerRepository repo, List<String> artifactIds, Map<String, byte[]> entries, Map<Path, byte[]> staged) throws IOException {
		if (artifactIds.stream().anyMatch(id -> repo.getArtifact(id) == null))
			return false;
		for (String id : artifactIds) {
			byte[] bytes = serialize(repo.getArtifact(id));
			entries.put(id, bytes);
			staged.put(this.artifactFile(id), digest(bytes));
		}
		return true;
	}

	private static void writePack(Map<String, byte[]> entries, Path pack) throws IOException {
		Path tmpFile = pack.resolveSibling(pack.getFileName() + "." + UUID.randomUUID() + ".tmp");
		try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(tmpFile, StandardOpenOption.CREATE_NEW)))) {
			for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
				byte[] bytes = entry.getValue();
				CRC32 crc32 = new CRC32();
				crc32.update(bytes);
				ZipEntry zipEntry = new ZipEntry(entry.getKey());
				zipEntry.setMethod(ZipEntry.STORED);
				zipEntry.setSize(bytes.length);
				zipEntry.setCompressedSize(bytes.length);
				zipEntry.setCrc(crc32.getValue());
				zos.putNextEntry(zipEntry);
				zos.write(bytes);
				zos.closeEntry();
			}
		}
		Files.move(tmpFile, pack, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
	}

	/** The file an artifact had of its own before packs - also the key of its digest. */
	private Path artifactFile(String artifactId) {
		return this.artifactsDir.resolve(artifactId + DB_FILE_SUFFIX);
	}

	private boolean isArtifactStored(SerRepository repo, String artifactId) {
		String pack = repo.getArtifactPacks().get(artifactId);
		return Files.exists(pack != null ? this.artifactsDir.resolve(pack) : this.artifactFile(artifactId));
	}

	/**
	 * After a committed write: deletes the files of the artifacts just packed that still had a file
	 * of their own, and every pack nothing is in anymore (also one left by a crashed write). Only
	 * called when no reader holds the previous core, which may still reference them.
	 */
	private void deleteSupersededArtifactFiles(SerRepository repo, Set<String> packedArtifactIds) throws IOException {
		for (String id : packedArtifactIds)
			Files.deleteIfExists(this.artifactFile(id));
		if (!Files.isDirectory(this.artifactsDir))
			return;
		Set<String> referenced = new HashSet<>(repo.getArtifactPacks().values());
		try (DirectoryStream<Path> packs = Files.newDirectoryStream(this.artifactsDir, PACK_PREFIX + "*" + PACK_SUFFIX)) {
			for (Path pack : packs) {
				if (!referenced.contains(pack.getFileName().toString()))
					Files.deleteIfExists(pack);
			}
		}
	}

	private void registerReachableArtifacts(Node.Op node, SerRepository repo) {
		if (node.getArtifact() != null) {
			repo.registerArtifact(node.getArtifact());
		}
		for (Node.Op child : node.getChildren()) {
			this.registerReachableArtifacts(child, repo);
		}
	}


	private void reset() {
		this.persistedDigests.clear();
		this.id = null;
		this.dbFile = null;
		this.database = null;
		this.transaction = null;
		this.transactionCounter = 0;
		this.releaseWriteLock();
	}

	/**
	 * Releases the exclusive write lock acquired in {@link #beginReadWrite()}, if one is currently
	 * held - needed here (not just in {@link #endReadWrite()}'s success path) because
	 * {@link #rollback()}/{@link #close()} also call {@link #reset()} while a READ_WRITE
	 * transaction may still be holding it. Previously {@link #reset()} just nulled out
	 * writeFileChannel/writeFileLock without releasing them, leaking the OS-level lock: every
	 * subsequent {@code begin(READ_WRITE)} attempt for the rest of the process's lifetime then
	 * threw {@code OverlappingFileLockException}, permanently write-locking the repository after
	 * any failed write transaction (e.g. a single failed commit() making a running session unable
	 * to ever commit again without restarting). Best-effort: a failure releasing/closing must not
	 * prevent the rest of {@link #reset()}'s state cleanup from completing.
	 */
	private void releaseWriteLock() {
		if (this.writeFileLock != null) {
			try {
				this.writeFileLock.close();
			} catch (IOException e) {
				System.err.println("Error releasing write lock: " + e.getMessage());
			}
		}
		if (this.writeFileChannel != null) {
			try {
				this.writeFileChannel.close();
			} catch (IOException e) {
				System.err.println("Error closing write file channel: " + e.getMessage());
			}
		}
		this.writeFileChannel = null;
		this.writeFileLock = null;
	}

	private String readCurrentId() throws IOException {
		// get shared lock on id file, read id, release lock, return it
		try (RandomAccessFile ras = new RandomAccessFile(this.idFile.toFile(), "r"); FileChannel fileChannel = ras.getChannel(); FileLock fileLock = fileChannel.lock(0, Long.MAX_VALUE, true)) {
			if (!fileLock.isValid())
				throw new EccoException("Could not obtain shared lock on ID file.");

			return ras.readLine();
		}
	}

	private void loadDatabase() throws IOException, ClassNotFoundException {
		// check if id file exists
		if (Files.exists(this.idFile)) {
			String id = this.readCurrentId();
			// check if this.id has changed or if this.dbFile has already been loaded before. if it has then do not load it again and just reuse this.database.)
			if (REUSE_DB_ACROSS_TRANSACTIONS && this.id != null && this.id.equals(id))
				return;
			this.id = id;

			Path dbFile = this.repositoryDir.resolve(this.id + DB_FILE_SUFFIX);
			if (Files.exists(dbFile)) {
				this.dbFile = dbFile;
				try (FileChannel dbFileChannel = FileChannel.open(this.dbFile, StandardOpenOption.READ); FileLock dbFileLock = dbFileChannel.lock(0, Long.MAX_VALUE, true)) {
					if (!dbFileLock.isValid())
						throw new EccoException("Could not obtain shared lock on DB file.");

					//this.database = (Database) this.deserialize(this.dbFile);
					InputStream is = new BufferedInputStream(Channels.newInputStream(dbFileChannel));
					ZipInputStream zis = new ZipInputStream(is);
					ZipEntry e = null;
					Database loaded = null;
					while ((e = zis.getNextEntry()) != null) {
						if (e.getName().equals(ZIP_ENTRY_NAME)) {
							ObjectInputStream ois = new ObjectInputStream(zis);
							loaded = (Database) ois.readObject();
							break;
						}
					}
					// used to leave the previous (or no) database in place and fail later with an NPE
					if (loaded == null)
						throw new EccoException("Repository core file " + dbFile + " is damaged: it has no " + ZIP_ENTRY_NAME + " entry.");
					this.database = loaded;
				}

				// delete db file if we can get exclusive lock and it does not match id file
				if (DELETE_OLD_DB_FILES) {
					String currentId = this.readCurrentId();
					Path currentDbFile = this.repositoryDir.resolve(currentId + DB_FILE_SUFFIX);
					if (!currentDbFile.equals(dbFile)) {
						// try to delete db file
						try (FileChannel oldDbFileChannel = FileChannel.open(dbFile, StandardOpenOption.WRITE); FileLock oldDbFileLock = oldDbFileChannel.lock(0, Long.MAX_VALUE, false)) {
							if (oldDbFileLock.isValid())
								Files.delete(dbFile);
						}
					}
				}
			} else {
				throw new EccoException("DB file does not exist: " + dbFile);
			}
		} else {
			this.database = new Database();
		}

		SerRepository repo = (SerRepository) this.database.getRepository();

		// repositories written before associations (520155c1) and then artifacts (d92c439e) got their
		// own files deserialize without these id sets. Their embedded content lives in fields that are
		// transient now, so they can't be migrated here - say so instead of failing with an NPE.
		if (repo.isOldStorageFormat()) {
			throw new EccoException("This repository (" + this.repositoryDir + ") was written in an older storage format "
					+ "(before " + (repo.hasAssociationIds() ? "artifacts" : "associations") + " were stored in their own files) "
					+ "that this version of ECCO cannot read. Open it with the ECCO version that created it to export its variants.");
		}

		// finish a committed transaction whose staged files weren't all moved into place yet (see
		// endReadWrite()); pending files of aborted transactions are only discarded while holding
		// the write lock, as they might belong to a concurrent writer otherwise
		this.recoverPendingFiles(this.id, this.transaction == TRANSACTION.READ_WRITE && this.writeFileLock != null);

		// load every artifact from its own file BEFORE any association - association trees' nodes
		// only carry an artifactId now (see SerNode.artifactId's javadoc), so the global artifact
		// store needs to already be in place for the resolution pass below to resolve them against.
		// This is what actually fixes pog-mismatch-real-cause-duplicate-storageid: an artifact is
		// now loaded exactly once, from its own file, regardless of how many associations reference
		// it - structurally impossible for it to come back as multiple distinct objects sharing one
		// storageId, rather than merely hoping a name-tag-scan-and-overwrite (the old approach)
		// happens to land on a usable one.
		Map<String, Artifact.Op<?>> loadedById = new HashMap<>();
		Map<String, List<String>> idsByPack = new LinkedHashMap<>();
		for (String artifactId : repo.getArtifactIds()) {
			String pack = repo.getArtifactPacks().get(artifactId);
			if (pack == null)
				loadedById.put(artifactId, (Artifact.Op<?>) this.readZippedTracked(this.artifactFile(artifactId)));
			else
				idsByPack.computeIfAbsent(pack, p -> new ArrayList<>()).add(artifactId);
		}
		for (Map.Entry<String, List<String>> pack : idsByPack.entrySet())
			this.readPack(this.artifactsDir.resolve(pack.getKey()), pack.getValue(), loadedById);
		List<Artifact.Op<?>> loadedArtifacts = new ArrayList<>(repo.getArtifactIds().size());
		for (String artifactId : repo.getArtifactIds())
			loadedArtifacts.add(loadedById.get(artifactId));
		repo.restoreArtifacts(loadedArtifacts);

		// load each association from its own file (eagerly - this spike only addresses the write
		// side; every association is still loaded on open, same as before) and wire up the
		// resolver every commit needs to turn the IDs it holds back into Association objects. A
		// no-op for a brand new repository (SerRepository starts with an empty association-id set).
		List<Association.Op> loadedAssociations = new ArrayList<>(repo.getAssociationIds().size());
		for (String associationId : repo.getAssociationIds()) {
			Path associationFile = this.associationsDir.resolve(associationId + DB_FILE_SUFFIX);
			loadedAssociations.add((Association.Op) this.readZippedTracked(associationFile));
		}
		repo.restoreAssociations(loadedAssociations);
		// NOTE: deliberately NOT this.database.getCommitIndex().values() here - that map is only
		// ever populated by SerCommitDao.save()/SerRepositoryDao.store(), neither of which
		// EccoService's actual commit() flow ever calls (commits are added via
		// SerRepository.addCommit(), a separate, always-populated collection). Using commitIndex
		// left every commit loaded from disk with no association resolver wired up at all -
		// Commit.getAssociations() would throw IllegalStateException for every commit after any
		// reload. Caught by CommitAssociationConsistencyTest, not by any pre-existing test (none of
		// them call Commit.getAssociations() after a reload).
		for (Commit commit : repo.getCommits()) {
			if (commit instanceof SerCommit serCommit) {
				serCommit.setAssociationResolver(repo);
			}
		}

		this.resolveCrossAssociationReferences(repo);
		this.resolveModuleReferences(repo);

		// SerRepository.mainTree is purely derived from associations (see buildMainTree()) but
		// isn't transient, so a freshly-loaded repository initially holds whatever copy was
		// persisted alongside the "core" blob - built by copying each association's tree
		// (SerBoostedAssociationMerger.createBoostedAssociationTree -> copyTree, which reuses
		// artifact instances rather than cloning them). That copy was serialized as part of the core
		// blob, a stream entirely separate from the per-association files, so its node/artifact
		// references suffer the exact same cross-file dangling problem resolveCrossAssociationReferences
		// just fixed for the associations themselves - except mainTree isn't indexed by that pass at
		// all. Just invalidating it (not rebuilding it here) sidesteps that entirely: getMainTree()
		// rebuilds lazily, on first actual use, from the now-correctly-resolved associations. Calling
		// buildMainTree() unconditionally here instead was tried and reverted - it made every
		// read-only transaction (e.g. just listing associations, which never touches the main tree)
		// pay for a full copy+boost+PartialOrderGraph-merge of every association, a measured
		// real-world regression (slow repo open in the GUI's Artifacts tab).
		repo.invalidateMainTree();
	}

	/**
	 * Each association was just deserialized from its own, independent file/stream, so any
	 * reference that points OUTSIDE that association's own tree (SerNode.artifactId,
	 * SerArtifactReference.source/target - transient, carrying only an id; and
	 * SerArtifact.containingNode, not persisted at all but derived from the trees) was not restored
	 * by the default deserialization of that file. artifactsById comes
	 * directly from the global artifact store (restoreArtifacts(), already loaded above) rather than
	 * being harvested by walking nodes - that's what actually fixes
	 * pog-mismatch-real-cause-duplicate-storageid, since it means there is structurally exactly one
	 * instance per artifact id, not "whichever association's independently-deserialized copy
	 * happened to be indexed last". This walks every loaded association's tree once to wire each
	 * node's artifactId to that instance (and each artifact's containing node to the unique node
	 * holding it), then fills in the remaining transient fields - always resolving to a real, properly-reconstructed
	 * instance, never a dangling or independently-duplicated fragment. See
	 * TreesObjectIdentityDependencyTest and incremental-persistence-node-sharing-blocker for why
	 * this matters.
	 */
	private void resolveCrossAssociationReferences(SerRepository repo) {
		Map<String, Artifact.Op<?>> artifactsById = new HashMap<>();
		for (String artifactId : repo.getArtifactIds()) {
			Artifact.Op<?> artifact = repo.getArtifact(artifactId);
			if (artifact != null) {
				artifactsById.put(artifactId, artifact);
			}
		}

		for (Association.Op association : repo.getAssociations()) {
			if (association.getRootNode() != null) {
				this.resolveNodeArtifacts(association.getRootNode(), artifactsById);
			}
		}

		for (Artifact.Op<?> artifact : artifactsById.values()) {
			if (!(artifact instanceof SerArtifact<?> serArtifact)) continue;

			this.resolveReferences(serArtifact.getUses(), artifactsById);
			this.resolveReferences(serArtifact.getUsedBy(), artifactsById);

			if (serArtifact.getPartialOrderGraph() != null) {
				this.resolvePartialOrderGraph(serArtifact.getPartialOrderGraph(), artifactsById);
			}
		}
	}

	/**
	 * Each association's AssociationCounter -> ModuleCounter -> ModuleRevisionCounter chain holds
	 * direct (non-transient) references to Module/ModuleRevision - deserialized independently once
	 * per association file (SerModuleCounter.module, SerModuleRevisionCounter.moduleRevision), so
	 * every association ends up with its own data-equal-but-object-distinct copy instead of sharing
	 * the repository's one canonical instance (SerRepository.modules/features, part of the "core"
	 * blob, not split into per-association files). Association.computeCondition() reads a mutable
	 * count directly off whichever ModuleRevision instance a counter happens to reference
	 * (moduleRevisionCounter.getObject().getCount()), not off the per-association counter itself -
	 * so that divergence corrupts presence-condition computation for every association after any
	 * reload, in a way a single continuous session never exhibits (the repository's registry is the
	 * only source of Module/ModuleRevision objects there, so everything shares by construction).
	 * Unlike artifacts/nodes, Module/ModuleRevision equality is already content-based (feature id
	 * strings all the way down), so no id surrogate is needed - resolving is just replacing each
	 * counter's reference with the repository's own lookup result.
	 */
	private void resolveModuleReferences(SerRepository repo) {
		// counters read in compact form are built on the repository's modules right away; only
		// those read in the old form hold copies to replace
		for (Association.Op association : repo.getAssociations()) {
			if (association.getCounter() instanceof SerAssociationCounter serAssociationCounter
					&& serAssociationCounter.resolveCompactChildren(repo))
				continue;
			for (ModuleCounter moduleCounter : association.getCounter().getChildren()) {
				if (!(moduleCounter instanceof SerModuleCounter serModuleCounter)) continue;

				Module module = moduleCounter.getObject();
				SerModule canonicalModule = repo.getModule(module.getPos(), module.getNeg());
				if (canonicalModule != null) {
					serModuleCounter.resolveModule(canonicalModule);
				}
				Module lookupModule = canonicalModule != null ? canonicalModule : module;

				for (ModuleRevisionCounter moduleRevisionCounter : moduleCounter.getChildren()) {
					if (!(moduleRevisionCounter instanceof SerModuleRevisionCounter serModuleRevisionCounter)) continue;

					ModuleRevision moduleRevision = moduleRevisionCounter.getObject();
					ModuleRevision canonicalModuleRevision = lookupModule.getRevision(moduleRevision.getPos(), moduleRevision.getNeg());
					if (canonicalModuleRevision instanceof SerModuleRevision serCanonicalModuleRevision) {
						serModuleRevisionCounter.resolveModuleRevision(serCanonicalModuleRevision);
					}
				}
			}
		}
	}

	/**
	 * PartialOrderGraphs get merged across nodes that can belong to different associations
	 * (Trees.slice(), Trees.java:115), so a POG node's artifact (SerPartialOrderGraphNode.artifact)
	 * can be a foreign, side-channel-serialized reference for the same reason
	 * SerArtifact.containingNode was - resolved here against the same global artifact-id index.
	 */
	private void resolvePartialOrderGraph(PartialOrderGraph.Op graph, Map<String, Artifact.Op<?>> artifactsById) {
		for (PartialOrderGraph.Node.Op node : graph.collectNodes()) {
			if (!(node instanceof SerPartialOrderGraphNode serNode)) continue;

			String artifactId = serNode.getArtifactId();
			if (artifactId == null) continue; // head/tail sentinel nodes have no artifact

			Artifact.Op<?> artifact = artifactsById.get(artifactId);
			if (artifact == null) {
				throw new EccoException("Could not resolve POG node artifact " + artifactId + " after loading all associations.");
			}
			serNode.resolveArtifact(artifact);
		}
	}

	private void resolveNodeArtifacts(Node.Op node, Map<String, Artifact.Op<?>> artifactsById) {
		if (node instanceof SerNode serNode) {

			String artifactId = serNode.getArtifactId();
			if (artifactId != null) {
				Artifact.Op<?> artifact = artifactsById.get(artifactId);
				if (artifact == null) {
					throw new EccoException("Could not resolve node artifact " + artifactId + " after loading all associations.");
				}
				serNode.resolveArtifact(artifact);
				// an artifact's containing node is the unique node holding it - derived here rather than
				// persisted (see SerArtifact's containingNode field); verified to equal the previously
				// persisted value on every repository examined
				if (node.isUnique() && artifact instanceof SerArtifact<?> serArtifact)
					serArtifact.resolveContainingNode(node);
			}
		}
		for (Node.Op child : node.getChildren()) {
			this.resolveNodeArtifacts(child, artifactsById);
		}
	}

	private void resolveReferences(Iterable<ArtifactReference.Op> references, Map<String, Artifact.Op<?>> artifactsById) {
		for (ArtifactReference.Op reference : references) {
			if (!(reference instanceof SerArtifactReference serReference)) continue;

			Artifact.Op<?> source = artifactsById.get(serReference.getSourceId());
			Artifact.Op<?> target = artifactsById.get(serReference.getTargetId());
			if (source == null || target == null) {
				throw new EccoException("Could not resolve artifact reference (source=" + serReference.getSourceId() + ", target=" + serReference.getTargetId() + ") after loading all associations.");
			}
			serReference.resolveReferences(source, target);
		}
	}


//	private Object deserialize(Path file) throws IOException, ClassNotFoundException {
//		try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(file))) {
//			ZipEntry e = null;
//			while ((e = zis.getNextEntry()) != null) {
//				if (e.getName().equals("ecco.ser")) {
//					try (ObjectInputStream ois = new ObjectInputStream(zis)) {
//						return ois.readObject();
//					}
//				}
//			}
//		}
//		return null;
//	}

//	private void serialize(Object object, Path file) throws IOException {
//		try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(file, StandardOpenOption.CREATE))) {
//			zos.putNextEntry(new ZipEntry("ecco.ser"));
//			try (ObjectOutputStream oos = new ObjectOutputStream(zos)) {
//				oos.writeObject(object);
//			}
//		}
//	}

}
