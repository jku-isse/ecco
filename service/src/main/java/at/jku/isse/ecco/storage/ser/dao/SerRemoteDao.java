package at.jku.isse.ecco.storage.ser.dao;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.core.Remote;
import at.jku.isse.ecco.dao.RemoteDao;
import at.jku.isse.ecco.dao.TransactionStrategy;
import at.jku.isse.ecco.storage.mem.core.MemRemote;
import at.jku.isse.ecco.storage.mem.dao.Database;
import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class SerRemoteDao extends SerAbstractGenericDao implements RemoteDao {

	@Inject
	public SerRemoteDao(SerTransactionStrategy transactionStrategy) {
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
		final Map<String, MemRemote> remoteIndex = root.getRemoteIndex();

		final MemRemote memEntity = (MemRemote) remote;

		remoteIndex.put(memEntity.getName(), memEntity);

		return memEntity;
	}

	@Override
	public void removeRemote(String name) {
		checkNotNull(name);

		if (this.transactionStrategy.getTransaction() != TransactionStrategy.TRANSACTION.READ_WRITE)
			throw new EccoException("Attempted to remove remote without active READ_WRITE transaction.");

		final Database root = this.transactionStrategy.getDatabase();
		final Map<String, MemRemote> remoteIndex = root.getRemoteIndex();

		remoteIndex.remove(name);
	}

}
