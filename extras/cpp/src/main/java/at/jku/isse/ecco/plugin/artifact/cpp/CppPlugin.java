package at.jku.isse.ecco.plugin.artifact.cpp;

import at.jku.isse.ecco.plugin.artifact.ArtifactPlugin;
import com.google.inject.Module;

public class CppPlugin extends ArtifactPlugin {

	private static final String[] fileTypes = new String[]{"java"};

	private CppModule module = new CppModule();

	public String[] getFileTypes() {
		return fileTypes;
	}

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
		return "JavaArtifactPlugin";
	}

	@Override
	public String getDescription() {
		return "Java Artifact Plugin";
	}

}
