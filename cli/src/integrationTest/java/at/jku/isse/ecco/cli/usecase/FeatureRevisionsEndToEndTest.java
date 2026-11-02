package at.jku.isse.ecco.cli.usecase;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static at.jku.isse.ecco.cli.usecase.Steps.*;
import static at.jku.isse.ecco.test.Shop.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * UC-3 Commit variant and UC-5 Check out variant with feature revisions, through the command line:
 * a feature committed in three revisions, by name ({@code search'}) and by id ({@code [<id>]'}),
 * and each revision checked out again - by name for the latest, by its id's prefix as the CLI
 * prints it ({@code search.d678240}), by the feature's id. Every command an {@code ecco} process of
 * its own. The configurations a commit or checkout cannot take are refused, the repository
 * unchanged.
 * <p>
 * The oracle is the files: each revision checks out as it was committed, byte for byte.
 */
public class FeatureRevisionsEndToEndTest {

    private static final Map<String, byte[]> SEARCH_2 = with(BASE_SEARCH, "src/search.txt", "query\nrank\nfilter\n");
    private static final Map<String, byte[]> SEARCH_3 = with(BASE_SEARCH, "src/search.txt", "query\nrank\nfilter\nsort\n");

    @Test
    @Timeout(600)
    public void aFeatureIsCommittedAndCheckedOutInRevisions(@TempDir Path tmp) throws Exception {
        Path work = Files.createDirectories(tmp.resolve("shop"));
        ok(work, "init");
        commit(work, BASE, "BASE", "base");

        // UC-3 B3 (FR-F1a): A - a new feature; A' - a new revision of it
        String first = revision(commit(work, BASE_SEARCH, "BASE, search", "search"), "search");
        String second = revision(commit(work, SEARCH_2, "BASE, search'", "search filters"), "search");
        assertNotEquals(first, second);

        // the feature's id, as `features` shows it, in the form a configuration takes
        String listed = ok(work, "features", "search").out();
        Matcher id = Pattern.compile("^search \\[([^\\]]+)]$", Pattern.MULTILINE).matcher(listed);
        assertTrue(id.find(), listed);
        String feature = "[" + id.group(1) + "]";
        assertTrue(listed.contains(first) && listed.contains(second), listed);

        // [<id>]' - a new revision of the feature with that id
        String third = revision(commit(work, SEARCH_3, "BASE, " + feature + "'", "search sorts"), "search");
        assertNotEquals(second, third);

        // UC-5 B2 (FR-F1a, FR-F2, FR-F7a): each revision checks out as it was committed - the latest
        // by name or by id, the others by the prefix of their id, by name or by id
        assertEquals("", checkout(work, "BASE, search", SEARCH_3));
        assertEquals("", checkout(work, "BASE, " + feature, SEARCH_3));
        assertEquals("", checkout(work, "BASE, " + first, BASE_SEARCH));
        assertEquals("", checkout(work, "BASE, " + second, SEARCH_2));
        assertEquals("", checkout(work, "BASE, " + feature + first.substring(first.indexOf('.')), BASE_SEARCH));
        // and left out
        assertEquals("", checkout(work, "BASE", BASE));

        // UC-5 SA1: what a checkout cannot take is refused, nothing written
        clear(work);
        // FR-F2b, FR-K13: "search.2" names no revision - a new one, which a checkout does not have
        refused(work, "", "checkout", "-c", "BASE, search.2");
        // FR-F5a: two revisions of one feature
        refused(work, "selected twice", "checkout", "-c", "BASE, " + first + ", " + second);
        // FR-F8: there is no "without" - "-search" names a feature "-search", which does not exist
        refused(work, "to not select search, leave it out", "checkout", "-c", "BASE, -search");
        // FR-F7b: an id no feature has
        refused(work, "", "checkout", "-c", "BASE, [no-such-id]");
        assertSameFiles(Map.of(), workingFiles(work));

        // UC-3 SA1: and a commit
        writeVariant(work, SEARCH_3);
        refused(work, "selected twice", "commit", "-c", "BASE, " + first + ", " + second, "-m", "twice");
        refused(work, "", "commit", "-c", "BASE, [no-such-id]", "-m", "no such feature");

        // the revisions are still those three, each checking out as before
        String after = ok(work, "features", "search").out();
        assertEquals(4, after.lines().count(), after);
        assertEquals("", checkout(work, "BASE, " + first, BASE_SEARCH));
    }

    private static Map<String, byte[]> with(Map<String, byte[]> variant, String path, String content) {
        Map<String, byte[]> files = new TreeMap<>(variant);
        files.put(path, content.getBytes(StandardCharsets.UTF_8));
        return files;
    }
}
