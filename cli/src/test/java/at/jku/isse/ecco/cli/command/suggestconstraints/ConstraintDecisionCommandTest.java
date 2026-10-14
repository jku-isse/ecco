package at.jku.isse.ecco.cli.command.suggestconstraints;

import at.jku.isse.ecco.cli.MainTestAccess;
import at.jku.isse.ecco.service.EccoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * accept-constraint and reject-constraint decide a suggestion as the GUI does, and
 * suggest-constraints then lists only the undecided ones (all of them with --all). Run through the
 * command line itself, against a repository where Core is in every commit and A and B are never
 * together: MANDATORY Core and EXCLUDES A / B are suggested.
 */
public class ConstraintDecisionCommandTest {

    private static Path repository(Path tmp) throws IOException {
        Path work = Files.createDirectories(tmp.resolve("work"));
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(work.resolve(".ecco"));
            service.init();
            for (int i = 0; i < 8; i++) {
                Path variant = Files.createDirectories(tmp.resolve("v" + i));
                String feature = i % 2 == 0 ? "A" : "B";
                Files.writeString(variant.resolve("core.txt"), "core\n");
                Files.writeString(variant.resolve(feature + ".txt"), feature + " " + i + "\n");
                service.setBaseDir(variant);
                service.commit("v" + i, "Core, " + feature);
            }
        }
        return work;
    }

    /** Runs the command line in {@code dir}; returns its exit code and what it printed. */
    private record Run(int exit, String out, String err) {
    }

    private static Run ecco(Path dir, String... args) {
        PrintStream out = System.out, err = System.err;
        ByteArrayOutputStream capturedOut = new ByteArrayOutputStream(), capturedErr = new ByteArrayOutputStream();
        System.setOut(new PrintStream(capturedOut, true));
        System.setErr(new PrintStream(capturedErr, true));
        try {
            int exit = MainTestAccess.run(args, dir);
            return new Run(exit, capturedOut.toString(), capturedErr.toString());
        } finally {
            System.setOut(out);
            System.setErr(err);
        }
    }

    @Test
    @Timeout(120)
    public void acceptedAndRejectedSuggestionsAreNotListedAgain(@TempDir Path tmp) throws IOException {
        Path work = repository(tmp);

        Run before = ecco(work, "suggest-constraints");
        assertEquals(0, before.exit(), before.err());
        assertTrue(before.out().contains("MANDATORY Core"), before.out());
        assertTrue(before.out().contains("EXCLUDES  A / B"), before.out());
        assertFalse(before.out().contains("accepted and"), "nothing decided yet: " + before.out());

        Run accepted = ecco(work, "accept-constraint", "mandatory", "Core");
        assertEquals(0, accepted.exit(), accepted.err());
        assertEquals("Accepted MANDATORY Core.", accepted.out().strip(), "8 commits without an exception: trusted");

        // either order names the same pair
        Run rejected = ecco(work, "reject-constraint", "EXCLUDES", "B", "A");
        assertEquals(0, rejected.exit(), rejected.err());
        assertEquals("Rejected EXCLUDES A / B; it is not suggested again.", rejected.out().strip());

        Run after = ecco(work, "suggest-constraints");
        assertFalse(after.out().contains("MANDATORY Core"), after.out());
        assertFalse(after.out().contains("EXCLUDES  A / B"), after.out());
        assertTrue(after.out().contains("(1 accepted and 1 rejected not listed; --all lists them)"), after.out());

        Run all = ecco(work, "suggest-constraints", "--all");
        assertTrue(all.out().contains("Accepted:\n  MANDATORY Core   [trusted]\n"), all.out());
        assertTrue(all.out().contains("Rejected:\n  EXCLUDES A / B\n"), all.out());
        assertTrue(all.out().contains("(1 accepted and 1 rejected listed below)"), all.out());
    }

    /** Rejecting an accepted suggestion withdraws the acceptance; --undo makes either pending again. */
    @Test
    @Timeout(120)
    public void decisionsReplaceEachOtherAndUndoMakesThemPending(@TempDir Path tmp) throws IOException {
        Path work = repository(tmp);

        ecco(work, "accept-constraint", "MANDATORY", "Core");
        ecco(work, "reject-constraint", "MANDATORY", "Core");
        Run all = ecco(work, "suggest-constraints", "--all");
        assertTrue(all.out().contains("(0 accepted and 1 rejected listed below)"), all.out());
        assertFalse(all.out().contains("Accepted:"), all.out());

        Run notAccepted = ecco(work, "accept-constraint", "MANDATORY", "Core", "--undo");
        assertEquals(0, notAccepted.exit(), notAccepted.err());
        assertEquals("MANDATORY Core was not accepted.", notAccepted.out().strip());

        Run undone = ecco(work, "reject-constraint", "MANDATORY", "Core", "--undo");
        assertEquals("MANDATORY Core is pending again.", undone.out().strip());
        Run pending = ecco(work, "suggest-constraints");
        assertTrue(pending.out().contains("MANDATORY Core"), pending.out());
        assertFalse(pending.out().contains("rejected"), pending.out());

        ecco(work, "accept-constraint", "MANDATORY", "Core");
        Run unaccepted = ecco(work, "accept-constraint", "MANDATORY", "Core", "--undo");
        assertEquals("MANDATORY Core is pending again.", unaccepted.out().strip());
        assertTrue(ecco(work, "suggest-constraints").out().contains("MANDATORY Core"));
    }

    /** A constraint on features that do not exist, of the wrong arity or of no kind is refused, and nothing is stored. */
    @Test
    @Timeout(120)
    public void malformedDecisionsAreRefused(@TempDir Path tmp) throws IOException {
        Path work = repository(tmp);

        Run typo = ecco(work, "accept-constraint", "REQUIRES", "A", "Cor");
        assertEquals(1, typo.exit());
        assertTrue(typo.err().contains("No feature named \"Cor\"."), typo.err());

        Run two = ecco(work, "accept-constraint", "MANDATORY", "Core", "A");
        assertEquals(1, two.exit());
        assertTrue(two.err().contains("MANDATORY takes one feature, not two."), two.err());

        Run one = ecco(work, "reject-constraint", "REQUIRES", "A");
        assertEquals(1, one.exit());
        assertTrue(one.err().contains("REQUIRES takes two features."), one.err());

        Run kind = ecco(work, "reject-constraint", "IMPLIES", "A", "B");
        assertEquals(1, kind.exit());
        assertTrue(kind.err().contains("No constraint kind \"IMPLIES\""), kind.err());

        Run all = ecco(work, "suggest-constraints", "--all");
        assertFalse(all.out().contains("accepted and"), "nothing was stored: " + all.out());
    }
}
