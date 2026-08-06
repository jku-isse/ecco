package at.jku.isse.ecco.storage.neo4j.dao;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.dao.CommitDao;
import at.jku.isse.ecco.dao.TransactionStrategy;
import at.jku.isse.ecco.storage.neo4j.domain.NeoCommit;
import com.google.inject.Inject;
import org.neo4j.ogm.session.Session;

import java.util.ArrayList;
import java.util.List;

public class NeoCommitDao extends NeoAbstractGenericDao implements CommitDao {

	@Inject
	public NeoCommitDao(NeoTransactionStrategy transactionStrategy) {
		super(transactionStrategy);
	}

	@Override
	public List<Commit> loadAllCommits() throws EccoException {
		final Session neoSession = this.transactionStrategy.getNeoSession();
		return new ArrayList<>(neoSession.loadAll(NeoCommit.class));
	}

	@Override
	public NeoCommit load(String id) throws EccoException {
		final Session neoSession = this.transactionStrategy.getNeoSession();
		return neoSession.load(NeoCommit.class, id);
	}

	@Override
	public void remove(String id) throws EccoException {
		if (this.transactionStrategy.getTransaction() != TransactionStrategy.TRANSACTION.READ_WRITE)
			throw new EccoException("Attempted to remove commit without active READ_WRITE transaction.");

		final Session neoSession = this.transactionStrategy.getNeoSession();
		NeoCommit commit = neoSession.load(NeoCommit.class, id);
		neoSession.delete(commit);
	}

	@Override
	public void remove(Commit entity) throws EccoException {
		if (this.transactionStrategy.getTransaction() != TransactionStrategy.TRANSACTION.READ_WRITE)
			throw new EccoException("Attempted to remove commit without active READ_WRITE transaction.");

		final Session neoSession = this.transactionStrategy.getNeoSession();
		NeoCommit commit = neoSession.load(NeoCommit.class, entity.getId());
		neoSession.delete(commit);
	}

	@Override
	public Commit save(Commit entity) throws EccoException {
		final Session neoSession = this.transactionStrategy.getNeoSession();
		neoSession.save(entity);

		return entity;
	}

}
