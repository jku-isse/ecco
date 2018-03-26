package at.jku.isse.ecco.storage.ser.dao;

import at.jku.isse.ecco.core.Remote;
import at.jku.isse.ecco.dao.SettingsDao;
import com.google.inject.Inject;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class SerSettingsDao extends SerAbstractGenericDao implements SettingsDao {

	@Inject
	public SerSettingsDao(SerTransactionStrategy transactionStrategy) {
		super(transactionStrategy);
	}

	@Override
	public Collection<Remote> loadAllRemotes() {
		return null;
	}

	@Override
	public Remote loadRemote(String name) {
		return null;
	}

	@Override
	public Remote storeRemote(Remote remote) {
		return null;
	}

	@Override
	public void removeRemote(String name) {

	}

	@Override
	public Map<String, String> loadPluginMap() {
		return null;
	}

	@Override
	public void addPluginMapping(String pattern, String pluginId) {

	}

	@Override
	public void removePluginMapping(String pattern) {

	}

	@Override
	public Set<String> loadIgnorePatterns() {
		return null;
	}

	@Override
	public void addIgnorePattern(String ignorePattern) {

	}

	@Override
	public void removeIgnorePattern(String ignorePattern) {

	}

	@Override
	public void open() {

	}

	@Override
	public void close() {

	}

	@Override
	public void init() {

	}

}
