package at.jku.isse.ecco.dao;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.core.BaseAssociation;
import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

public class MemAssociationDao extends MemAbstractGenericDao<Association> implements AssociationDao {

	private final MemEntityFactory entityFactory;

	@Inject
	public MemAssociationDao(MemTransactionStrategy transactionStrategy, final MemEntityFactory entityFactory) {
		super(transactionStrategy);

		checkNotNull(entityFactory);

		this.entityFactory = entityFactory;
	}

	@Override
	public List<Association> loadAllAssociations() throws EccoException {
		final Database root = this.transactionStrategy.getDatabase();

		final List<Association> associations = new ArrayList<>(root.getAssociationIndex().values());

		return associations;
	}

	@Override
	public Map<Association, Map<Association, Integer>> loadDependencyMap() throws EccoException {
		return null; // TODO
	}

	@Override
	public Map<Association, Map<Association, Integer>> loadConflictsMap() throws EccoException {
		return null; // TODO
	}

	@Override
	public void storeDependencyMap(Map<Association, Map<Association, Integer>> dependencyMap) throws EccoException {
		// TODO
	}

	@Override
	public void storeConflictsMap(Map<Association, Map<Association, Integer>> conflictsMap) throws EccoException {
		// TODO
	}

	@Override
	public Association load(String id) throws EccoException {
		final Database root = this.transactionStrategy.getDatabase();

		return root.getAssociationIndex().get(id);
	}

	@Override
	public void remove(String id) throws EccoException {
		final Database root = this.transactionStrategy.getDatabase();

		root.getAssociationIndex().remove(id);
	}

	@Override
	public void remove(Association entity) throws EccoException {
		final Database root = this.transactionStrategy.getDatabase();

		root.getAssociationIndex().remove(entity.getId());
	}

	@Override
	public Association save(Association entity) throws EccoException {
		final Database root = this.transactionStrategy.getDatabase();

		final BaseAssociation baseEntity = (BaseAssociation) entity;

		if (!root.getAssociationIndex().containsKey(baseEntity.getId())) {
			baseEntity.setId(root.nextAssociationId());
		}

		root.getAssociationIndex().put(baseEntity.getId(), baseEntity);

		return baseEntity;
	}

}
