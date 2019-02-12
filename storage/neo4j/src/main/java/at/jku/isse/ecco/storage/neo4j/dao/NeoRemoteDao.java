package at.jku.isse.ecco.storage.neo4j.dao;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.core.Remote;
import at.jku.isse.ecco.dao.RemoteDao;
import at.jku.isse.ecco.dao.TransactionStrategy;
import at.jku.isse.ecco.storage.mem.core.MemRemote;
import at.jku.isse.ecco.storage.mem.dao.Database;
import at.jku.isse.ecco.storage.neo4j.domain.NeoDatabase;
import at.jku.isse.ecco.storage.neo4j.domain.NeoRemote;
import com.google.inject.Inject;
import org.neo4j.ogm.cypher.ComparisonOperator;
import org.neo4j.ogm.cypher.Filter;
import org.neo4j.ogm.cypher.Filters;
import org.neo4j.ogm.session.Session;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class NeoRemoteDao extends NeoAbstractGenericDao implements RemoteDao {

	@Inject
	public NeoRemoteDao(NeoTransactionStrategy transactionStrategy) {
		super(transactionStrategy);
	}


	@Override
	public Collection<Remote> loadAllRemotes() {
		final Session neoSession = this.transactionStrategy.getNeoSession();
		final Collection<Remote> remotes = new ArrayList<>(neoSession.loadAll(NeoRemote.class));

		return remotes;
	}

	@Override
	public Remote loadRemote(String name) {
		checkNotNull(name);
		checkArgument(!name.isEmpty(), "Expected a non-empty name but was empty.");

		final Session neoSession = this.transactionStrategy.getNeoSession();

		// prepare statement
		Map<String, Object> params = new HashMap<>(1);
		params.put ("name", name);
		String query = "MATCH(n:NeoRemote {name:$name}) " +
						"RETURN n";

		return neoSession.queryForObject(NeoRemote.class, query, params);
	}

	@Override
	public Remote storeRemote(Remote remote) {
		checkNotNull(remote);

		if (this.transactionStrategy.getTransaction() != TransactionStrategy.TRANSACTION.READ_WRITE)
			throw new EccoException("Attempted to store remote without active READ_WRITE transaction.");

		final Session neoSession = this.transactionStrategy.getNeoSession();
		neoSession.save(remote);
		return remote;
	}

	@Override
	public void removeRemote(String name) {
		checkNotNull(name);

		if (this.transactionStrategy.getTransaction() != TransactionStrategy.TRANSACTION.READ_WRITE)
			throw new EccoException("Attempted to remove remote without active READ_WRITE transaction.");

		final Session neoSession = this.transactionStrategy.getNeoSession();

		Filters composite = new Filters();
		Filter filter = new Filter("name", ComparisonOperator.EQUALS, name);
		composite.add(filter);

		neoSession.delete(NeoRemote.class, composite, false);
	}

}
