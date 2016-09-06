package at.jku.isse.ecco.dao;

import at.jku.isse.ecco.core.*;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.PerstFeature;
import org.garret.perst.FieldIndex;
import org.garret.perst.Persistent;

import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
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


	private int maxOrder = 5;
	private String userName = "";
	private String userMailAddress = "";

	private Configuration currentCheckoutConfiguration = null;

	private boolean manualMode = false;

	public boolean isManualMode() {
		return this.manualMode;
	}

	public void setManualMode(boolean manualMode) {
		this.manualMode = manualMode;
	}

	private final FieldIndex<PerstRemote> remoteIndex;

	private final Collection<Path> ignoredFiles = new HashSet<Path>();

	private final Map<Path, String> fileToPluginMap = new HashMap<Path, String>();


	/**
	 * Constructs a new DatabaseRoot with the given indexers.
	 *
	 * @param associationIndex used to index {@link PerstAssociation}
	 * @param featureIndex     used to index {@link PerstFeature}
	 * @param commitIndex      used to index {@link PerstCommit}
	 * @param variantIndex     used to index {@link PerstVariant}
	 */
	public DatabaseRoot(final FieldIndex<PerstAssociation> associationIndex, final FieldIndex<PerstFeature> featureIndex, final FieldIndex<PerstCommit> commitIndex, final FieldIndex<PerstVariant> variantIndex, final FieldIndex<PerstRemote> remoteIndex) {
		checkNotNull(associationIndex);
		checkNotNull(featureIndex);
		checkNotNull(commitIndex);
		checkNotNull(variantIndex);

		this.associationIndex = associationIndex;
		this.featureIndex = featureIndex;
		this.commitIndex = commitIndex;
		this.variantIndex = variantIndex;
		this.remoteIndex = remoteIndex;
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
	public FieldIndex<PerstAssociation> getAssociationIndex() {
		return this.associationIndex;
	}

	/**
	 * Returns the indexer that stores {@link PerstFeature}s.
	 *
	 * @return {@link PerstFeature} indexer
	 */
	public FieldIndex<PerstFeature> getFeatureIndex() {
		return this.featureIndex;
	}

	public FieldIndex<PerstCommit> getCommitIndex() {
		return this.commitIndex;
	}

	public FieldIndex<PerstVariant> getVariantIndex() {
		return this.variantIndex;
	}


	public int getMaxOrder() {
		return maxOrder;
	}

	public void setMaxOrder(int maxOrder) {
		this.maxOrder = maxOrder;
	}

	public String getUserName() {
		return userName;
	}

	public void setUserName(String userName) {
		this.userName = userName;
	}

	public String getUserMailAddress() {
		return userMailAddress;
	}

	public void setUserMailAddress(String userMailAddress) {
		this.userMailAddress = userMailAddress;
	}

	public Configuration getCurrentCheckoutConfiguration() {
		return currentCheckoutConfiguration;
	}

	public void setCurrentCheckoutConfiguration(Configuration currentCheckoutConfiguration) {
		this.currentCheckoutConfiguration = currentCheckoutConfiguration;
	}


	public FieldIndex<PerstRemote> getRemoteIndex() {
		return this.remoteIndex;
	}


	// TODO: figure out how to best deal with this
	public Collection<Path> getIgnoredFiles() {
		return ignoredFiles;
	}

	public Map<Path, String> getFileToPluginMap() {
		return fileToPluginMap;
	}


	// TODO: probably remove this.
	private final Map<Association, Map<Association, Integer>> dependencyMap = new HashMap<>();

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
