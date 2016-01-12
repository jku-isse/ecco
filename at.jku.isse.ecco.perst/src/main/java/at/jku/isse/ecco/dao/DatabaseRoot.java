package at.jku.isse.ecco.dao;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.PerstAssociation;
import at.jku.isse.ecco.core.PerstCommit;
import at.jku.isse.ecco.core.PerstVariant;
import at.jku.isse.ecco.feature.PerstFeature;
import org.garret.perst.FieldIndex;
import org.garret.perst.Persistent;

import java.util.HashMap;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * The root object of the persisted object which contains indexers for the different types.
 *
 * @author Hannes Thaller
 * @version 1.0
 */
public class DatabaseRoot extends Persistent {

	private final FieldIndex<PerstAssociation> associationIndex;
	private final FieldIndex<PerstFeature> featureIndex;
	private final FieldIndex<PerstCommit> commitIndex;
	private final FieldIndex<PerstVariant> variantIndex;

	private int currentCommitId = 0;
	private int currentAssociationId = 0;

	private final Map<Association, Map<Association, Integer>> dependencyMap = new HashMap<>();

	/**
	 * Constructs a new DatabaseRoot with the given indexers.
	 *
	 * @param associationIndex used to index {@link PerstAssociation}
	 * @param featureIndex     used to index {@link PerstFeature}
	 * @param commitIndex      used to index {@link PerstCommit}
	 * @param variantIndex     used to index {@link PerstVariant}
	 */
	public DatabaseRoot(final FieldIndex<PerstAssociation> associationIndex, final FieldIndex<PerstFeature> featureIndex, final FieldIndex<PerstCommit> commitIndex, final FieldIndex<PerstVariant> variantIndex) {
		checkNotNull(associationIndex);
		checkNotNull(featureIndex);
		checkNotNull(commitIndex);
		checkNotNull(variantIndex);

		this.associationIndex = associationIndex;
		this.featureIndex = featureIndex;
		this.commitIndex = commitIndex;
		this.variantIndex = variantIndex;
	}

	public int nextCommitId() {
		this.currentCommitId++;
		this.modify();
		return this.currentCommitId;
	}

	public int nextAssociationId() {
		this.currentAssociationId++;
		this.modify();
		return this.currentAssociationId;
	}

	/**
	 * Returns the indexer that stores {@link PerstAssociation}s.
	 *
	 * @return {@link PerstAssociation} indexer
	 */
	FieldIndex<PerstAssociation> getAssociationIndex() {
		return this.associationIndex;
	}

	/**
	 * Returns the indexer that stores {@link PerstFeature}s.
	 *
	 * @return {@link PerstFeature} indexer
	 */
	FieldIndex<PerstFeature> getFeatureIndex() {
		return this.featureIndex;
	}

	FieldIndex<PerstCommit> getCommitIndex() {
		return this.commitIndex;
	}

	FieldIndex<PerstVariant> getVariantIndex() {
		return this.variantIndex;
	}

	/**
	 * Returns the dependency map.
	 *
	 * @return The dependency map.
	 */
	Map<Association, Map<Association, Integer>> getDependencyMap() {
		return dependencyMap;
	}

	/**
	 * Sets the dependency map.
	 *
	 * @param dependencyMap that should be persisted
	 */
	public void setDependencyMap(Map<Association, Map<Association, Integer>> dependencyMap) {
		checkNotNull(dependencyMap);

		this.dependencyMap.clear();
		this.dependencyMap.putAll(dependencyMap);
	}

}
