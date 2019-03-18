package at.jku.isse.ecco.storage.neo4j.dao;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.dao.RepositoryDao;
import at.jku.isse.ecco.dao.TransactionStrategy;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.storage.neo4j.domain.NeoRepository;
import com.google.inject.Inject;
import org.neo4j.ogm.session.Session;


public class NeoRepositoryDao extends NeoAbstractGenericDao implements RepositoryDao {

	@Inject
	public NeoRepositoryDao(NeoTransactionStrategy transactionStrategy) {
		super(transactionStrategy);
	}

	@Override
	public Repository.Op load() {
		final Session neoSession = this.transactionStrategy.getNeoSession();

		NeoRepository repository = neoSession.load(NeoRepository.class, 0L);
		if (repository == null) {
			NeoRepository repo = new NeoRepository(this.transactionStrategy);
			return repo;
		} else {
//			repository.features.putAll(neoSession.loadAll(NeoFeature.class, 4).stream().collect(Collectors.toMap(NeoFeature::getId, feature -> feature)));
//			repository.getAssociations().addAll((neoSession.loadAll(NeoAssociation.class, 4)));
//			repository.setTransactionStrategy(this.transactionStrategy);
//			repository.getFeatures();
			return repository;
		}
	}

	@Override
	public void store(Repository.Op repository) {
		if (this.transactionStrategy.getTransaction() != TransactionStrategy.TRANSACTION.READ_WRITE)
			throw new EccoException("Attempted to store repository without active READ_WRITE transaction.");

		final Session neoSession = this.transactionStrategy.getNeoSession();
		neoSession.save(repository);
	}

}
