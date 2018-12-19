package at.jku.isse.ecco.storage.neo4j.dao;

import at.jku.isse.ecco.core.Remote;
import at.jku.isse.ecco.dao.RemoteDao;
import at.jku.isse.ecco.storage.neo4j.core.NeoRemote;
import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class MemRemoteDao extends MemAbstractGenericDao implements RemoteDao {

	@Inject
	public MemRemoteDao(MemTransactionStrategy transactionStrategy) {
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

		final Database root = this.transactionStrategy.getDatabase();
		final Map<String, NeoRemote> remoteIndex = root.getRemoteIndex();

		final NeoRemote memEntity = (NeoRemote) remote;

		remoteIndex.put(memEntity.getName(), memEntity);

		return memEntity;
	}

	@Override
	public void removeRemote(String name) {
		checkNotNull(name);

		final Database root = this.transactionStrategy.getDatabase();
		final Map<String, NeoRemote> remoteIndex = root.getRemoteIndex();

		remoteIndex.remove(name);
	}

}
