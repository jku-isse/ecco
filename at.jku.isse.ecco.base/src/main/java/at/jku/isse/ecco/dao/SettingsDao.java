package at.jku.isse.ecco.dao;

import at.jku.isse.ecco.core.Remote;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.Feature;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;

public interface SettingsDao extends GenericDao<Feature> {

	public int loadMaxOrder();

	public void storeMaxOrder(int maxOrder);


	public String loadUserName();

	public void storeUserName(String committer);

	public String loadUserMailAddress();

	public void storeUserMailAddress(String committer);


	public Configuration loadCurrentCheckoutConfiguration();

	public void storeCurrentCheckoutConfiguration(Configuration configuration);


	public Collection<Remote> loadAllRemotes();

	public Remote loadRemote(String name);

	public Remote storeRemote(Remote remote);


	public void setManualMode(boolean manualMode);

	public boolean isManualMode();


	// TODO: change these to a FileInfo type that contains either the plugin that should parse the file or marks the file to be ignored or sets it to auto (to automatically choose the plugin that should load the file)!


	public Collection<Path> loadIgnoredFiles();

	public void storeIgnoredFiles(Collection<Path> ignoredFiles);


	public Map<Path, String> loadFileToPluginMap();

	public void storeFileToPluginMap(Map<Path, String> fileToPluginMap);


}
