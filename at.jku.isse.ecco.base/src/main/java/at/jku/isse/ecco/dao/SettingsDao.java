package at.jku.isse.ecco.dao;

import at.jku.isse.ecco.core.Remote;
import at.jku.isse.ecco.feature.Feature;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public interface SettingsDao extends GenericDao<Feature> {

	public int loadMaxOrder();

	public void storeMaxOrder(int maxOrder);


	public void storeManualMode(boolean manualMode);

	public boolean loadManualMode();


	public Collection<Remote> loadAllRemotes();

	public Remote loadRemote(String name);

	public Remote storeRemote(Remote remote);

	public void removeRemote(String name);


	// #################################################################


	// map from glob patterns to plugins (string ids) to use for those files

	public Map<String, String> loadPluginMap();

	public void addPluginMapping(String pattern, String pluginId);

	public void removePluginMapping(String pattern);


	// set of glob patterns (strings) for files to ignore

	public Set<String> loadIgnorePatterns();

	public void addIgnorePattern(String ignorePattern);

	public void removeIgnorePattern(String ignorePattern);

}
