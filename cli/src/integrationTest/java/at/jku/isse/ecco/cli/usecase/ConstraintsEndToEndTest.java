package at.jku.isse.ecco.cli.usecase;

import at.jku.isse.ecco.cli.usecase.EccoProcess.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static at.jku.isse.ecco.cli.usecase.Steps.*;
import static at.jku.isse.ecco.test.Shop.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * UC-8 Review mined constraints through the command line, and what an accepted constraint does
 * afterwards: a checkout and a commit that violate it are warned about (FR-K5f, IF-C9a), and the
 * decisions travel with a fork and a pull (FR-N4b). Every command an {@code ecco} process of its
 * own. The steps in the comments are those of UC-8's flows.
 */
public class ConstraintsEndToEndTest {

    private static final String REQUIRES = "REQUIRES export -> search";

    @Test
    @Timeout(600)
    public void aProductLineEngineerDecidesConstraintsThatTravelWithTheRepository(@TempDir Path tmp) throws Exception {
        Path work = Files.createDirectories(tmp.resolve("shop"));
        ok(work, "init");
        commit(work, BASE, "BASE", "base");
        commit(work, BASE_SEARCH, "BASE, search", "search");
        // export always with search, four times: enough witnesses (FR-N1b) for the rule to be trusted
        for (int i = 1; i <= 4; i++)
            commit(work, BASE_SEARCH_EXPORT, "BASE, search, export", "export " + i);

        // UC-8 SA4, B2-B3: the pending suggestions, mined from the configurations
        String suggested = ok(work, "suggest-constraints").out();
        assertTrue(suggested.contains("REQUIRES  export -> search"), suggested);
        assertTrue(suggested.contains("MANDATORY BASE"), suggested);

        // UC-8 SA4, B4-B5 and SA2: one accepted, trusted at once; one rejected
        assertEquals("Accepted " + REQUIRES + ".", ok(work, "accept-constraint", "REQUIRES", "export", "search").out().strip());
        assertEquals("Rejected MANDATORY BASE; it is not suggested again.", ok(work, "reject-constraint", "MANDATORY", "BASE").out().strip());
        // a feature the repository does not have, the wrong number of features: refused, nothing stored
        refused(work, "No feature named \"nosuch\".", "accept-constraint", "REQUIRES", "export", "nosuch");
        refused(work, "REQUIRES takes two features.", "accept-constraint", "REQUIRES", "export");

        // decided, they are no longer suggested; --all lists them, the accepted one with its trust
        String pending = ok(work, "suggest-constraints").out();
        assertFalse(pending.contains("export -> search") || pending.contains("MANDATORY BASE"), pending);
        assertTrue(pending.contains("(1 accepted and 1 rejected"), pending);
        String all = ok(work, "suggest-constraints", "--all").out();
        assertTrue(all.contains("Accepted:\n  " + REQUIRES + "   [trusted]"), all);
        assertTrue(all.contains("Rejected:\n  MANDATORY BASE"), all);

        // FR-K5f: a checkout of export without search is warned about
        String warnings = checkout(work, "BASE, export", null);
        assertTrue(warnings.contains("1 CONSTRAINT"), warnings);
        assertTrue(Files.readString(work.resolve(".warnings")).contains("CONSTRAINT: "), Files.readString(work.resolve(".warnings")));

        // FR-N4b: a fork takes the decisions over
        Path fork = Files.createDirectories(tmp.resolve("fork"));
        ok(fork, "fork", work.toString());
        assertEquals(all, ok(fork, "suggest-constraints", "--all").out());

        // IF-C9a, FR-K8: a commit of export without search is committed, and the violation printed
        writeVariant(work, BASE_EXPORT);
        Result violating = ok(work, "commit", "-c", "BASE, export", "-m", "export alone");
        assertTrue(violating.out().contains("\nCONSTRAINT: "), violating.toString());
        // the commit is an exception to the rule: accepted still, but no longer trusted
        String exception = ok(work, "suggest-constraints", "--all").out();
        assertTrue(exception.contains(REQUIRES + "   [not yet trusted"), exception);

        // FR-N4b: a pull brings the exception, and the fork no longer trusts the rule either
        ok(fork, "pull");
        assertEquals(exception, ok(fork, "suggest-constraints", "--all").out());

        // UC-8 SA4, SA3: --undo moves the accepted constraint back to pending
        assertEquals(REQUIRES + " is pending again.", ok(work, "accept-constraint", "REQUIRES", "export", "search", "--undo").out().strip());
        String undone = ok(work, "suggest-constraints", "--all").out();
        assertFalse(undone.contains("Accepted:"), undone);
        assertTrue(undone.contains("Rejected:\n  MANDATORY BASE"), undone);
    }
}
