package at.jku.isse.ecco.dao;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.artifact.PerstArtifact;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.PerstAssociation;
import at.jku.isse.ecco.module.PerstPresenceCondition;
import at.jku.isse.ecco.module.PresenceCondition;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.tree.PerstNode;
import at.jku.isse.ecco.tree.PerstRootNode;
import com.google.inject.Inject;
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
	 * @param transactionStrategy The transaction strategy.
	 * @param entityFactory       The factory which is used to create new entities.
	 */
	@Inject
	public PerstAssociationDao(PerstTransactionStrategy transactionStrategy, final PerstEntityFactory entityFactory) {
		super(transactionStrategy);

		checkNotNull(entityFactory);

		this.entityFactory = entityFactory;
	}

	@Override
	public List<Association> loadAllAssociations() throws EccoException {
		//final DatabaseRoot root = this.openDatabase();
		final DatabaseRoot root = this.transactionStrategy.getDatabaseRoot();

		final List<Association> result = new ArrayList<>(root.getAssociationIndex());

		//this.closeDatabase();
		this.transactionStrategy.done();

		return result;
	}

	@Override
	public Association load(final String id) throws EccoException {
		checkNotNull(id);
		checkArgument(!id.isEmpty(), "Expected a non empty id.");

		//final DatabaseRoot root = this.openDatabase();
		final DatabaseRoot root = this.transactionStrategy.getDatabaseRoot();

		final Association perstAssociation = root.getAssociationIndex().get(id);

		//this.closeDatabase();
		this.transactionStrategy.done();

		return perstAssociation;
	}

	@Override
	public void remove(final Association entity) throws EccoException {
		checkNotNull(entity);

		//final DatabaseRoot root = this.openDatabase();
		final DatabaseRoot root = this.transactionStrategy.getDatabaseRoot();

		root.getAssociationIndex().remove(entity);

		//this.closeDatabase();
		this.transactionStrategy.done();
	}

	@Override
	public void remove(final String id) throws EccoException {
		checkNotNull(id);
		checkArgument(!id.isEmpty(), "Expected a non empty id.");

		//final DatabaseRoot root = this.openDatabase();
		final DatabaseRoot root = this.transactionStrategy.getDatabaseRoot();

		root.getAssociationIndex().removeKey(id);

		//this.closeDatabase();
		this.transactionStrategy.done();
	}

	@Override
	public Association save(final Association entity) throws EccoException {
		checkNotNull(entity);

		//final PerstAssociation association = entityFactory.createPerstAssociation(entity);
		final PerstAssociation association = (PerstAssociation) entity; // TODO!

		//final DatabaseRoot root = this.openDatabase();
		final DatabaseRoot root = this.transactionStrategy.getDatabaseRoot();
		final FieldIndex<PerstAssociation> associationIndex = root.getAssociationIndex();

		if (association.getId() == 0) {
			association.setId(root.nextAssociationId());
		}

		association.store();
		this.savePresenceCondition(association.getPresenceCondition());
		this.saveNode(association.getRootNode());

		if (!associationIndex.contains(association)) {
			associationIndex.put(association);
		} else {
			associationIndex.set(association);
		}

		//this.closeDatabase();
		this.transactionStrategy.done();

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
			for (Node child : node.getChildren()) {
				this.saveNode(child);
			}
			if (node.getArtifact() != null)
				((PerstArtifact) node.getArtifact()).store();
			//this.closeDatabase();
			return node;
		} else if (entity instanceof PerstRootNode) {
			//this.openDatabase();
			PerstRootNode node = (PerstRootNode) entity;
			node.store();
			for (Node child : node.getChildren()) {
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
	public Map<Association, Map<Association, Integer>> loadDependencyMap() throws EccoException {
		//final DatabaseRoot root = this.openDatabase();
		final DatabaseRoot root = this.transactionStrategy.getDatabaseRoot();

		final Map<Association, Map<Association, Integer>> dependecyMap = root.getDependencyMap();

		//this.closeDatabase();
		this.transactionStrategy.done();

		return dependecyMap;
	}

	@Override
	public Map<Association, Map<Association, Integer>> loadConflictsMap() {
		return null;
	}

	@Override
	public void storeDependencyMap(final Map<Association, Map<Association, Integer>> dependencyMap) throws EccoException {
		checkNotNull(dependencyMap);

		//final DatabaseRoot root = this.openDatabase();
		final DatabaseRoot root = this.transactionStrategy.getDatabaseRoot();
		root.setDependencyMap(dependencyMap);

		//this.closeDatabase();
		this.transactionStrategy.done();
	}

	@Override
	public void storeConflictsMap(Map<Association, Map<Association, Integer>> conflictsMap) {

	}

}
