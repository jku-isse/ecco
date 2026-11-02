package at.jku.isse.ecco.cli.usecase;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

import static at.jku.isse.ecco.cli.usecase.Steps.*;
import static at.jku.isse.ecco.test.Shop.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * UC-6 Resolve ambiguous order through the command line (its flow SA3): two features each add a
 * line at the same place, so a checkout of both cannot know their order and warns ORDER; the
 * variant developer puts the lines in order in the checked-out file and runs {@code order}, and
 * later checkouts know it. Every command an {@code ecco} process of its own. The steps in the
 * comments are those of UC-6's flows.
 */
public class ResolveOrderEndToEndTest {

    private static final Map<String, byte[]> BASE_AUDIT = with(BASE, "src/core.txt", "start\nload\naudit\nsave\nstop\n");
    private static final String AUDIT_FIRST = "start\nload\naudit\nsearch\nsave\nstop\n";
    private static final String SEARCH_FIRST = "start\nload\nsearch\naudit\nsave\nstop\n";

    @Test
    @Timeout(600)
    public void aVariantDeveloperRecordsTheOrderACheckoutCouldNotKnow(@TempDir Path tmp) throws Exception {
        Path work = Files.createDirectories(tmp.resolve("shop"));
        ok(work, "init");
        commit(work, BASE, "BASE", "base");
        commit(work, BASE_SEARCH, "BASE, search", "search");
        commit(work, BASE_AUDIT, "BASE, audit", "audit");

        // UC-5 B8, B12: search and audit were never committed together - which comes first?
        String warnings = checkout(work, "BASE, search, audit", null);
        assertTrue(warnings.contains("1 ORDER"), warnings);
        String listed = Files.readString(work.resolve(".warnings"));
        assertTrue(listed.contains("ORDER: ") && listed.contains("core.txt"), listed);
        Path core = work.resolve("src/core.txt");
        assertTrue(Files.readString(core).equals(AUDIT_FIRST) || Files.readString(core).equals(SEARCH_FIRST), Files.readString(core));

        // UC-6 SA3 step 5: a file holding content the checkout does not is refused, naming it
        Files.writeString(core, "start\nload\naudit\nsearch\nbogus\nsave\nstop\n");
        refused(work, "bogus", "order", "src/core.txt");

        // UC-6 SA3, B6-B7: the lines put in order, recorded - the file named from where the command runs
        Files.writeString(core, AUDIT_FIRST);
        String recorded = ok(work.resolve("src"), "order", "core.txt").out().strip();
        assertTrue(recorded.startsWith("Recorded the order of core.txt ("), recorded);
        assertEquals("The order of core.txt was already recorded.", ok(work.resolve("src"), "order", "core.txt").out().strip());

        // UC-6 postcondition: later checkouts of these lines know the order - and nothing else changed:
        // still MISSING for search with audit, which a commit of the configuration would have removed
        assertEquals("1 MISSING", checkout(work, "BASE, search, audit", with(BASE_SEARCH, "src/core.txt", AUDIT_FIRST)));
        assertEquals("", checkout(work, "BASE, search", BASE_SEARCH));
        assertEquals("", checkout(work, "BASE, audit", BASE_AUDIT));
        assertEquals("", checkout(work, "BASE", BASE));

        // UC-6 SA4: an order that contradicts the recorded one is refused, the repository unchanged
        checkout(work, "BASE, search, audit", null);
        Files.writeString(core, SEARCH_FIRST);
        refused(work, "", "order", "src/core.txt");
        assertEquals("1 MISSING", checkout(work, "BASE, search, audit", with(BASE_SEARCH, "src/core.txt", AUDIT_FIRST)));
    }

    private static Map<String, byte[]> with(Map<String, byte[]> variant, String path, String content) {
        Map<String, byte[]> files = new TreeMap<>(variant);
        files.put(path, content.getBytes(StandardCharsets.UTF_8));
        return files;
    }
}
