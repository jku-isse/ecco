package at.jku.isse.ecco.plugin.artifact;

import at.jku.isse.ecco.genericAdapter.eccoModelAdapter.GenericAdapterModule;
import com.google.inject.Module;

/**
 * @author Michael Jahn
 */
public class GenericAdapterPlugin extends ArtifactPlugin {

	private GenericAdapterModule module = new GenericAdapterModule();

	@Override
	public String getPluginId() {
		return GenericAdapterPlugin.class.getName();
	}

	@Override
	public Module getModule() {
		return module;
	}

	@Override
	public String getName() {
		return "Generic Adapter Plugin";
	}

	@Override
	public String getDescription() {
		return "A generic adapter plugin";
	}

}
