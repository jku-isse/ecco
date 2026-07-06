package at.jku.isse.ecco.service;

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

	public static List<Path> getRecentRepositories() {
		String stored = prefs().get(RECENT_REPOS_KEY, "");
		if (stored.isEmpty()) {
			return new ArrayList<>();
		}

		List<Path> paths = new ArrayList<>();
		for (String s : stored.split(SEPARATOR)) {
			if (!s.isEmpty()) {
				paths.add(Paths.get(s));
			}
		}
		return paths;
	}

	public static void addRecentRepository(Path repositoryDir) {
		LinkedHashSet<Path> paths = new LinkedHashSet<>();
		paths.add(repositoryDir);
		paths.addAll(getRecentRepositories());

		List<Path> trimmed = new ArrayList<>(paths);
		if (trimmed.size() > MAX_ENTRIES) {
			trimmed = trimmed.subList(0, MAX_ENTRIES);
		}

		StringBuilder sb = new StringBuilder();
		for (Path p : trimmed) {
			sb.append(p).append(SEPARATOR);
		}
		prefs().put(RECENT_REPOS_KEY, sb.toString());
	}

	private static Preferences prefs() {
		return Preferences.userNodeForPackage(RecentRepositories.class);
	}
}
