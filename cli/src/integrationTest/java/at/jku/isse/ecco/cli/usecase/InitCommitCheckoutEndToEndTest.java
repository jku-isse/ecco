package at.jku.isse.ecco.cli.usecase;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static at.jku.isse.ecco.test.Shop.*;
import static at.jku.isse.ecco.cli.usecase.Steps.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * UC-1 Initialize repository, UC-3 Commit variant and UC-5 Check out variant (doc/requirements.md),
 * through the command line as a variant developer uses it: every command is an {@code ecco} process
 * of its own in the working directory, so every step opens the repository from disk (UC-2). The
 * steps in the comments are those of the use cases' flows.
 * <p>
 * The oracle is the files: a committed variant checks out byte for byte (NFR-R3), and a feature
 * combination never committed as such checks out as the merge of the variants. A refused command
 * leaves the repository as it was, byte for byte.
 */
public class InitCommitCheckoutEndToEndTest {

    @Test
    @Timeout(600)
    public void aVariantDeveloperInitializesCommitsAndChecksOut(@TempDir Path tmp) throws Exception {
        Path work = Files.createDirectories(tmp.resolve("shop"));

        // UC-1 B1-B5: an empty repository in <directory>/.ecco
        ok(work, "init");
        assertTrue(Files.isDirectory(work.resolve(".ecco")));
        assertEquals("", ok(work, "features").out().strip(), "a new repository has no features");

        // UC-1 SA1: a second init is refused and leaves the repository unchanged
        refused(work, "Repository already exists at this location.", "init");

        // UC-3 B1-B12, three times: the files of the working directory as a variant
        commit(work, BASE, "BASE", "first variant");
        commit(work, BASE_SEARCH, "BASE, search", "search");
        commit(work, BASE_EXPORT, "BASE, export", "export");

        String features = ok(work, "features").out();
        for (String feature : new String[]{"BASE", "search", "export"})
            assertTrue(features.contains(feature), features);

        // UC-3 SA1: a configuration that is not well-formed or names no feature is refused, and
        // the repository is unchanged
        writeVariant(work, BASE);
        refused(work, "", "commit", "-c", "BASE, bad name", "-m", "invalid");
        refused(work, "A commit needs at least one feature", "commit", "-c", "", "-m", "empty");

        // UC-5 B1-B12: every committed variant checks out as it was committed (NFR-R3)...
        assertEquals("", checkout(work, "BASE", BASE));
        assertEquals("", checkout(work, "BASE, search", BASE_SEARCH));
        assertEquals("", checkout(work, "BASE, export", BASE_EXPORT));
        // ...and a combination never committed as such checks out composed from them, with the
        // interaction of the two features reported as possibly missing - it was never committed
        assertEquals("1 MISSING", checkout(work, "BASE, search, export", BASE_SEARCH_EXPORT));
        assertTrue(Files.readString(work.resolve(".warnings")).contains("MISSING: export + search"));

        // UC-5 SA2: a working directory that is not empty is refused and left as it was
        Map<String, byte[]> before = workingFiles(work);
        refused(work, "", "checkout", "-c", "BASE");
        assertSameFiles(before, workingFiles(work));

        // UC-5 SA1: a configuration error is refused, nothing is written
        clear(work);
        refused(work, "", "checkout", "-c", "BASE, bad name");
        assertSameFiles(Map.of(), workingFiles(work));
    }
}
