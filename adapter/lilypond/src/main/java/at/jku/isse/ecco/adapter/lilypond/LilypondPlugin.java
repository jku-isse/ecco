package at.jku.isse.ecco.adapter.lilypond;

import at.jku.isse.ecco.adapter.ArtifactPlugin;
import com.google.inject.Module;

public class LilypondPlugin extends ArtifactPlugin {

	private static final String[] fileTypes = new String[] {"ly", "ily"};

	public static String[] getFileTypes() {
		return fileTypes;
	}

	private LilypondModule module = new LilypondModule();

	@Override
	public String getPluginId() {
		return LilypondPlugin.class.getName();
	}

	@Override
	public Module getModule() {
		return this.module;
	}

	@Override
	public String getName() {
		return "LilypondArtifactPlugin";
	}

	@Override
	public String getDescription() {
		return "Lilypond Artifact Plugin";
	}

}
