package at.jku.isse.ecco.storage.neo4j.dao;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.dao.RepositoryDao;
import at.jku.isse.ecco.dao.TransactionStrategy;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.storage.neo4j.domain.*;
import com.google.inject.Inject;
import org.neo4j.ogm.session.Session;

import java.util.Collection;


public class NeoRepositoryDao extends NeoAbstractGenericDao implements RepositoryDao {

	@Inject
	public NeoRepositoryDao(NeoTransactionStrategy transactionStrategy) {
		super(transactionStrategy);
	}

	@Override
	public Repository.Op load() {
		final Session neoSession = this.transactionStrategy.getNeoSession();

		NeoRepository repository = neoSession.load(NeoRepository.class, 0L, 1);
		if (repository == null) {
			NeoRepository repo = new NeoRepository(this.transactionStrategy);
			return repo;
		} else {
//			repository.features.putAll(neoSession.loadAll(NeoFeature.class, 4).stream().collect(Collectors.toMap(NeoFeature::getId, feature -> feature)));
//			repository.getAssociations().addAll((neoSession.loadAll(NeoAssociation.class, 4)));
//			repository.setTransactionStrategy(this.transactionStrategy);
//			System.out.println("FEATURESIZE: " + repository.getFeatures().size());
//			repository.getFeatures().forEach(f-> {
//				System.out.println("REVISIONS: " + f.getRevisions());
//			});
//			Collection<NeoFeatureRevision> featureRevisions = neoSession.loadAll(NeoFeatureRevision.class);
//			Collection<NeoRootNode> neoRootNodes = neoSession.loadAll(NeoRootNode.class);
//			Collection<NeoNode> neoNodes = neoSession.loadAll(NeoNode.class);
			repository.setTransactionStrategy(this.transactionStrategy);
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
