package at.jku.isse.ecco.storage.ser.dao;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.service.SyncedFolders;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.dao.TransactionStrategy;
import at.jku.isse.ecco.pog.PartialOrderGraph;
import at.jku.isse.ecco.storage.common.dao.Database;
import at.jku.isse.ecco.storage.ser.artifact.SerArtifact;
import at.jku.isse.ecco.storage.ser.core.SerCommit;
import at.jku.isse.ecco.storage.ser.repository.SerRepository;
import at.jku.isse.ecco.storage.ser.tree.SerNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import java.io.BufferedInputStream;
import java.util.Set;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static com.google.common.base.Preconditions.checkNotNull;

@Singleton
public class SerTransactionStrategy implements TransactionStrategy {

	private static final Logger LOGGER = Logger.getLogger(SerTransactionStrategy.class.getName());


	private static final String ID_FILENAME = "id";
	private static final String WRITELOCK_FILENAME = "write";
	private static final String ASSOCIATIONS_DIRNAME = "associations";
	private static final String ARTIFACTS_DIRNAME = "artifacts";
	private static final String PIECES_DIRNAME = "pieces";

	// repository directory
	private final Path repositoryDir;
	// file containing the current database id
	private final Path idFile;
	// lock file for making sure there is onyl one write transaction going on at a time
	private final Path writeLockFile;
	// one file per association lives here - see the class javadoc on SerCommit for why this split exists
	private final Path associationsDir;
	// the artifact packs live here - see writeArtifactPack()
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
	// the parts of the storage - the strategy itself holds the transactions, the locks and the core,
	// and the order of the steps of reading and writing
	private final CommitJournal journal;
	private final AssociationStore associations;
	private final PieceStore pieces;
	private final ArtifactPackStore packs;



	@Inject
	public SerTransactionStrategy(@Named("repositoryDir") final Path repositoryDir) {
		checkNotNull(repositoryDir);
		this.repositoryDir = repositoryDir;
		this.idFile = repositoryDir.resolve(ID_FILENAME);
		this.writeLockFile = repositoryDir.resolve(WRITELOCK_FILENAME);
		this.associationsDir = repositoryDir.resolve(ASSOCIATIONS_DIRNAME);
		this.artifactsDir = repositoryDir.resolve(ARTIFACTS_DIRNAME);
		this.journal = new CommitJournal(this.associationsDir, this.artifactsDir);
		this.pieces = new PieceStore(repositoryDir.resolve(PIECES_DIRNAME));
		this.associations = new AssociationStore(this.associationsDir, this.pieces);
		this.packs = new ArtifactPackStore(this.artifactsDir);
		this.reset();
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
		} catch (IOException e) {
			throw new EccoException(this.ioFailure(transaction == TRANSACTION.READ_WRITE ? "open for writing" : "read", e), e);
		} catch (ClassNotFoundException e) {
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
				throw new EccoException(this.ioFailure("write", e), e);
			}
		}
	}


	/**
	 * Says which repository could not be read or written, and why - "Error beginning transaction"
	 * with the system's "Operation timed out" left the user guessing - and, for a repository in a
	 * folder a cloud service synchronizes (the usual cause of that timeout), what to do.
	 */
	private String ioFailure(String operation, IOException e) {
		String message = "Could not " + operation + " the repository " + this.repositoryDir.toAbsolutePath().normalize() + ": "
				+ (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()) + ".";
		return SyncedFolders.warning(this.repositoryDir).map(warning -> message + " " + warning).orElse(message);
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
		try {
			this.writeFileLock = this.writeFileChannel.lock(0, Long.MAX_VALUE, false);
			if (!this.writeFileLock.isValid())
				throw new EccoException("Could not obtain exclusive lock on WRITE file.");
		} catch (IOException | RuntimeException e) {
			// the channel stayed open until close() when locking failed
			this.writeFileChannel.close();
			this.writeFileChannel = null;
			this.writeFileLock = null;
			throw e;
		}

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
		ArtifactPackStore.registerDirtyArtifacts(repo);

		// write dirty artifacts before dirty associations: an association's nodes only carry
		// artifact IDs now (see SerNode.artifactId's javadoc), so on a fresh load the artifact files
		// need to already exist for the resolution pass to find - writing them first is not itself
		// required for crash-safety (nothing points at them until the id-file swap below, same as
		// associations), just keeps the two writes in the same order load reads them back in.
		// compute the new core id up front: every dirty file below is staged under a name tagged with
		// it, so that the id-file swap further down is the single commit point for the whole
		// transaction (see the javadoc on this method)
		String newId = UUID.randomUUID().toString();
		String pendingSuffix = CommitJournal.pendingSuffix(newId);
		// discard leftovers of earlier transactions that failed before their swap - safe here, we
		// hold the exclusive write lock, so no other writer can have files in flight
		this.journal.recoverPendingFiles(this.id, true);

		Map<String, byte[]> stagedArtifacts = new HashMap<>();
		Set<String> looseArtifactFiles = this.packs.write(repo, newId, stagedArtifacts);

		// then the associations it touched (see AssociationStore#stage)
		Map<Path, byte[]> staged = this.associations.stage(repo, pendingSuffix);
		// the pieces of their trees not stored yet - in a pack of their own, before the core that refers to them
		this.pieces.write(newId);

		// serialize to new db file
		Path newDbFile = this.repositoryDir.resolve(newId + StorageFiles.DB_FILE_SUFFIX);
		// this "core" write is now cheap regardless of repository size: SerRepository.associations
		// and SerCommit.associations are both ID-only now (see their javadocs), so the only things
		// actually reachable from `database` here are IDs, commit/feature/module metadata, and
		// similar - not the (large, POG-heavy) association trees themselves.
		this.database.recordStorageFormat();
		StorageFiles.writeStored(this.database, newDbFile);

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

		// the swap above committed the transaction: whatever fails from here on must not report it as
		// failed - the user would retry, and a retried commit is a second commit. The next load
		// finishes what is left (loadDatabase() rolls pending files forward).
		try {
			this.finishCommitted(repo, newId, staged, stagedArtifacts, oldCoreReleased, looseArtifactFiles);
		} catch (IOException | RuntimeException e) {
			LOGGER.log(Level.WARNING, "The write to " + this.repositoryDir + " is committed, but finishing it failed; the next read of the repository finishes it.", e);
			// nothing held in memory is trusted: the next transaction reads the repository again
			this.reset();
			return;
		}

		// release exclusive write lock - via releaseWriteLock() rather than closing directly, so the
		// fields are nulled too; otherwise the next reset() (e.g. close()) tried to release the
		// already-closed lock again and printed "Error releasing write lock: null" after every write
		this.releaseWriteLock();

		this.transaction = null;
	}

	/**
	 * After the commit point of {@link #endReadWrite()}: moves the staged files over their stable
	 * names, deletes the files the new core no longer references, and records what is stored now.
	 */
	private void finishCommitted(SerRepository repo, String newId, Map<Path, byte[]> staged, Map<String, byte[]> stagedArtifacts,
								 boolean oldCoreReleased, Set<String> looseArtifactFiles) throws IOException {
		this.journal.recoverPendingFiles(newId, false);
		this.associations.committed(repo, staged);
		this.packs.committed(stagedArtifacts);
		if (oldCoreReleased) {
			this.packs.deleteSupersededArtifactFiles(repo, looseArtifactFiles);
			this.pieces.deleteUnreferenced(repo.getAssociationIds());
		}
		// stored as they are now: unchanged until changed again
		for (Artifact.Op<?> artifact : repo.getDirtyArtifacts())
			if (artifact instanceof SerArtifact<?> serArtifact)
				serArtifact.clearModified();
		repo.clearDirtyTracking();
	}




	private void reset() {
		this.associations.clear();
		this.packs.clear();
		this.pieces.clear();
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
			if (this.id != null && this.id.equals(id))
				return;
			this.id = id;

			Path dbFile = this.repositoryDir.resolve(this.id + StorageFiles.DB_FILE_SUFFIX);
			if (Files.exists(dbFile)) {
				this.dbFile = dbFile;
				try (FileChannel dbFileChannel = FileChannel.open(this.dbFile, StandardOpenOption.READ); FileLock dbFileLock = dbFileChannel.lock(0, Long.MAX_VALUE, true)) {
					if (!dbFileLock.isValid())
						throw new EccoException("Could not obtain shared lock on DB file.");

					InputStream is = new BufferedInputStream(Channels.newInputStream(dbFileChannel));
					ZipInputStream zis = new ZipInputStream(is);
					ZipEntry e = null;
					Database loaded = null;
					while ((e = zis.getNextEntry()) != null) {
						if (e.getName().equals(StorageFiles.ZIP_ENTRY_NAME)) {
							ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(zis.readAllBytes()));
							loaded = (Database) ois.readObject();
							break;
						}
					}
					// used to leave the previous (or no) database in place and fail later with an NPE
					if (loaded == null)
						throw new EccoException("Repository core file " + dbFile + " is damaged: it has no " + StorageFiles.ZIP_ENTRY_NAME + " entry.");
					if (loaded.getStorageFormat() > Database.STORAGE_FORMAT)
						throw new EccoException("This repository (" + this.repositoryDir + ") was written by a newer version of ECCO (storage format "
								+ loaded.getStorageFormat() + "; this version reads up to " + Database.STORAGE_FORMAT + "). Open it with that version or a later one.");
					this.database = loaded;
				}

				// delete db file if we can get exclusive lock and it does not match id file
				String currentId = this.readCurrentId();
				Path currentDbFile = this.repositoryDir.resolve(currentId + StorageFiles.DB_FILE_SUFFIX);
				if (!currentDbFile.equals(dbFile)) {
					// try to delete db file
					try (FileChannel oldDbFileChannel = FileChannel.open(dbFile, StandardOpenOption.WRITE); FileLock oldDbFileLock = oldDbFileChannel.lock(0, Long.MAX_VALUE, false)) {
						if (oldDbFileLock.isValid())
							Files.delete(dbFile);
					}
				}
			} else {
				throw new EccoException("DB file does not exist: " + dbFile);
			}
		} else {
			this.database = new Database();
		}

		SerRepository repo = (SerRepository) this.database.getRepository();

		// repositories written before associations (12330811) and then artifacts (3e2b04d6) got their
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
		this.journal.recoverPendingFiles(this.id, this.transaction == TRANSACTION.READ_WRITE && this.writeFileLock != null);

		// the artifacts before any association (see ArtifactPackStore#load)
		this.packs.load(repo);

		// load each association from its own file (eagerly: every association is loaded on open) and wire up the
		// resolver every commit needs to turn the IDs it holds back into Association objects. A
		// no-op for a brand new repository (SerRepository starts with an empty association-id set).
		this.pieces.load();
		List<Association.Op> loadedAssociations = new ArrayList<>(repo.getAssociationIds().size());
		for (String associationId : repo.getAssociationIds()) {
			loadedAssociations.add(this.associations.read(associationId));
		}
		repo.restoreAssociations(loadedAssociations);
		this.pieces.loaded();
		// the repository's commits (Commit.getAssociations() needs the resolver after a reload -
		// CommitAssociationConsistencyTest)
		for (Commit commit : repo.getCommits()) {
			if (commit instanceof SerCommit serCommit) {
				serCommit.setAssociationResolver(repo);
			}
		}

		LoadResolver.resolveCrossAssociationReferences(repo);
		LoadResolver.resolveModuleReferences(repo);
		// the nodes' retroactive conditions are derived from the counters and not stored
		// (SerFeatureTrace): set here, as after every commit
		repo.setRetroactiveConditions();
		this.associations.loaded(repo);

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
}
