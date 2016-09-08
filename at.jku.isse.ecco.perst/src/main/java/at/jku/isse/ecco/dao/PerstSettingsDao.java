package at.jku.isse.ecco.dao;

import at.jku.isse.ecco.core.PerstRemote;
import at.jku.isse.ecco.core.Remote;
import at.jku.isse.ecco.feature.Feature;
import com.google.inject.Inject;
import org.garret.perst.FieldIndex;

import java.util.*;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class PerstSettingsDao extends PerstAbstractGenericDao<Feature> implements SettingsDao {

	private final PerstEntityFactory entityFactory;


	@Inject
	public PerstSettingsDao(PerstTransactionStrategy transactionStrategy, final PerstEntityFactory entityFactory) {
		super(transactionStrategy);

		checkNotNull(entityFactory);

		this.entityFactory = entityFactory;
	}


	@Override
	public int loadMaxOrder() {
		final DatabaseRoot root = this.transactionStrategy.getDatabaseRoot();

		return root.getMaxOrder();
	}

	@Override
	public void storeMaxOrder(int maxOrder) {
		final DatabaseRoot root = this.transactionStrategy.getDatabaseRoot();

		root.setMaxOrder(maxOrder);
	}


	@Override
	public void storeManualMode(boolean manualMode) {
		final DatabaseRoot root = this.transactionStrategy.getDatabaseRoot();

		root.setManualMode(true);

		root.store();

		this.transactionStrategy.done();
	}

	@Override
	public boolean loadManualMode() {
		final DatabaseRoot root = this.transactionStrategy.getDatabaseRoot();

		return root.isManualMode();
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


	@Override
	public Map<String, String> loadPluginMap() {
		final DatabaseRoot root = this.transactionStrategy.getDatabaseRoot();

		final Map<String, String> pluginMap = new HashMap<>();
		pluginMap.putAll(root.getPluginMap());

		this.transactionStrategy.done();

		return pluginMap;
	}

	@Override
	public void addPluginMapping(String pattern, String pluginId) {
		final DatabaseRoot root = this.transactionStrategy.getDatabaseRoot();

		root.getPluginMap().put(pattern, pluginId);
		root.store();

		this.transactionStrategy.done();
	}

	@Override
	public void removePluginMapping(String pattern) {
		final DatabaseRoot root = this.transactionStrategy.getDatabaseRoot();

		root.getPluginMap().remove(pattern);
		root.store();

		this.transactionStrategy.done();
	}

	@Override
	public Set<String> loadIgnorePatterns() {
		final DatabaseRoot root = this.transactionStrategy.getDatabaseRoot();

		final Set<String> ignorePatterns = new HashSet<>();
		ignorePatterns.addAll(root.getIgnorePatterns());

		this.transactionStrategy.done();

		return ignorePatterns;
	}

	@Override
	public void addIgnorePattern(String ignorePattern) {
		final DatabaseRoot root = this.transactionStrategy.getDatabaseRoot();

		root.getIgnorePatterns().add(ignorePattern);
		root.store();

		this.transactionStrategy.done();
	}

	@Override
	public void removeIgnorePattern(String ignorePattern) {
		final DatabaseRoot root = this.transactionStrategy.getDatabaseRoot();

		root.getIgnorePatterns().remove(ignorePattern);
		root.store();

		this.transactionStrategy.done();
	}

}
