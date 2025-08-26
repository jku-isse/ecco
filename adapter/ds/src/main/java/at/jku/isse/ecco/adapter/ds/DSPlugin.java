package at.jku.isse.ecco.adapter.ds;

import at.jku.isse.ecco.adapter.ArtifactPlugin;
import com.google.inject.Module;

public class DSPlugin extends ArtifactPlugin {

	private static final String[] fileTypes = new String[]{"txt", "xml", "tex", "java"};//, "c", "h", "cpp", "hpp"};

	private at.jku.isse.ecco.adapter.ds.DSModule module = new at.jku.isse.ecco.adapter.ds.DSModule();

	public String[] getFileTypes() {
		return fileTypes;
	}

	@Override
	public String getPluginId() {
		return DSPlugin.class.getName();
	}

	@Override
	public Module getModule() {
		return this.module;
	}

	@Override
	public String getName() {
		return "DSArtifactPlugin";
	}

	@Override
	public String getDescription() {
		return "DesignSpace Artifact Plugin";
	}

}
