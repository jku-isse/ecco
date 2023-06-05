package at.jku.isse.ecco.storage.jackson;

import at.jku.isse.ecco.storage.StoragePlugin;
import com.google.inject.Module;

public class JacksonPlugin extends StoragePlugin {

	private JacksonModule module = new JacksonModule();

	@Override
	public String getPluginId() {
		return "at.jku.isse.ecco.storage.jackson";
	}

	@Override
	public Module getModule() {
		return this.module;
	}

	@Override
	public String getName() {
		return "JacksonPlugin";
	}

	@Override
	public String getDescription() {
		return "Jackson Plugin";
	}

}
