package at.jku.isse.ecco.dao;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.BaseAssociation;
import at.jku.isse.ecco.core.BaseCommit;
import at.jku.isse.ecco.core.BaseVariant;
import at.jku.isse.ecco.feature.BaseFeature;

import java.util.HashMap;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

public class Database {

	private final Map<Integer, BaseAssociation> associationIndex;
	private final Map<String, BaseFeature> featureIndex;
	private final Map<Integer, BaseCommit> commitIndex;
	private final Map<String, BaseVariant> variantIndex;

	private int currentCommitId = 0;
	private int currentAssociationId = 0;

	private final Map<Association, Map<Association, Integer>> dependencyMap = new HashMap<>();

	public Database() {
		this.associationIndex = new HashMap<>();
		this.featureIndex = new HashMap<>();
		;
		this.commitIndex = new HashMap<>();
		;
		this.variantIndex = new HashMap<>();
		;
	}

	public int nextCommitId() {
		this.currentCommitId++;
		return this.currentCommitId;
	}

	public int nextAssociationId() {
		this.currentAssociationId++;
		return this.currentAssociationId;
	}

	Map<Integer, BaseAssociation> getAssociationIndex() {
		return this.associationIndex;
	}

	Map<String, BaseFeature> getFeatureIndex() {
		return this.featureIndex;
	}

	Map<Integer, BaseCommit> getCommitIndex() {
		return this.commitIndex;
	}

	Map<String, BaseVariant> getVariantIndex() {
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
