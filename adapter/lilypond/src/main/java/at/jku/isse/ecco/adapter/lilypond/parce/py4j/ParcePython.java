package at.jku.isse.ecco.adapter.lilypond.parce.py4j;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.service.LilypondPreferences;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

/**
 * The python that runs parce, and the lymodel it reads musical tokens with. Plain {@code python}
 * from the PATH used to be the only one tried, so a GUI started from the Finder (whose PATH has no
 * MacPorts or Homebrew), or a {@code python} other than the one parce and lymodel were installed
 * into, failed the commit. Now: the python set in Preferences if there is one, else the first of
 * {@code python}, {@code python3} and the usual install locations that imports parce and py4j -
 * and lymodel too, for a repository with musical tokens. lymodel is the one installed into that
 * python, or the lilypond-idea-plugin's python/ directory set in Preferences or in LYPYTHON.
 *
 * <p>Only what worked is remembered: whatever is installed or set after a failure is found by the
 * next commit, without restarting ECCO.
 */
public final class ParcePython {

	/** Modules a python is asked about; parce and py4j are always needed, lymodel for musical tokens. */
	static final String PARCE = "parce", PY4J = "py4j", LYMODEL = "lymodel";

	private static final List<String> WELL_KNOWN = List.of(
			"/opt/local/bin/python3", "/opt/local/bin/python",
			"/opt/homebrew/bin/python3",
			"/usr/local/bin/python3",
			"/Library/Frameworks/Python.framework/Versions/Current/bin/python3",
			"/usr/bin/python3");

	private static final String PROBE = String.join("\n",
			"import os, sys",
			"found = []",
			"for m in ('parce', 'py4j'):",
			"    try:",
			"        __import__(m)",
			"        found.append(m)",
			"    except Exception:",
			"        pass",
			"if os.environ.get('LYPYTHON'):",
			"    sys.path.insert(0, os.environ['LYPYTHON'])",
			"try:",
			"    import lymodel.lybar.normalize",
			"    found.append('lymodel')",
			"except Exception:",
			"    pass",
			"print(' '.join(found))");

	private static final int PROBE_TIMEOUT_SECONDS = 20;

	/** The python to run, and the lymodel directory to hand it as LYPYTHON (null: the installed one, if any). */
	public record Choice(String python, Path lymodelDir) {
	}

	/** What a python imports - null modules: it could not be run at all. */
	record Probe(String python, Set<String> modules) {
	}

	private static final Map<String, Choice> remembered = new HashMap<>();

	private ParcePython() {
	}

	/**
	 * The python for reading LilyPond, with lymodel when {@code musicalTokens}.
	 *
	 * @throws EccoException naming every python tried and what it lacks, when none will do
	 */
	public static synchronized Choice resolve(boolean musicalTokens) {
		String configured = LilypondPreferences.getPythonPath();
		Path lymodelDir = lymodelDir(LilypondPreferences.getLymodelPath(), System.getenv("LYPYTHON"));
		String key = musicalTokens + "|" + configured + "|" + lymodelDir;
		Choice choice = remembered.get(key);
		if (choice == null) {
			choice = resolve(musicalTokens, candidates(configured), lymodelDir, ParcePython::probe);
			remembered.put(key, choice);
		}
		return choice;
	}

	/** Forgets what worked - a parse failed with it, so the next one looks again. */
	public static synchronized void forget() {
		remembered.clear();
	}

	static Choice resolve(boolean musicalTokens, List<String> candidates, Path lymodelDir,
						  BiFunction<String, Path, Probe> prober) {
		List<Probe> tried = new ArrayList<>();
		for (String python : candidates) {
			Probe probe = prober.apply(python, lymodelDir);
			tried.add(probe);
			if (probe.modules() != null && probe.modules().containsAll(needed(musicalTokens))) {
				return new Choice(python, lymodelDir);
			}
		}
		throw new EccoException(report(musicalTokens, tried, lymodelDir));
	}

	/** The python set in Preferences alone, if there is one - else PATH's, then the usual install locations. */
	static List<String> candidates(String configured) {
		if (configured != null && !configured.isBlank()) {
			return List.of(configured.trim());
		}
		Set<String> candidates = new LinkedHashSet<>(List.of("python", "python3"));
		candidates.addAll(WELL_KNOWN);
		return new ArrayList<>(candidates);
	}

	/** The plugin's python/ directory from Preferences, else from LYPYTHON - only one that holds lymodel. */
	static Path lymodelDir(String configured, String environment) {
		for (String dir : Arrays.asList(configured, environment)) {
			if (dir != null && !dir.isBlank() && Files.isDirectory(Path.of(dir.trim(), LYMODEL))) {
				return Path.of(dir.trim());
			}
		}
		return null;
	}

	private static Set<String> needed(boolean musicalTokens) {
		return musicalTokens ? Set.of(PARCE, PY4J, LYMODEL) : Set.of(PARCE, PY4J);
	}

	static String report(boolean musicalTokens, List<Probe> tried, Path lymodelDir) {
		StringJoiner out = new StringJoiner("\n");
		out.add(musicalTokens
				? "No python found that reads LilyPond with musical tokens (lilypond.musicalTokens=true in this"
				+ " repository's .ecco/.settings): it needs parce, py4j and lymodel. Tried:"
				: "No python found that reads LilyPond: it needs parce and py4j. Tried:");
		for (Probe probe : tried) {
			if (probe.modules() == null) {
				out.add("  " + probe.python() + ": not found");
			} else {
				List<String> missing = new ArrayList<>(needed(musicalTokens));
				missing.removeAll(probe.modules());
				missing.sort(null);
				out.add("  " + probe.python() + ": " + (missing.isEmpty() ? "ok" : "no " + String.join(", no ", missing)));
			}
		}
		out.add(lymodelDir == null
				? "lymodel: looked for installed only (no lymodel directory set in Preferences or LYPYTHON)."
				: "lymodel: looked for in " + lymodelDir + " and installed.");
		out.add("Install what is missing into one of them (python -m pip install parce py4j,"
				+ " python -m pip install <lilypond-idea-plugin>/python), or set the Python and the lymodel"
				+ " directory (the plugin's python/) under Preferences > LilyPond.");
		return out.toString();
	}

	static Probe probe(String python, Path lymodelDir) {
		ProcessBuilder builder = new ProcessBuilder(python, "-c", PROBE).redirectErrorStream(true);
		if (lymodelDir != null) {
			builder.environment().put("LYPYTHON", lymodelDir.toString());
		}
		Process process = null;
		try {
			process = builder.start();
			String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
			if (!process.waitFor(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS) || process.exitValue() != 0) {
				return new Probe(python, Set.of());
			}
			String[] lines = output.strip().split("\\R");
			String last = lines[lines.length - 1].strip();
			return new Probe(python, last.isEmpty() ? Set.of() : new HashSet<>(Arrays.asList(last.split(" "))));
		} catch (IOException e) {
			return new Probe(python, null);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return new Probe(python, null);
		} finally {
			if (process != null) process.destroy();
		}
	}
}
