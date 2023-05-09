package at.jku.isse.ecco.adapter.runtime;

import at.jku.isse.ecco.adapter.ArtifactPlugin;
import com.google.inject.Module;

public class RuntimePlugin extends ArtifactPlugin {

	private RuntimeModule module = new RuntimeModule();

	@Override
	public String getPluginId() {
		return RuntimePlugin.class.getName(); //"plugin";//RuntimePlugin.class.getName();
	}

	@Override
	public Module getModule() {
		return this.module;
	}

	@Override
	public String getName() {
		return "RuntimeArtifactPlugin";
	}

	@Override
	public String getDescription() {
		return "Runtime Artifact Plugin";
	}

}
