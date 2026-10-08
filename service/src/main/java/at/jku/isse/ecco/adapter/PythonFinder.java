package at.jku.isse.ecco.adapter;

import at.jku.isse.ecco.EccoException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

/**
 * The python an adapter runs its scripts with (the LilyPond adapter's parce, the Python adapter's
 * libcst). Plain {@code python} from the PATH used to be the only one tried, so a GUI started from
 * the Finder (whose PATH has no MacPorts or Homebrew), or a {@code python} other than the one the
 * modules were installed into, failed. Now: the python set in Preferences if there is one, else the
 * first of {@code python}, {@code python3} and the usual install locations that imports every
 * module the adapter needs.
 *
 * <p>Only what worked is remembered: whatever is installed or set after a failure is found the next
 * time, without restarting ECCO - and an adapter whose script then fails calls {@link #forget()}.
 */
public final class PythonFinder {

	private static final List<String> WELL_KNOWN = List.of(
			"/opt/local/bin/python3", "/opt/local/bin/python",
			"/opt/homebrew/bin/python3",
			"/usr/local/bin/python3",
			"/Library/Frameworks/Python.framework/Versions/Current/bin/python3",
			"/usr/bin/python3");

	/** Prints which of the modules named in its arguments import, after putting ECCO_PYTHON_PATH on sys.path. */
	private static final String PROBE = String.join("\n",
			"import importlib, os, sys",
			"if os.environ.get('ECCO_PYTHON_PATH'):",
			"    sys.path.insert(0, os.environ['ECCO_PYTHON_PATH'])",
			"found = []",
			"for m in sys.argv[1:]:",
			"    try:",
			"        importlib.import_module(m)",
			"        found.append(m)",
			"    except Exception:",
			"        pass",
			"print(' '.join(found))");

	private static final int PROBE_TIMEOUT_SECONDS = 20;

	/**
	 * What one python imports, of the modules asked for - null modules: it could not be run at all.
	 */
	public record Probe(String python, Set<String> modules) {
	}

	/** Asks one python which of the modules it imports, with the extra path (or null) on its sys.path. */
	@FunctionalInterface
	public interface Prober {
		Probe probe(String python, List<String> modules, Path extraPath);
	}

	private static final Map<String, String> remembered = new HashMap<>();

	private PythonFinder() {
	}

	/**
	 * The python that imports every one of {@code modules}.
	 *
	 * @param configured the python set in Preferences - the only one tried when not blank
	 * @param modules    what it must import, e.g. {@code libcst} or {@code lymodel.lybar.normalize}
	 * @param extraPath  a directory put on its sys.path first (to be put on the script's too), or null
	 * @param purpose    what the python is for, as in "No python found that {purpose}"
	 * @param advice     how to fix it, appended to the report of what each python lacks
	 * @throws EccoException naming every python tried and what it lacks, when none will do
	 */
	public static synchronized String find(String configured, List<String> modules, Path extraPath,
										   String purpose, String advice) {
		String key = configured + "|" + modules + "|" + extraPath;
		String python = remembered.get(key);
		if (python == null) {
			python = find(candidates(configured), modules, extraPath, purpose, advice, PythonFinder::probe);
			remembered.put(key, python);
		}
		return python;
	}

	/** Forgets what worked - a script failed with it, so the next run looks again. */
	public static synchronized void forget() {
		remembered.clear();
	}

	static String find(List<String> candidates, List<String> modules, Path extraPath, String purpose, String advice,
					   Prober prober) {
		List<Probe> tried = new ArrayList<>();
		for (String python : candidates) {
			Probe probe = prober.probe(python, modules, extraPath);
			tried.add(probe);
			if (probe.modules() != null && probe.modules().containsAll(modules)) {
				return python;
			}
		}
		throw new EccoException(report(tried, modules, purpose, advice));
	}

	/** The python set in Preferences alone, if there is one - else PATH's, then the usual install locations. */
	public static List<String> candidates(String configured) {
		if (configured != null && !configured.isBlank()) {
			return List.of(configured.trim());
		}
		Set<String> candidates = new LinkedHashSet<>(List.of("python", "python3"));
		candidates.addAll(WELL_KNOWN);
		return new ArrayList<>(candidates);
	}

	static String report(List<Probe> tried, List<String> modules, String purpose, String advice) {
		StringJoiner out = new StringJoiner("\n");
		out.add("No python found that " + purpose + ": it needs " + names(modules) + ". Tried:");
		for (Probe probe : tried) {
			if (probe.modules() == null) {
				out.add("  " + probe.python() + ": not found");
			} else {
				List<String> missing = new ArrayList<>(modules);
				missing.removeAll(probe.modules());
				out.add("  " + probe.python() + ": " + (missing.isEmpty() ? "ok" : "no " + names(missing).replace(", ", ", no ")));
			}
		}
		out.add(advice);
		return out.toString();
	}

	/** Top-level module names: lymodel, not lymodel.lybar.normalize. */
	private static String names(List<String> modules) {
		return String.join(", ", modules.stream().map(m -> m.split("\\.")[0]).toList());
	}

	static Probe probe(String python, List<String> modules, Path extraPath) {
		List<String> command = new ArrayList<>(List.of(python, "-c", PROBE));
		command.addAll(modules);
		ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
		if (extraPath != null) {
			builder.environment().put("ECCO_PYTHON_PATH", extraPath.toString());
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
