package at.jku.isse.ecco.storage.mem.dao;

import at.jku.isse.ecco.core.Remote;
import at.jku.isse.ecco.dao.SettingsDao;
import at.jku.isse.ecco.storage.mem.core.MemRemote;
import com.google.inject.Inject;

import java.util.*;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class MemSettingsDao extends MemAbstractGenericDao implements SettingsDao {

	@Inject
	public MemSettingsDao(MemTransactionStrategy transactionStrategy) {
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
		final Map<String, MemRemote> remoteIndex = root.getRemoteIndex();

		final MemRemote memEntity = (MemRemote) remote;

		remoteIndex.put(memEntity.getName(), memEntity);

		return memEntity;
	}

	@Override
	public void removeRemote(String name) {
		checkNotNull(name);

		final Database root = this.transactionStrategy.getDatabase();
		final Map<String, MemRemote> remoteIndex = root.getRemoteIndex();

		remoteIndex.remove(name);
	}


	@Override
	public Map<String, String> loadPluginMap() {
		final Database root = this.transactionStrategy.getDatabase();

		final Map<String, String> pluginMap = new HashMap<>();
		pluginMap.putAll(root.getPluginMap());

		return pluginMap;
	}

	@Override
	public void addPluginMapping(String pattern, String pluginId) {
		final Database root = this.transactionStrategy.getDatabase();

		root.getPluginMap().put(pattern, pluginId);
	}

	@Override
	public void removePluginMapping(String pattern) {
		final Database root = this.transactionStrategy.getDatabase();

		root.getPluginMap().remove(pattern);
	}

	@Override
	public Set<String> loadIgnorePatterns() {
		final Database root = this.transactionStrategy.getDatabase();

		final Set<String> ignorePatterns = new HashSet<>();
		ignorePatterns.addAll(root.getIgnorePatterns());

		return ignorePatterns;
	}

	@Override
	public void addIgnorePattern(String ignorePattern) {
		final Database root = this.transactionStrategy.getDatabase();

		root.getIgnorePatterns().add(ignorePattern);
	}

	@Override
	public void removeIgnorePattern(String ignorePattern) {
		final Database root = this.transactionStrategy.getDatabase();

		root.getIgnorePatterns().remove(ignorePattern);
	}

}
