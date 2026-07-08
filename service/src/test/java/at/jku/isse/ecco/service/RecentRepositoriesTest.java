package at.jku.isse.ecco.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RecentRepositories} is backed by {@link Preferences}, the same OS-level store the real
 * GUI reads/writes - these tests save and restore the real value around each test so they don't
 * clobber an actual user's recent-repositories list.
 */
public class RecentRepositoriesTest {

	private static final String RECENT_REPOS_KEY = "recentRepositoryDirs";
	private String originalValue;

	@BeforeEach
	public void saveOriginalValueAndClear() {
		this.originalValue = prefs().get(RECENT_REPOS_KEY, null);
		prefs().remove(RECENT_REPOS_KEY);
	}

	@AfterEach
	public void restoreOriginalValue() throws BackingStoreException {
		if (this.originalValue == null) {
			prefs().remove(RECENT_REPOS_KEY);
		} else {
			prefs().put(RECENT_REPOS_KEY, this.originalValue);
		}
		prefs().flush();
	}

	@Test
	public void getRecentRepositories_dropsEntriesWhoseDirectoryNoLongerExists() throws IOException {
		Path existingDir = Files.createTempDirectory("recent-repo-exists");
		Path deletedDir = Files.createTempDirectory("recent-repo-deleted");
		Files.delete(deletedDir);

		prefs().put(RECENT_REPOS_KEY, deletedDir + "\n" + existingDir + "\n");

		List<Path> recent = RecentRepositories.getRecentRepositories();

		assertEquals(List.of(existingDir), recent);
	}

	@Test
	public void getRecentRepositories_prunesStaleEntriesFromPersistedList() throws IOException {
		Path existingDir = Files.createTempDirectory("recent-repo-exists");
		Path deletedDir = Files.createTempDirectory("recent-repo-deleted");
		Files.delete(deletedDir);

		prefs().put(RECENT_REPOS_KEY, deletedDir + "\n" + existingDir + "\n");

		RecentRepositories.getRecentRepositories();

		assertTrue(prefs().get(RECENT_REPOS_KEY, "").contains(existingDir.toString()));
		assertTrue(!prefs().get(RECENT_REPOS_KEY, "").contains(deletedDir.toString()));
	}

	@Test
	public void addRecentRepository_addsMostRecentFirst() throws IOException {
		Path a = Files.createTempDirectory("recent-repo-a");
		Path b = Files.createTempDirectory("recent-repo-b");

		RecentRepositories.addRecentRepository(a);
		RecentRepositories.addRecentRepository(b);

		assertEquals(List.of(b, a), RecentRepositories.getRecentRepositories());
	}

	private static Preferences prefs() {
		return Preferences.userNodeForPackage(RecentRepositories.class);
	}
}
