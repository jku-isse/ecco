package at.jku.isse.ecco.plugin.artifact.file;

import at.jku.isse.ecco.plugin.artifact.ArtifactPlugin;
import com.google.inject.Module;

public class FilePlugin extends ArtifactPlugin {

	// private static final String[] fileTypes = new String[] { "" };

	private FileModule module = new FileModule();

	@Override
	public String getPluginId() {
		return FilePlugin.class.getName();
	}

	@Override
	public Module getModule() {
		return this.module;
	}

	@Override
	public String getName() {
		return "FileArtifactPlugin";
	}

	@Override
	public String getDescription() {
		return "File Artifact Plugin";
	}

}
