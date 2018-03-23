package at.jku.isse.ecco.storage.ser;

import at.jku.isse.ecco.storage.StoragePlugin;
import com.google.inject.Module;

public class SerPlugin extends StoragePlugin {

	private SerModule module = new SerModule();

	@Override
	public String getPluginId() {
		return "at.jku.isse.ecco.storage.ser";
	}

	@Override
	public Module getModule() {
		return this.module;
	}

	@Override
	public String getName() {
		return "SerPlugin";
	}

	@Override
	public String getDescription() {
		return "Serialization Plugin";
	}

}
