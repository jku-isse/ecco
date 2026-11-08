package at.jku.isse.ecco.service;

import at.jku.isse.ecco.adapter.ArtifactPlugin;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

/**
 * Persists which artifact adapters (by {@link ArtifactPlugin#getPluginId()}) the user has
 * deactivated, so {@link EccoService} can skip binding them. This is a small, per-user runtime
 * setting (backed by {@link Preferences}, e.g. the platform registry/plist), distinct from the
 * bundled {@code ecco.properties} classpath resource, which is packaged deployment config rather
 * than something a user toggles at runtime.
 *
 * <p>Also the python the Python adapter runs libcst with - blank by default, in which case it is
 * looked for (see {@link at.jku.isse.ecco.adapter.PythonFinder}).
 */
public final class AdapterPreferences {

	private static final String DISABLED_ADAPTERS_KEY = "disabledAdapterPluginIds";
	private static final String PYTHON_ADAPTER_PYTHON_KEY = "pythonAdapterPythonPath";
	private static final String SEPARATOR = ",";
	private static final String UNSET_MARKER = "\u0000unset";

	/**
	 * Adapters that are on the classpath (so they show up as options) but are off until the user
	 * opts in via the Preferences dialog: the line-based Java and challenge adapters cannot write
	 * files, and these four claim *.java or *.go, mostly for experiments. C and C++ are on (the
	 * README maps *.c, *.h, *.cpp and *.hpp to them); that only changes new repositories, since
	 * .adapters fixes an existing repository's routing.
	 */
	private static final Set<String> DEFAULT_DISABLED_PLUGIN_IDS = Set.of(
			"at.jku.isse.ecco.adapter.challenge.JavaPlugin",
			"at.jku.isse.ecco.adapter.java.JavaPlugin",
			"at.jku.isse.ecco.adapter.runtime.RuntimePlugin",
			"at.jku.isse.ecco.adapter.golang.GoPlugin"
	);

	private AdapterPreferences() {
	}

	public static Set<String> getDisabledPluginIds() {
		String stored = prefs().get(DISABLED_ADAPTERS_KEY, UNSET_MARKER);
		if (stored.equals(UNSET_MARKER)) {
			return new HashSet<>(DEFAULT_DISABLED_PLUGIN_IDS);
		}
		if (stored.isEmpty()) {
			return new HashSet<>();
		}
		return Arrays.stream(stored.split(SEPARATOR)).collect(Collectors.toCollection(HashSet::new));
	}

	public static void setDisabledPluginIds(Set<String> disabledPluginIds) {
		prefs().put(DISABLED_ADAPTERS_KEY, String.join(SEPARATOR, disabledPluginIds));
	}

	public static boolean isEnabled(ArtifactPlugin plugin) {
		return !getDisabledPluginIds().contains(plugin.getPluginId());
	}

	/** Blank by default, deliberately - the Python adapter then tries python, python3 and the usual install locations. */
	public static String getPythonAdapterPython() {
		return prefs().get(PYTHON_ADAPTER_PYTHON_KEY, "");
	}

	public static void setPythonAdapterPython(String pythonPath) {
		prefs().put(PYTHON_ADAPTER_PYTHON_KEY, pythonPath == null ? "" : pythonPath.trim());
	}

	private static Preferences prefs() {
		return Preferences.userNodeForPackage(AdapterPreferences.class);
	}
}
