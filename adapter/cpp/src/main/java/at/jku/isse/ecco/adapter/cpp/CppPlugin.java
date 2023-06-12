package at.jku.isse.ecco.adapter.cpp;


import at.jku.isse.ecco.adapter.ArtifactPlugin;
import com.google.inject.Module;

public class CppPlugin extends ArtifactPlugin {

	private CppModule module = new CppModule();

	@Override
	public String getPluginId() {
		return CppPlugin.class.getName();
	}

	@Override
	public Module getModule() {
		return this.module;
	}

	@Override
	public String getName() {
		return "CppArtifactPlugin";
	}

	@Override
	public String getDescription() {
		return "Cpp Artifact Plugin";
	}

}
