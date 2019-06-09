package at.jku.isse.ecco.storage.perst;

import at.jku.isse.ecco.storage.StoragePlugin;
import com.google.inject.Module;

public class PerstPlugin extends StoragePlugin {

	private PerstModule module = new PerstModule();

	@Override
	public String getPluginId() {
		return "at.jku.isse.ecco.storage.perst";
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
