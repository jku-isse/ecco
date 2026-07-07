package at.jku.isse.ecco.adapter.typescript;

import at.jku.isse.ecco.adapter.ArtifactPlugin;
import com.google.inject.Module;

public class TypeScriptPlugin extends ArtifactPlugin {

	private static final String[] fileTypes = new String[]{"ts"};//, "c", "h", "cpp", "hpp"};

	private TypeScriptModule module = new TypeScriptModule();

	public String[] getFileTypes() {
		return fileTypes;
	}

	@Override
	public String getPluginId() {
		return TypeScriptPlugin.class.getName();
	}

	@Override
	public Module getModule() {
		return this.module;
	}

	@Override
	public String getName() {
		return "TypeScriptArtifactPlugin";
	}

	@Override
	public String getDescription() {
		return "TypeScript Artifact Plugin";
	}

}
