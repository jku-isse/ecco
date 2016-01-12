package at.jku.isse.ecco.dao;

import at.jku.isse.ecco.artifact.PerstArtifact;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.PerstAssociation;
import at.jku.isse.ecco.module.PerstPresenceCondition;
import at.jku.isse.ecco.module.PresenceCondition;
import at.jku.isse.ecco.sequenceGraph.PerstSequenceGraph;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.tree.PerstNode;
import at.jku.isse.ecco.tree.PerstOrderedNode;
import at.jku.isse.ecco.tree.PerstRootNode;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.garret.perst.FieldIndex;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * The association dao which provides access methods for associations.
 *
 * @author Hannes Thaller
 * @version 1.0
 */
public class PerstAssociationDao extends PerstAbstractGenericDao<Association> implements AssociationDao {

	private final PerstEntityFactory entityFactory;

	/**
	 * Constructs new association dao with the given connection string which is
	 * a path to a database file or the path where a new database should be
	 * created.
	 *
	 * @param connectionString path to the database
	 * @param entityFactory    The factory which is used to create new entities.
	 */
	@Inject
	public PerstAssociationDao(@Named("connectionString") final String connectionString, final PerstEntityFactory entityFactory) {
		super(connectionString);

		checkNotNull(entityFactory);

		this.entityFactory = entityFactory;
	}

	@Override
	public List<Association> loadAllAssociations() {
		final DatabaseRoot root = this.openDatabase();

		final List<Association> result = new ArrayList<>(root.getAssociationIndex());

		this.closeDatabase();

		return result;
	}

	@Override
	public Association load(final String id) {
		checkNotNull(id);
		checkArgument(!id.isEmpty(), "Expected a non empty id.");

		final DatabaseRoot root = this.openDatabase();

		final Association perstAssociation = root.getAssociationIndex().get(id);

		this.closeDatabase();

		return perstAssociation;
	}

	@Override
	public void remove(final Association entity) {
		checkNotNull(entity);

		final DatabaseRoot root = this.openDatabase();

		root.getAssociationIndex().remove(entity);

		this.closeDatabase();
	}

	@Override
	public void remove(final String id) {
		checkNotNull(id);
		checkArgument(!id.isEmpty(), "Expected a non empty id.");

		final DatabaseRoot root = this.openDatabase();

		root.getAssociationIndex().removeKey(id);

		this.closeDatabase();
	}

	@Override
	public Association save(final Association entity) {
		checkNotNull(entity);

		//final PerstAssociation association = entityFactory.createPerstAssociation(entity);
		final PerstAssociation association = (PerstAssociation) entity; // TODO!

		final DatabaseRoot root = this.openDatabase();
		final FieldIndex<PerstAssociation> associationIndex = root.getAssociationIndex();

		if (association.getId() == 0) {
			association.setId(root.nextAssociationId());
		}

		association.store();
		this.savePresenceCondition(association.getPresenceCondition());
		this.saveNode(association.getArtifactTreeRoot());

		if (!associationIndex.contains(association)) {
			associationIndex.put(association);
		} else {
			associationIndex.set(association);
		}

		this.closeDatabase();

		return association;
	}


	private PresenceCondition savePresenceCondition(final PresenceCondition entity) {
		checkNotNull(entity);

		final PerstPresenceCondition presenceCondition = (PerstPresenceCondition) entity;

		//this.openDatabase();

		presenceCondition.storeRecursively();

		//this.closeDatabase();

		return presenceCondition;
	}

	private Node saveNode(final Node entity) {
		checkNotNull(entity);

		if (entity instanceof PerstNode) {
			//this.openDatabase();
			PerstNode node = (PerstNode) entity;
			node.store();
			for (Node child : node.getAllChildren()) {
				this.saveNode(child);
			}
			if (node.getArtifact() != null)
				((PerstArtifact) node.getArtifact()).store();
			//this.closeDatabase();
			return node;
		} else if (entity instanceof PerstOrderedNode) {
			//this.openDatabase();
			PerstOrderedNode node = (PerstOrderedNode) entity;
			node.store();
			for (Node child : node.getAllChildren()) {
				this.saveNode(child);
			}
			if (node.getArtifact() != null)
				((PerstArtifact) node.getArtifact()).store();
			if (node.getSequenceGraph() != null)
				((PerstSequenceGraph) node.getSequenceGraph()).storeRecursively();
			//this.closeDatabase();
			return node;
		} else if (entity instanceof PerstRootNode) {
			//this.openDatabase();
			PerstRootNode node = (PerstRootNode) entity;
			node.store();
			for (Node child : node.getAllChildren()) {
				this.saveNode(child);
			}
			if (node.getArtifact() != null)
				((PerstArtifact) node.getArtifact()).store();
			//this.closeDatabase();
			return node;
		}

		return null;
	}


	@Override
	public Map<Association, Map<Association, Integer>> loadDependencyMap() {
		final DatabaseRoot root = this.openDatabase();

		final Map<Association, Map<Association, Integer>> dependecyMap = root.getDependencyMap();

		this.closeDatabase();

		return dependecyMap;
	}

	@Override
	public Map<Association, Map<Association, Integer>> loadConflictsMap() {
		return null;
	}

	@Override
	public void storeDependencyMap(final Map<Association, Map<Association, Integer>> dependencyMap) {
		checkNotNull(dependencyMap);

		final DatabaseRoot root = this.openDatabase();
		root.setDependencyMap(dependencyMap);

		this.closeDatabase();
	}

	@Override
	public void storeConflictsMap(Map<Association, Map<Association, Integer>> conflictsMap) {

	}

}
