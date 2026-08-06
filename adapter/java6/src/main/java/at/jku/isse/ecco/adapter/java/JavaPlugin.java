package at.jku.isse.ecco.adapter.java;

import at.jku.isse.ecco.adapter.ArtifactPlugin;
import com.google.inject.Module;

/**
 * The activator class controls the plug-in life cycle
 */
public class JavaPlugin extends ArtifactPlugin {

	private static final String[] fileTypes = new String[]{"java"};

	private JavaModule module = new JavaModule();

	public String[] getFileTypes() {
		return fileTypes;
	}

	@Override
	public String getPluginId() {
		return JavaPlugin.class.getName();
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
