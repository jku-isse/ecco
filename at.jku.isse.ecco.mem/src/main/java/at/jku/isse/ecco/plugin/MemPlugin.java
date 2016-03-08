package at.jku.isse.ecco.plugin;

import at.jku.isse.ecco.plugin.data.DataPlugin;
import com.google.inject.Module;

public class MemPlugin extends DataPlugin {

	private MemModule module = new MemModule();

	@Override
	public String getPluginId() {
		return "at.jku.isse.ecco.perst";
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
