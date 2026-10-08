package at.jku.isse.ecco.adapter.lilypond.parce.py4j;

import at.jku.isse.ecco.adapter.PythonFinder;
import at.jku.isse.ecco.service.LilypondPreferences;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * The python that runs parce, and the lymodel it reads musical tokens with: found by
 * {@link PythonFinder} - the python set in Preferences, else the first of python, python3 and the
 * usual install locations that imports parce and py4j, and lymodel too for a repository with
 * musical tokens. lymodel is the one installed into that python, or the lilypond-idea-plugin's
 * python/ directory set in Preferences or in LYPYTHON.
 */
public final class ParcePython {

	/** What reading LilyPond imports; lymodel only for musical tokens, by the module the script uses. */
	static final String PARCE = "parce", PY4J = "py4j", LYMODEL = "lymodel.lybar.normalize";

	/** The python to run, and the lymodel directory to hand it as LYPYTHON (null: the installed one, if any). */
	public record Choice(String python, Path lymodelDir) {
	}

	private ParcePython() {
	}

	/**
	 * The python for reading LilyPond, with lymodel when {@code musicalTokens}.
	 *
	 * @throws at.jku.isse.ecco.EccoException naming every python tried and what it lacks, when none will do
	 */
	public static Choice resolve(boolean musicalTokens) {
		Path lymodelDir = lymodelDir(LilypondPreferences.getLymodelPath(), System.getenv("LYPYTHON"));
		String python = PythonFinder.find(LilypondPreferences.getPythonPath(), modules(musicalTokens), lymodelDir,
				purpose(musicalTokens), advice(lymodelDir));
		return new Choice(python, lymodelDir);
	}

	/** Forgets what worked - a parse failed with it, so the next one looks again. */
	public static void forget() {
		PythonFinder.forget();
	}

	static List<String> modules(boolean musicalTokens) {
		return musicalTokens ? List.of(PARCE, PY4J, LYMODEL) : List.of(PARCE, PY4J);
	}

	static String purpose(boolean musicalTokens) {
		return musicalTokens
				? "reads LilyPond with musical tokens (lilypond.musicalTokens=true in this repository's .ecco/.settings)"
				: "reads LilyPond";
	}

	static String advice(Path lymodelDir) {
		return (lymodelDir == null
				? "lymodel: looked for installed only (no lymodel directory set in Preferences or LYPYTHON).\n"
				: "lymodel: looked for in " + lymodelDir + " and installed.\n")
				+ "Install what is missing into one of them (python -m pip install parce py4j,"
				+ " python -m pip install <lilypond-idea-plugin>/python), or set the Python and the lymodel"
				+ " directory (the plugin's python/) under Preferences > Lilypond.";
	}

	/** The plugin's python/ directory from Preferences, else from LYPYTHON - only one that holds lymodel. */
	static Path lymodelDir(String configured, String environment) {
		for (String dir : Arrays.asList(configured, environment)) {
			if (dir != null && !dir.isBlank() && Files.isDirectory(Path.of(dir.trim(), "lymodel"))) {
				return Path.of(dir.trim());
			}
		}
		return null;
	}
}
