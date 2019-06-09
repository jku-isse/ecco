package at.jku.isse.ecco.plugin.artifact.runtime;

import at.jku.isse.ecco.plugin.artifact.ArtifactPlugin;
import com.google.inject.Module;

public class RuntimePlugin extends ArtifactPlugin {

	// private static final String[] runtimeTypes = new String[] { "" };

	private RuntimeModule module = new RuntimeModule();

	@Override
	public String getPluginId() {
		return RuntimePlugin.class.getName();
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
