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
	private static final String ZIP_ENTRY_NAME = "ecco.ser";

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
	 */
	private static void writeStored(Object object, Path file) throws IOException {
		ByteArrayOutputStream serialized = new ByteArrayOutputStream();
		try (ObjectOutputStream oos = new ObjectOutputStream(serialized)) {
			oos.writeObject(object);
		}
		byte[] serializedBytes = serialized.toByteArray();
		CRC32 crc32 = new CRC32();
		crc32.update(serializedBytes);
		try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(file, StandardOpenOption.CREATE))) {
			ZipEntry entry = new ZipEntry(ZIP_ENTRY_NAME);
			entry.setMethod(ZipEntry.STORED);
			entry.setSize(serializedBytes.length);
			entry.setCompressedSize(serializedBytes.length);
			entry.setCrc(crc32.getValue());
			zos.putNextEntry(entry);
			zos.write(serializedBytes);
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
		for (Artifact.Op<?> artifact : repo.getDirtyArtifacts()) {
			if (!(artifact instanceof SerArtifact<?> serArtifact)) continue;
			Path artifactFile = this.artifactsDir.resolve(serArtifact.getStorageId() + DB_FILE_SUFFIX);
			writeStored(artifact, artifactFile);
		}

		// write only the associations actually touched this transaction, one file each, rather
		// than the whole database - most associations are untouched by any given commit but were,
		// before this, being fully reserialized every single time anyway. See the class javadoc on
		// SerCommit for why commits/the repository hold association IDs rather than direct
		// references (that's what makes it safe to leave everything else out of the "core" write
		// below). Written BEFORE the core/id-file swap: if we crash after writing some of these but
		// before the swap, the old core (which doesn't reference the new files yet) is still valid,
		// and the new files are just harmless, unreferenced garbage - the same "write new, then
		// atomically flip a pointer to it" safety property the core file already had.
		if (!repo.getDirtyAssociations().isEmpty()) {
			Files.createDirectories(this.associationsDir);
		}
		for (Association association : repo.getDirtyAssociations()) {
			Path associationFile = this.associationsDir.resolve(association.getId() + DB_FILE_SUFFIX);
			writeStored(association, associationFile);
		}

		// compute new random id
		String newId = UUID.randomUUID().toString();
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

		// obtain exclusive lock on id file, write new id, update current id and db file, release lock
		try (FileChannel idFileChannel = FileChannel.open(this.idFile, StandardOpenOption.WRITE, StandardOpenOption.CREATE); FileLock idFileLock = idFileChannel.lock(0, Long.MAX_VALUE, false)) {
			if (!idFileLock.isValid())
				throw new EccoException("Could not obtain exclusive lock on ID file.");

			// write new id to id file
			idFileChannel.write(ByteBuffer.wrap(newId.getBytes(Charset.defaultCharset())));

			// delete old db file if nobody has a shared lock anymore (i.e. if we can get an exclusive lock on it)
			if (this.dbFile != null) {
				try (FileChannel oldDbFileChannel = FileChannel.open(this.dbFile, StandardOpenOption.WRITE); FileLock oldDbFileLock = oldDbFileChannel.lock(0, Long.MAX_VALUE, false)) {
					if (oldDbFileLock.isValid())
						Files.delete(this.dbFile);
				}
			}

			// update id and db file
			this.id = newId;
			this.dbFile = newDbFile;

			// release exclusive id lock automatically when exiting try block
		}

		// best-effort cleanup of association files no longer referenced by the now-current core -
		// after the id-file swap above, so a failure here never leaves the repository in a state
		// where the current core references a file that got deleted
		for (String removedId : repo.getRemovedAssociationIds()) {
			Files.deleteIfExists(this.associationsDir.resolve(removedId + DB_FILE_SUFFIX));
		}
		repo.clearDirtyTracking();

		// release exclusive write lock automatically after try block
		this.writeFileLock.close();
		this.writeFileChannel.close();

		this.transaction = null;
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
		this.id = null;
		this.dbFile = null;
		this.database = null;
		this.transaction = null;
		this.transactionCounter = 0;
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
					while ((e = zis.getNextEntry()) != null) {
						if (e.getName().equals("ecco.ser")) {
							ObjectInputStream ois = new ObjectInputStream(zis);
							this.database = (Database) ois.readObject();
							break;
						}
					}
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
				throw new EccoException("DB file does not exist: " + this.dbFile);
			}
		} else {
			this.database = new Database();
		}

		SerRepository repo = (SerRepository) this.database.getRepository();

		// load every artifact from its own file BEFORE any association - association trees' nodes
		// only carry an artifactId now (see SerNode.artifactId's javadoc), so the global artifact
		// store needs to already be in place for the resolution pass below to resolve them against.
		// This is what actually fixes pog-mismatch-real-cause-duplicate-storageid: an artifact is
		// now loaded exactly once, from its own file, regardless of how many associations reference
		// it - structurally impossible for it to come back as multiple distinct objects sharing one
		// storageId, rather than merely hoping a name-tag-scan-and-overwrite (the old approach)
		// happens to land on a usable one.
		List<Artifact.Op<?>> loadedArtifacts = new ArrayList<>(repo.getArtifactIds().size());
		for (String artifactId : repo.getArtifactIds()) {
			Path artifactFile = this.artifactsDir.resolve(artifactId + DB_FILE_SUFFIX);
			loadedArtifacts.add((Artifact.Op<?>) readZipped(artifactFile));
		}
		repo.restoreArtifacts(loadedArtifacts);

		// load each association from its own file (eagerly - this spike only addresses the write
		// side; every association is still loaded on open, same as before) and wire up the
		// resolver every commit needs to turn the IDs it holds back into Association objects. A
		// no-op for a brand new repository (SerRepository starts with an empty association-id set).
		List<Association.Op> loadedAssociations = new ArrayList<>(repo.getAssociationIds().size());
		for (String associationId : repo.getAssociationIds()) {
			Path associationFile = this.associationsDir.resolve(associationId + DB_FILE_SUFFIX);
			loadedAssociations.add((Association.Op) readZipped(associationFile));
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
	 * SerArtifact.containingNode, SerArtifactReference.source/target - all transient, carrying only
	 * an id) was not restored by the default deserialization of that file. artifactsById comes
	 * directly from the global artifact store (restoreArtifacts(), already loaded above) rather than
	 * being harvested by walking nodes - that's what actually fixes
	 * pog-mismatch-real-cause-duplicate-storageid, since it means there is structurally exactly one
	 * instance per artifact id, not "whichever association's independently-deserialized copy
	 * happened to be indexed last". This walks every loaded association's tree once to wire each
	 * node's artifactId to that instance and build a node id -> instance index, then uses both to
	 * fill in the remaining transient fields - always resolving to a real, properly-reconstructed
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

		Map<String, Node.Op> nodesById = new HashMap<>();
		for (Association.Op association : repo.getAssociations()) {
			if (association.getRootNode() != null) {
				this.indexNodeAndResolveArtifact(association.getRootNode(), nodesById, artifactsById);
			}
		}

		for (Artifact.Op<?> artifact : artifactsById.values()) {
			if (!(artifact instanceof SerArtifact<?> serArtifact)) continue;

			String containingNodeId = serArtifact.getContainingNodeId();
			if (containingNodeId != null) {
				Node.Op containingNode = nodesById.get(containingNodeId);
				if (containingNode == null) {
					throw new EccoException("Could not resolve containing node " + containingNodeId + " for artifact " + serArtifact.getStorageId() + " after loading all associations.");
				}
				serArtifact.resolveContainingNode(containingNode);
			}

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
		for (Association.Op association : repo.getAssociations()) {
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

	private void indexNodeAndResolveArtifact(Node.Op node, Map<String, Node.Op> nodesById, Map<String, Artifact.Op<?>> artifactsById) {
		if (node instanceof SerNode serNode) {
			nodesById.put(serNode.getStorageId(), node);

			String artifactId = serNode.getArtifactId();
			if (artifactId != null) {
				Artifact.Op<?> artifact = artifactsById.get(artifactId);
				if (artifact == null) {
					throw new EccoException("Could not resolve node artifact " + artifactId + " after loading all associations.");
				}
				serNode.resolveArtifact(artifact);
			}
		}
		for (Node.Op child : node.getChildren()) {
			this.indexNodeAndResolveArtifact(child, nodesById, artifactsById);
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
