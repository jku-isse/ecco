package at.jku.isse.ecco.storage.jackson.dao;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.core.Remote;
import at.jku.isse.ecco.dao.RemoteDao;
import at.jku.isse.ecco.dao.TransactionStrategy;
import at.jku.isse.ecco.storage.jackson.core.JacksonRemote;
import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class JacksonRemoteDao extends JacksonAbstractGenericDao implements RemoteDao {

	@Inject
	public JacksonRemoteDao(JacksonTransactionStrategy transactionStrategy) {
		super(transactionStrategy);
	}


	@Override
	public Collection<Remote> loadAllRemotes() {
		final Database root = this.transactionStrategy.getDatabase();

		final Collection<Remote> remotes = new ArrayList<>(root.getRemoteIndex().values());

		return remotes;
	}

	@Override
	public Remote loadRemote(String name) {
		checkNotNull(name);
		checkArgument(!name.isEmpty(), "Expected a non-empty name but was empty.");

		final Database root = this.transactionStrategy.getDatabase();

		final Remote remote = root.getRemoteIndex().get(name);

		return remote;
	}

	@Override
	public Remote storeRemote(Remote remote) {
		checkNotNull(remote);

		if (this.transactionStrategy.getTransaction() != TransactionStrategy.TRANSACTION.READ_WRITE)
			throw new EccoException("Attempted to store remote without active READ_WRITE transaction.");

		final Database root = this.transactionStrategy.getDatabase();
		final Map<String, JacksonRemote> remoteIndex = root.getRemoteIndex();

		final JacksonRemote memEntity = (JacksonRemote) remote;

		remoteIndex.put(memEntity.getName(), memEntity);

		return memEntity;
	}

	@Override
	public void removeRemote(String name) {
		checkNotNull(name);

		if (this.transactionStrategy.getTransaction() != TransactionStrategy.TRANSACTION.READ_WRITE)
			throw new EccoException("Attempted to remove remote without active READ_WRITE transaction.");

		final Database root = this.transactionStrategy.getDatabase();
		final Map<String, JacksonRemote> remoteIndex = root.getRemoteIndex();

		remoteIndex.remove(name);
	}

}
