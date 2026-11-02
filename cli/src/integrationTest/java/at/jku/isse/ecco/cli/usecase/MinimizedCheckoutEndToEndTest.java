package at.jku.isse.ecco.cli.usecase;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static at.jku.isse.ecco.cli.usecase.Steps.*;
import static at.jku.isse.ecco.test.Shop.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * UC-9 Minimize presence conditions through the command line, with the checkout that uses them
 * (UC-5 SA3): under an accepted constraint, {@code minimize} stores simpler conditions, and
 * {@code checkout --minimized} writes the same files as a checkout without them, for every
 * configuration the feature model allows - also after a commit changed what some were computed
 * from. Every command an {@code ecco} process of its own.
 */
public class MinimizedCheckoutEndToEndTest {

    private static final Map<String, byte[]> BASE_SEARCH_LOGGING = with(BASE_SEARCH,
            "src/core.txt", "start\nlog\nload\nsearch\nsave\nstop\n",
            "src/logging.txt", "level=info\n");

    /** The configurations REQUIRES export -> search allows. */
    private static final List<String> ALLOWED = List.of(
            "BASE", "BASE, search", "BASE, logging", "BASE, search, logging",
            "BASE, search, export", "BASE, search, export, logging");

    @Test
    @Timeout(600)
    public void aMinimizedCheckoutWritesWhatAFullOneDoes(@TempDir Path tmp) throws Exception {
        Path work = Files.createDirectories(tmp.resolve("shop"));
        ok(work, "init");
        commit(work, BASE, "BASE", "base");
        commit(work, BASE_SEARCH, "BASE, search", "search");
        for (int i = 1; i <= 4; i++)
            commit(work, BASE_SEARCH_EXPORT, "BASE, search, export", "export " + i);
        commit(work, BASE_LOGGING, "BASE, logging", "logging");
        assertEquals("Accepted REQUIRES export -> search.", ok(work, "accept-constraint", "REQUIRES", "export", "search").out().strip());

        // UC-9 SA1 (FR-N7b): the preview leaves the repository unchanged
        String preview = leavesRepository(work, work, "minimize-preview").out();
        assertFalse(preview.isBlank());

        // UC-9 B1-B5: the minimized conditions stored
        String stored = ok(work, "minimize").out().strip();
        assertTrue(stored.matches("Stored minimized conditions for [1-9]\\d* associations; use them with: checkout --minimized -c <configuration>"), stored);

        // UC-5 SA3 (FR-N9): with them, the same files as without, for every allowed configuration
        assertSameCheckouts(work);

        // FR-N8: a commit changes what some were computed from - they are no longer used, the
        // others still are, and the checkouts still agree
        commit(work, BASE_SEARCH_LOGGING, "BASE, search, logging", "search with logging");
        assertSameCheckouts(work);
        ok(work, "minimize");
        assertSameCheckouts(work);
    }

    private static void assertSameCheckouts(Path work) throws Exception {
        for (String configuration : ALLOWED) {
            clear(work);
            ok(work, "checkout", "-c", configuration);
            Map<String, byte[]> full = workingFiles(work);
            clear(work);
            ok(work, "checkout", "--minimized", "-c", configuration);
            assertSameFiles(full, workingFiles(work));
        }
    }

    private static Map<String, byte[]> with(Map<String, byte[]> variant, String... pathsAndContents) {
        Map<String, byte[]> files = new TreeMap<>(variant);
        for (int i = 0; i < pathsAndContents.length; i += 2)
            files.put(pathsAndContents[i], pathsAndContents[i + 1].getBytes(StandardCharsets.UTF_8));
        return files;
    }
}
