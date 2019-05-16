package at.jku.isse.ecco.storage.neo4j.dao;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.dao.RepositoryDao;
import at.jku.isse.ecco.dao.TransactionStrategy;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.storage.neo4j.domain.*;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.tree.RootNode;
import com.google.inject.Inject;
import org.neo4j.ogm.session.LoadStrategy;
import org.neo4j.ogm.session.Session;

import java.util.Collection;
import java.util.Iterator;
import java.util.stream.Collectors;


public class NeoRepositoryDao extends NeoAbstractGenericDao implements RepositoryDao {

	@Inject
	public NeoRepositoryDao(NeoTransactionStrategy transactionStrategy) {
		super(transactionStrategy);
	}

	@Override
	public Repository.Op load() {
		final Session neoSession = this.transactionStrategy.getNeoSession();
		System.out.println(neoSession);
		System.out.println(transactionStrategy);


		neoSession.setLoadStrategy(LoadStrategy.SCHEMA_LOAD_STRATEGY);
		NeoRepository repository = neoSession.load(NeoRepository.class, 0L);
		//neoSession.setLoadStrategy(LoadStrategy.PATH_LOAD_STRATEGY);
		if (repository == null) {
			NeoRepository repo = new NeoRepository(this.transactionStrategy);
			return repo;
		} else {
			repository.setTransactionStrategy(this.transactionStrategy);

			{
				/** load features */
				Iterator<NeoFeature> it = repository.getFeatures().iterator();
				while (it.hasNext()) {
					NeoFeature actFeature = it.next();

					if (actFeature.getNeoId() != null) {
						NeoFeature loadedFeature = neoSession.load(NeoFeature.class, actFeature.getNeoId(), 3);
					}
				}
			}

			/** load associations */
			neoSession.loadAll(NeoAssociation.class, 3);
			Iterator<NeoAssociation.Op> it = repository.getAssociations().iterator();
			while(it.hasNext()) {
				NeoAssociation.Op actAssoc =  it.next();
				NeoRootNode actRootNode = (NeoRootNode) actAssoc.getRootNode();
				NeoRootNode load = neoSession.load(NeoRootNode.class, actRootNode.getNeoId(), 3);

				System.out.println();
//            NeoRootNode rootNode = (NeoRootNode) assoc.getRootNode();
//            if (rootNode.getNeoId() != null) {
//                neoSession.load(NeoRootNode.class, rootNode.getNeoId(), 2);
//            }
			}

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
