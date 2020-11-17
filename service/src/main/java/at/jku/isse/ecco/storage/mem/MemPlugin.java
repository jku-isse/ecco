package at.jku.isse.ecco.storage.mem;

import at.jku.isse.ecco.storage.StoragePlugin;
import com.google.inject.Module;

public class MemPlugin extends StoragePlugin {

	private MemModule module = new MemModule();

	@Override
	public String getPluginId() {
		return "at.jku.isse.ecco.storage.mem";
	}

	@Override
	public Module getModule() {
		return this.module;
	}

	@Override
	public String getName() {
		return "MemPlugin";
	}

	@Override
	public String getDescription() {
		return "Memory Plugin";
	}

}
