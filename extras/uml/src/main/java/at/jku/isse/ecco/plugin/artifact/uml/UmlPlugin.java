package at.jku.isse.ecco.plugin.artifact.uml;

import at.jku.isse.ecco.plugin.artifact.ArtifactPlugin;
import com.google.inject.Module;

public class UmlPlugin extends ArtifactPlugin {

	private static final String[] fileTypes = new String[]{"xmi"};

	private UmlModule module = new UmlModule();

	public String[] getFileTypes() {
		return fileTypes;
	}

	@Override
	public String getPluginId() {
		return UmlPlugin.class.getName();
	}

	@Override
	public Module getModule() {
		return this.module;
	}

	@Override
	public String getName() {
		return "UmlArtifactPlugin";
	}

	@Override
	public String getDescription() {
		return "UML Artifact Plugin";
	}

}
