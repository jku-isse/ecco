package at.jku.isse.ecco.service;

import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Persists the user-configurable Lilypond executable path and its {@code -I} include search
 * paths (see {@code at.jku.isse.ecco.adapter.lilypond.LilypondCompiler} in the adapter-lilypond
 * module). Blank/empty by default, in which case the compiler falls back to the bundled
 * {@code lilypond-config.properties} classpath resource. Same small, per-user runtime setting
 * pattern as {@link AdapterPreferences}/{@link LlmPreferences} (backed by {@link Preferences})
 * rather than the bundled properties file, which is packaged deployment config, not something a
 * user edits at runtime.
 *
 * <p>Also the python that runs parce and the lymodel directory (the lilypond-idea-plugin's python/)
 * it reads musical tokens with - blank by default, in which case the adapter looks for them itself
 * (see {@code at.jku.isse.ecco.adapter.lilypond.parce.py4j.ParcePython}).
 */
public final class LilypondPreferences {

	private static final String EXECUTABLE_PATH_KEY = "lilypondExecutablePath";
	private static final String SEARCH_PATHS_KEY = "lilypondSearchPaths";
	private static final String PYTHON_PATH_KEY = "lilypondPythonPath";
	private static final String LYMODEL_PATH_KEY = "lilypondLymodelPath";
	private static final String SEPARATOR = "|";

	private LilypondPreferences() {
	}

	/** Blank by default, deliberately - falls back to the bundled lilypond-config.properties value when unset. */
	public static String getExecutablePath() {
		return prefs().get(EXECUTABLE_PATH_KEY, "");
	}

	public static void setExecutablePath(String executablePath) {
		prefs().put(EXECUTABLE_PATH_KEY, executablePath == null ? "" : executablePath.trim());
	}

	/** Empty by default, deliberately - falls back to the bundled lilypond-config.properties value when unset. */
	public static List<String> getSearchPaths() {
		String stored = prefs().get(SEARCH_PATHS_KEY, "");
		if (stored.isBlank()) {
			return List.of();
		}
		List<String> paths = new ArrayList<>();
		for (String path : stored.split("\\" + SEPARATOR)) {
			if (!path.isBlank()) {
				paths.add(path.trim());
			}
		}
		return paths;
	}

	public static void setSearchPaths(List<String> searchPaths) {
		prefs().put(SEARCH_PATHS_KEY, searchPaths == null ? "" : String.join(SEPARATOR, searchPaths));
	}

	/** Blank by default, deliberately - the adapter then tries python, python3 and the usual install locations. */
	public static String getPythonPath() {
		return prefs().get(PYTHON_PATH_KEY, "");
	}

	public static void setPythonPath(String pythonPath) {
		prefs().put(PYTHON_PATH_KEY, pythonPath == null ? "" : pythonPath.trim());
	}

	/** Blank by default, deliberately - the adapter then uses LYPYTHON, or the lymodel installed into its python. */
	public static String getLymodelPath() {
		return prefs().get(LYMODEL_PATH_KEY, "");
	}

	public static void setLymodelPath(String lymodelPath) {
		prefs().put(LYMODEL_PATH_KEY, lymodelPath == null ? "" : lymodelPath.trim());
	}

	private static Preferences prefs() {
		return Preferences.userNodeForPackage(LilypondPreferences.class);
	}

}