package at.jku.isse.ecco.adapter.lilypond;

import at.jku.isse.ecco.adapter.ArtifactPlugin;
import com.google.inject.Module;

public class LilypondPlugin extends ArtifactPlugin {

	private static final String[] fileTypes = new String[] {"ly", "ily"};

	public static String[] getFileTypes() {
		return fileTypes;
	}

	private final LilypondModule module = new LilypondModule();

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

	/** Recorded in a new repository's settings: musical tokens, unless -Decco.lilypond.musicalTokens=false. */
	public static final String MUSICAL_TOKENS_SETTING = "lilypond.musicalTokens";

	@Override
	public java.util.Map<String, String> newRepositorySettings() {
		return java.util.Map.of(MUSICAL_TOKENS_SETTING, System.getProperty("ecco.lilypond.musicalTokens", "true"));
	}

}
