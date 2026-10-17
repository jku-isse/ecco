package at.jku.isse.ecco.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Persists a most-recently-used list of repository directories the user has opened or
 * initialized, so the GUI can offer them as quick picks instead of requiring the user to navigate
 * to the directory again every time. Backed by {@link Preferences}, like {@link AdapterPreferences}.
 */
public final class RecentRepositories {

	private static final String RECENT_REPOS_KEY = "recentRepositoryDirs";
	private static final String SEPARATOR = "\n";
	private static final int MAX_ENTRIES = 10;

	private RecentRepositories() {
	}

	/**
	 * @return the recent repository directories that exist now. One missing - an unmounted drive, an
	 * iCloud folder not downloaded yet - is kept on the list and shown again once it is there: it was
	 * taken off for good; {@link #removeRecentRepository} takes it off.
	 */
	public static List<Path> getRecentRepositories() {
		List<Path> paths = new ArrayList<>();
		for (Path path : stored())
			if (Files.exists(path))
				paths.add(path);
		return paths;
	}

	/** Every directory on the list, also those missing now. */
	private static List<Path> stored() {
		List<Path> paths = new ArrayList<>();
		for (String s : prefs().get(RECENT_REPOS_KEY, "").split(SEPARATOR))
			if (!s.isEmpty())
				paths.add(Paths.get(s));
		return paths;
	}

	public static void addRecentRepository(Path repositoryDir) {
		LinkedHashSet<Path> paths = new LinkedHashSet<>();
		paths.add(repositoryDir);
		paths.addAll(stored());

		List<Path> trimmed = new ArrayList<>(paths);
		if (trimmed.size() > MAX_ENTRIES) {
			trimmed = trimmed.subList(0, MAX_ENTRIES);
		}

		store(trimmed);
	}

	/** Takes [repositoryDir] off the list - the repository stays as it is. */
	public static void removeRecentRepository(Path repositoryDir) {
		List<Path> paths = new ArrayList<>(stored());
		paths.remove(repositoryDir);
		store(paths);
	}

	private static void store(List<Path> paths) {
		StringBuilder sb = new StringBuilder();
		for (Path p : paths) {
			sb.append(p).append(SEPARATOR);
		}
		prefs().put(RECENT_REPOS_KEY, sb.toString());
	}

	private static Preferences prefs() {
		return Preferences.userNodeForPackage(RecentRepositories.class);
	}
}
