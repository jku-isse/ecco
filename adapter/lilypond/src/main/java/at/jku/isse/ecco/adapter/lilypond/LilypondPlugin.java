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

	/**
	 * Recorded in a new repository's settings: variable contexts - each top-level assignment a context
	 * named after its variable - unless -Decco.lilypond.variableContexts=false. See LilypondParser_1.py.
	 */
	public static final String VARIABLE_CONTEXTS_SETTING = "lilypond.variableContexts";

	/**
	 * Recorded in a new repository's settings: what reads its LilyPond - {@link #LYBAR_PARSER}, lymodel's
	 * tree-sitter parse (LilypondParser_2.py), unless -Decco.lilypond.parser=parce. A repository without
	 * it was created before and is read with parce (LilypondParser_1.py): the two parses give tokens and
	 * contexts of their own, which never match each other's.
	 */
	public static final String PARSER_SETTING = "lilypond.parser";

	/** The first version of the lymodel parse; a later one that changes tokens gets a number of its own. */
	public static final String LYBAR_PARSER = "lybar-1";

	@Override
	public java.util.Map<String, String> newRepositorySettings() {
		return java.util.Map.of(MUSICAL_TOKENS_SETTING, System.getProperty("ecco.lilypond.musicalTokens", "true"),
				VARIABLE_CONTEXTS_SETTING, System.getProperty("ecco.lilypond.variableContexts", "true"),
				PARSER_SETTING, System.getProperty("ecco.lilypond.parser", LYBAR_PARSER));
	}

}
