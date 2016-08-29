package at.jku.isse.ecco.dao;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.core.PerstRemote;
import at.jku.isse.ecco.core.Remote;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.Feature;
import com.google.inject.Inject;
import org.garret.perst.FieldIndex;

import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

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
	public Feature load(String id) throws EccoException {
		return null;
	}

	@Override
	public void remove(String id) throws EccoException {

	}

	@Override
	public void remove(Feature entity) throws EccoException {

	}

	@Override
	public Feature save(Feature entity) throws EccoException {
		return null;
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
	public String loadUserName() {
		final DatabaseRoot root = this.transactionStrategy.getDatabaseRoot();

		return root.getUserName();
	}

	@Override
	public void storeUserName(String userName) {
		final DatabaseRoot root = this.transactionStrategy.getDatabaseRoot();

		root.setUserName(userName);
	}

	@Override
	public String loadUserMailAddress() {
		final DatabaseRoot root = this.transactionStrategy.getDatabaseRoot();

		return root.getUserMailAddress();
	}

	@Override
	public void storeUserMailAddress(String userMailAddress) {
		final DatabaseRoot root = this.transactionStrategy.getDatabaseRoot();

		root.setUserMailAddress(userMailAddress);
	}

	@Override
	public Configuration loadCurrentCheckoutConfiguration() {
		final DatabaseRoot root = this.transactionStrategy.getDatabaseRoot();

		return root.getCurrentCheckoutConfiguration();
	}

	@Override
	public void storeCurrentCheckoutConfiguration(Configuration configuration) {
		final DatabaseRoot root = this.transactionStrategy.getDatabaseRoot();

		root.setCurrentCheckoutConfiguration(configuration);
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
	public Collection<Path> loadIgnoredFiles() {
		final DatabaseRoot root = this.transactionStrategy.getDatabaseRoot();

		return root.getIgnoredFiles();
	}

	@Override
	public void storeIgnoredFiles(Collection<Path> ignoredFiles) {
		final DatabaseRoot root = this.transactionStrategy.getDatabaseRoot();

		// TODO
	}

	@Override
	public Map<Path, String> loadFileToPluginMap() {
		final DatabaseRoot root = this.transactionStrategy.getDatabaseRoot();

		return root.getFileToPluginMap();
	}

	@Override
	public void storeFileToPluginMap(Map<Path, String> fileToPluginMap) {
		final DatabaseRoot root = this.transactionStrategy.getDatabaseRoot();

		// TODO
	}

}
