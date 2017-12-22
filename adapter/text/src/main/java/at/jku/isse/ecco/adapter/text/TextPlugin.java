package at.jku.isse.ecco.adapter.text;

import at.jku.isse.ecco.adapter.ArtifactPlugin;
import com.google.inject.Module;

public class TextPlugin extends ArtifactPlugin {

	private static final String[] fileTypes = new String[]{"txt", "xml", "tex", "java", "c", "h", "cpp", "hpp"};

	private TextModule module = new TextModule();

	public String[] getFileTypes() {
		return fileTypes;
	}

	@Override
	public String getPluginId() {
		return TextPlugin.class.getName();
	}

	@Override
	public Module getModule() {
		return this.module;
	}

	@Override
	public String getName() {
		return "TextArtifactPlugin";
	}

	@Override
	public String getDescription() {
		return "Text Artifact Plugin";
	}

}
