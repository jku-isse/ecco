package at.jku.isse.ecco.service;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.counter.ModuleCounter;
import at.jku.isse.ecco.counter.ModuleRevisionCounter;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.storage.ser.core.SerCommit;
import at.jku.isse.ecco.storage.ser.counter.CompactCounters;
import at.jku.isse.ecco.storage.ser.dao.LoadResolver;
import at.jku.isse.ecco.storage.ser.module.SerModuleRevision;
import at.jku.isse.ecco.storage.ser.repository.SerRepository;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.logging.Logger;

/**
 * Object input streams for the fetch/pull/push/fork wire protocol. Everything read from a peer goes
 * through an allow-list filter: only ECCO's own classes plus the JDK value and collection types they
 * are built from may be deserialized. A plain ObjectInputStream would instantiate any serializable
 * class on the classpath - and run its readObject() - before the caller's cast could reject it, the
 * classic deserialization-gadget remote-code-execution vector, reachable by anyone who can connect
 * to a running sync server.
 * <p>
 * at.jku.cdl.ecco is included because the java-ast adapter's artifact data lives there, Eclipse
 * Collections because several entities hold its maps/sets. If a new
 * adapter persists a type outside these packages, sync will fail with InvalidClassException naming
 * it - add it here deliberately rather than widening the patterns.
 */
public final class SyncObjectStreams {

	private static final Logger LOGGER = Logger.getLogger(SyncObjectStreams.class.getName());

	private static final ObjectInputFilter ALLOW_LIST = ObjectInputFilter.Config.createFilter(
			"at.jku.isse.ecco.**;at.jku.cdl.ecco.**;java.lang.*;java.util.*;java.util.concurrent.*;java.util.concurrent.atomic.*;org.eclipse.collections.impl.**;!*");

	/**
	 * The allow-list, logging what it rejects - InvalidClassException's own message ("filter status:
	 * REJECTED") doesn't name the class.
	 */
	static final ObjectInputFilter FILTER = info -> {
		ObjectInputFilter.Status status = ALLOW_LIST.checkInput(info);
		if (status == ObjectInputFilter.Status.REJECTED)
			LOGGER.warning("Sync: rejected deserializing " + info.serialClass());
		return status;
	};

	private SyncObjectStreams() {
	}

	public static ObjectInputStream newObjectInputStream(InputStream in) throws IOException {
		ObjectInputStream ois = new ObjectInputStream(in);
		ois.setObjectInputFilter(FILTER);
		return ois;
	}

	/**
	 * Writes a repository - a subset to fetch, pull, push or fork - as the peer reads it with
	 * {@link #readRepository}: the repository, then its associations and artifacts, which are not part
	 * of its own serialized form (associations are held behind a transient index, a node holds only
	 * its artifact's id - see Repository.Op#restoreAssociations/#collectArtifacts/#resolveArtifacts).
	 * All three go through the one stream, so what they share stays one object.
	 */
	static void writeRepository(ObjectOutputStream oos, Repository.Op repository) throws IOException {
		oos.writeObject(repository);
		oos.writeObject(new ArrayList<Association.Op>(repository.getAssociations()));
		oos.writeObject(new ArrayList<Artifact.Op<?>>(repository.collectArtifacts()));
	}

	/** Reads a repository written by {@link #writeRepository}, its associations restored and its artifacts resolved. */
	static Repository.Op readRepository(ObjectInputStream ois) throws IOException, ClassNotFoundException {
		Repository.Op repository = (Repository.Op) ois.readObject();
		@SuppressWarnings("unchecked")
		Collection<Association.Op> associations = (Collection<Association.Op>) ois.readObject();
		repository.restoreAssociations(associations);
		@SuppressWarnings("unchecked")
		Collection<Artifact.Op<?>> artifacts = (Collection<Artifact.Op<?>>) ois.readObject();
		repository.resolveArtifacts(artifacts);
		return repository;
	}

	/**
	 * A repository as it is, for a fork of everything (CLONE): the repository with its commits, its
	 * associations and their artifacts, written as {@link #writeRepository} writes them but with the
	 * associations' counters in their compact form, as in an association file (CompactCounters) - the
	 * exact counts of a repository of many features are millions of entries (1.9 million in one of 47
	 * variants), too many to send one object each. The compact form refers to the repository's list
	 * of counted module revisions, so every counted one is put on that list before the repository is
	 * written.
	 */
	static byte[] writeCopy(Repository.Op repository) throws IOException {
		SerRepository serRepository = (SerRepository) repository;
		for (Association.Op association : repository.getAssociations())
			for (ModuleCounter moduleCounter : association.getCounter().getChildren())
				for (ModuleRevisionCounter moduleRevisionCounter : moduleCounter.getChildren())
					serRepository.countedIndex((SerModuleRevision) moduleRevisionCounter.getObject());
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (CompactCounters.Stream oos = new CompactCounters.Stream(bytes, serRepository)) {
			writeRepository(oos, repository);
		}
		return bytes.toByteArray();
	}

	/** Reads a repository written by {@link #writeCopy}, resolved as a load resolves one. */
	static Repository.Op readCopy(byte[] bytes) throws IOException, ClassNotFoundException {
		try (ObjectInputStream ois = newObjectInputStream(new ByteArrayInputStream(bytes))) {
			Repository.Op repository = readRepository(ois);
			if (repository instanceof SerRepository serRepository) {
				for (Commit commit : serRepository.getCommits())
					if (commit instanceof SerCommit serCommit)
						serCommit.setAssociationResolver(serRepository);
				LoadResolver.resolveModuleReferences(serRepository);
			}
			return repository;
		}
	}
}
