package at.jku.isse.ecco.storage.perst.dao;

import at.jku.isse.ecco.core.Remote;
import at.jku.isse.ecco.dao.RemoteDao;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.storage.perst.core.PerstRemote;
import com.google.inject.Inject;
import org.garret.perst.FieldIndex;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class PerstRemoteDao extends PerstAbstractGenericDao<Feature> implements RemoteDao {

	private final PerstEntityFactory entityFactory;


	@Inject
	public PerstRemoteDao(PerstTransactionStrategy transactionStrategy, final PerstEntityFactory entityFactory) {
		super(transactionStrategy);

		checkNotNull(entityFactory);

		this.entityFactory = entityFactory;
	}


	@Override
	public Collection<Remote> loadAllRemotes() {
		final DatabaseRoot root = this.transactionStrategy.getDatabaseRoot();

		final Set<Remote> remotes = new LinkedHashSet<>(root.getRemoteIndex());

		this.transactionStrategy.done();

		return remotes;
	}

	@Override
	public Remote loadRemote(String name) {
		checkNotNull(name);
		checkArgument(!name.isEmpty(), "Expected a non-empty name but was empty.");

		final DatabaseRoot root = this.transactionStrategy.getDatabaseRoot();

		final Remote remote = root.getRemoteIndex().get(name);

		this.transactionStrategy.done();

		return remote;
	}

	@Override
	public Remote storeRemote(Remote remote) {
		checkNotNull(remote);

		final DatabaseRoot root = this.transactionStrategy.getDatabaseRoot();
		final FieldIndex<PerstRemote> remoteIndex = root.getRemoteIndex();

		final PerstRemote perstEntity = (PerstRemote) remote;

		if (!remoteIndex.contains(perstEntity)) {
			remoteIndex.put(perstEntity);
		} else {
			remoteIndex.set(perstEntity);
		}

		this.transactionStrategy.done();

		return perstEntity;
	}

	@Override
	public void removeRemote(String name) {
		checkNotNull(name);

		final DatabaseRoot root = this.transactionStrategy.getDatabaseRoot();
		final FieldIndex<PerstRemote> remoteIndex = root.getRemoteIndex();

		remoteIndex.removeKey(name);

		this.transactionStrategy.done();
	}

}
