package at.jku.isse.ecco.storage.perst.dao;

import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.storage.perst.core.PerstAssociation;
import at.jku.isse.ecco.storage.perst.core.PerstCommit;
import at.jku.isse.ecco.storage.perst.core.PerstRemote;
import at.jku.isse.ecco.storage.perst.core.PerstVariant;
import at.jku.isse.ecco.storage.perst.feature.PerstFeature;
import at.jku.isse.ecco.storage.perst.repository.PerstRepository;
import org.garret.perst.FieldIndex;
import org.garret.perst.Persistent;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * The root object of the persisted object which contains indexers for the different types.
 *
 * @author Hannes Thaller
 * @version 1.0
 */
public class DatabaseRoot extends Persistent {

	private final Repository.Op repository;

	private final FieldIndex<PerstCommit> commitIndex;
	private final FieldIndex<PerstVariant> variantIndex;
	private final FieldIndex<PerstRemote> remoteIndex;

	private int currentCommitId = 0;


	/**
	 * Constructs a new DatabaseRoot with the given indexers.
	 *
	 * @param associationIndex used to index {@link PerstAssociation}
	 * @param featureIndex     used to index {@link PerstFeature}
	 * @param commitIndex      used to index {@link PerstCommit}
	 * @param variantIndex     used to index {@link PerstVariant}
	 * @param remoteIndex      used to index {@link PerstRemote}
	 */
	public DatabaseRoot(final FieldIndex<PerstAssociation> associationIndex, final FieldIndex<PerstFeature> featureIndex, final FieldIndex<PerstCommit> commitIndex, final FieldIndex<PerstVariant> variantIndex, final FieldIndex<PerstRemote> remoteIndex) {
		checkNotNull(commitIndex);
		checkNotNull(variantIndex);
		checkNotNull(remoteIndex);

		this.commitIndex = commitIndex;
		this.variantIndex = variantIndex;
		this.remoteIndex = remoteIndex;

		this.repository = new PerstRepository();
	}


	public int nextCommitId() {
		this.currentCommitId++;
		this.modify();
		return this.currentCommitId;
	}


	public FieldIndex<PerstRemote> getRemoteIndex() {
		return this.remoteIndex;
	}

	public FieldIndex<PerstCommit> getCommitIndex() {
		return this.commitIndex;
	}

	public FieldIndex<PerstVariant> getVariantIndex() {
		return this.variantIndex;
	}


	public Repository.Op getRepository() {
		return this.repository;
	}

}
