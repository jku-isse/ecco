package at.jku.isse.ecco.plugin.data;

import com.google.inject.Module;

public class BaseDataPlugin extends DataPlugin {

	private BaseDataModule module = new BaseDataModule();

	@Override
	public String getPluginId() {
		return "at.jku.isse.ecco.base";
	}

	@Override
	public Module getModule() {
		return this.module;
	}

	@Override
	public String getName() {
		return "BaseDataPlugin";
	}

	@Override
	public String getDescription() {
		return "Base Data Plugin";
	}

}
