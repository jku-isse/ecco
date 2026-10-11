package at.jku.isse.ecco.adapter.lilypond.parser.py4j;

import at.jku.isse.ecco.adapter.PythonFinder;
import at.jku.isse.ecco.service.LilypondPreferences;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * The python that runs LilypondParser_2.py, which reads with lymodel's tree-sitter parse: found by
 * {@link PythonFinder} - the python set in Preferences, else the first of python, python3 and the
 * usual install locations that imports tree-sitter, py4j and lymodel. lymodel is the one installed
 * into that python, or the lilypond-idea-plugin's python/ directory set in Preferences or in LYPYTHON.
 */
public final class LilypondPython {

	/** What LilypondParser_2.py imports, whatever the repository's settings. */
	static final List<String> MODULES = List.of("tree_sitter", "py4j", "lymodel.lybar.grammar", "lymodel.lilydata",
			"lymodel.lybar.normalize");

	/** The python to run, and the lymodel directory to hand it as LYPYTHON (null: the installed one, if any). */
	public record Choice(String python, Path lymodelDir) {
	}

	private LilypondPython() {
	}

	/**
	 * The python for reading LilyPond.
	 *
	 * @throws at.jku.isse.ecco.EccoException naming every python tried and what it lacks, when none will do
	 */
	public static Choice resolve() {
		Path lymodelDir = lymodelDir(LilypondPreferences.getLymodelPath(), System.getenv("LYPYTHON"));
		String python = PythonFinder.find(LilypondPreferences.getPythonPath(), MODULES, lymodelDir, "reads LilyPond",
				advice(lymodelDir));
		return new Choice(python, lymodelDir);
	}

	/** Forgets what worked - a parse failed with it, so the next one looks again. */
	public static void forget() {
		PythonFinder.forget();
	}

	static String advice(Path lymodelDir) {
		return (lymodelDir == null
				? "lymodel: looked for installed only (no lymodel directory set in Preferences or LYPYTHON).\n"
				: "lymodel: looked for in " + lymodelDir + " and installed.\n")
				+ "Install what is missing into one of them (python -m pip install tree-sitter py4j,"
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
