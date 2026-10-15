package at.jku.isse.ecco.storage.ser.dao;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.storage.ser.repository.SerRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import static at.jku.isse.ecco.storage.ser.dao.StorageFiles.DB_FILE_SUFFIX;
import static at.jku.isse.ecco.storage.ser.dao.StorageFiles.digest;
import static at.jku.isse.ecco.storage.ser.dao.StorageFiles.serializeAssociation;
import static at.jku.isse.ecco.storage.ser.dao.StorageFiles.writeStoredBytes;

/**
 * The associations' files, one per association ({@code associations/<id>.ser.zip}): reading them,
 * and staging those a write changed - each written only if its bytes differ from the stored file.
 */
final class AssociationStore {

	/**
	 * Serialize the associations a write leaves alone (not added again since they were read), and
	 * fail if one differs from what is stored - a change made without Repository.Op#addAssociation()
	 * would otherwise not be stored, as the retroactive conditions of untouched associations were
	 * not (ReopenEquivalenceTest). On in the tests (build.gradle); -Decco.verifyUnchangedAssociations=true for a replay.
	 */
	static final boolean VERIFY_UNCHANGED_ASSOCIATIONS = Boolean.getBoolean("ecco.verifyUnchangedAssociations");

	private final Path dir;
	// SHA-256 of the serialized bytes of every stable artifact/association file as last read or
	// written by this process - lets endReadWrite() skip entities whose bytes didn't change. Cleared
	// by reset(); rebuilt by the full reload that follows.
	private final Map<Path, byte[]> persistedDigests = new HashMap<>();
	/**
	 * Only with {@link #VERIFY_UNCHANGED_ASSOCIATIONS}: the digest of each association as serialized
	 * when it was last read or written. Not the file's: an association read back serializes to the
	 * same content in different bytes (a boxed property value its file shares by reference is its
	 * own object after reading).
	 */
	private final Map<String, byte[]> verifiedAssociationDigests = new HashMap<>();

	AssociationStore(Path dir) {
		this.dir = dir;
	}

	/** The file of the association [id]. */
	Path file(String id) {
		return this.dir.resolve(id + DB_FILE_SUFFIX);
	}

	/** Reads the association [id], remembering the digest of its file. */
	Association.Op read(String id) throws IOException, ClassNotFoundException {
		Path file = this.file(id);
		StorageFiles.Read read = StorageFiles.readZipped(file);
		this.persistedDigests.put(file, read.digest());
		return (Association.Op) read.object();
	}

	/** After [repo] was read: with {@link #VERIFY_UNCHANGED_ASSOCIATIONS}, records its associations as they serialize now. */
	void loaded(SerRepository repo) throws IOException {
		if (!VERIFY_UNCHANGED_ASSOCIATIONS)
			return;
		this.verifiedAssociationDigests.clear();
		Set<Association.Op> dirty = Collections.newSetFromMap(new IdentityHashMap<>());
		dirty.addAll(repo.getDirtyAssociations());
		this.recordVerifiedDigests(repo.getAssociations().stream().filter(a -> !dirty.contains(a)).toList());
	}

	/**
	 * Stages the associations a write touched under [pendingSuffix] - only the touched ones, one file
	 * each, rather than the whole database: most associations are untouched by any given commit. See
	 * SerCommit for why commits and the repository hold association ids rather than references (what
	 * makes it safe to leave everything else out of the core). Staged under pending names, never over
	 * the stable files the still-current core loads.
	 *
	 * @return stable file -> digest of each file staged
	 */
	Map<Path, byte[]> stage(SerRepository repo, String pendingSuffix) throws IOException {
		if (VERIFY_UNCHANGED_ASSOCIATIONS)
			this.verifyUnchangedAssociations(repo);
		if (!repo.getDirtyAssociations().isEmpty())
			Files.createDirectories(this.dir);
		Map<Path, byte[]> staged = new HashMap<>();
		for (Association.Op association : repo.getDirtyAssociations())
			this.stageIfChanged(association, this.file(association.getId()), pendingSuffix, staged);
		if (VERIFY_UNCHANGED_ASSOCIATIONS) {
			this.recordVerifiedDigests(repo.getDirtyAssociations());
			this.verifiedAssociationDigests.keySet().removeAll(repo.getRemovedAssociationIds());
		}
		return staged;
	}

	/**
	 * After the commit point: the staged files are stored now; the files of associations the new core
	 * no longer references go (best effort - after the commit point, so a failure never leaves the
	 * current core referencing a deleted file).
	 */
	void committed(SerRepository repo, Map<Path, byte[]> staged) throws IOException {
		this.persistedDigests.putAll(staged);
		for (String removedId : repo.getRemovedAssociationIds()) {
			Path removedFile = this.file(removedId);
			Files.deleteIfExists(removedFile);
			this.persistedDigests.remove(removedFile);
		}
	}

	/** Forgets what was read and written - the next transaction reads everything again. */
	void clear() {
		this.persistedDigests.clear();
		this.verifiedAssociationDigests.clear();
	}

	/**
	 * Stages a dirty entity's file for the transaction tagged {@code pendingSuffix} (see
	 * SerTransactionStrategy#endReadWrite()) - unless its serialized bytes are identical to the stable file already
	 * on disk (known from {@link #persistedDigests}), in which case nothing is written at all. Most
	 * "dirty" entities of a commit are unchanged: Trees.slice() re-slices every touched association
	 * against the whole working tree, so far more is marked dirty than actually differs, and writing
	 * one file per artifact dominated commit time (see UnchangedArtifactRewriteTest).
	 */
	private void stageIfChanged(Association association, Path stableFile, String pendingSuffix, Map<Path, byte[]> staged) throws IOException {
		byte[] bytes = serializeAssociation(association);
		byte[] digest = digest(bytes);
		if (Arrays.equals(digest, this.persistedDigests.get(stableFile)) && Files.exists(stableFile))
			return;
		writeStoredBytes(bytes, stableFile.resolveSibling(stableFile.getFileName() + pendingSuffix));
		staged.put(stableFile, digest);
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
			if (!Arrays.equals(stored, digest(serializeAssociation(association))))
				throw new IllegalStateException("Association " + association.getId() + " changed without being marked to be written (Repository.Op#addAssociation) - the change would not be stored.");
		}
	}

	/** See {@link #verifiedAssociationDigests}: records [associations] as they serialize now. */
	private void recordVerifiedDigests(Collection<? extends Association> associations) throws IOException {
		for (Association association : associations)
			this.verifiedAssociationDigests.put(association.getId(), digest(serializeAssociation(association)));
	}
}
