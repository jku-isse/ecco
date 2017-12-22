package at.jku.isse.ecco.plugin;

import at.jku.isse.ecco.plugin.storage.DataPlugin;
import com.google.inject.Module;


public class JpaPlugin extends DataPlugin {

	private JpaModule module = new JpaModule();

	@Override
	public String getPluginId() {
		return "at.jku.isse.ecco.jpa";
	}

	@Override
	public Module getModule() {
		return this.module;
	}

	@Override
	public String getName() {
		return "PerstPlugin";
	}

	@Override
	public String getDescription() {
		return "Perst Plugin";
	}

}
