package at.jku.isse.ecco.cli.usecase;

import at.jku.isse.ecco.cli.usecase.EccoProcess.Result;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Map;

import static at.jku.isse.ecco.cli.usecase.Steps.*;
import static at.jku.isse.ecco.test.Shop.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The flows of UC-3 and UC-12 where something goes wrong, through the command line: a commit that
 * cannot read a file, a pull and a push while the remote's server is down, a pull between
 * repositories that read files differently. Each is refused with its reason, and leaves every
 * repository as it was, byte for byte (NFR-R1, FR-D7) - and the next attempt, once the cause is
 * gone, works as if nothing had happened. Every command an {@code ecco} process of its own.
 */
public class FailureFlowsEndToEndTest {

    /** UC-3 BA1: a file that cannot be read, a file mapped to an adapter that is not there. */
    @Test
    @Timeout(600)
    public void aCommitThatCannotReadAFileLeavesTheRepositoryAsItWas(@TempDir Path tmp) throws Exception {
        Path work = Files.createDirectories(tmp.resolve("shop"));
        ok(work, "init");
        commit(work, BASE, "BASE", "base");

        // FR-M8a: unreadable - the commit fails instead of committing the file empty
        writeVariant(work, BASE_SEARCH);
        Path search = work.resolve("src/search.txt");
        Files.setPosixFilePermissions(search, PosixFilePermissions.fromString("---------"));
        try {
            Assumptions.assumeFalse(Files.isReadable(search), "file permissions not enforced (root?)");
            refused(work, "search.txt", "commit", "-c", "BASE, search", "-m", "unreadable");
        } finally {
            Files.setPosixFilePermissions(search, PosixFilePermissions.fromString("rw-r--r--"));
        }

        // FR-M7a: mapped to an adapter that is not there - the commit fails instead of reading the
        // file with another adapter
        Path adapters = work.resolve(".ecco/.adapters");
        String mapped = Files.readString(adapters);
        Files.writeString(adapters, "no.such.Plugin;**.txt\n" + mapped);
        refused(work, "no.such.Plugin", "commit", "-c", "BASE, search", "-m", "no adapter");
        Files.writeString(adapters, mapped);

        // NFR-R1: the repository is as before the failures - the commit works, both variants check out
        commit(work, BASE_SEARCH, "BASE, search", "search");
        assertEquals("", checkout(work, "BASE", BASE));
        assertEquals("", checkout(work, "BASE, search", BASE_SEARCH));
    }

    /** UC-12 BA1: the remote's server is down. */
    @Test
    @Timeout(600)
    public void aPullOrPushWhileTheServerIsDownChangesNothing(@TempDir Path tmp) throws Exception {
        Path upstream = Files.createDirectories(tmp.resolve("upstream"));
        Path alice = Files.createDirectories(tmp.resolve("alice"));
        ok(upstream, "init");
        commit(upstream, BASE, "BASE", "base");

        int port;
        try (EccoServer server = new EccoServer(upstream, 0)) {
            port = server.port();
            ok(alice, "fork", server.address());
        }
        commit(upstream, BASE_SEARCH, "BASE, search", "search");
        commit(alice, BASE_EXPORT, "BASE, export", "export");

        // refused with the reason, alice and upstream unchanged
        Map<String, byte[]> served = tree(upstream.resolve(".ecco"));
        String unreachable = "Error connecting to remote localhost:" + port + ": Connection refused";
        refused(alice, unreachable, "pull");
        refused(alice, unreachable, "push");
        refused(alice, unreachable, "fetch");
        assertSameFiles(served, tree(upstream.resolve(".ecco")));

        // the server up again: the pull and the push work as if nothing had happened
        try (EccoServer server = new EccoServer(upstream, port)) {
            ok(alice, "pull");
            ok(alice, "push");
        }
        assertSameAsLinear(alice, linear(tmp, "linear-alice", BASE, BASE_EXPORT, BASE_SEARCH), Map.of(
                "BASE", BASE, "BASE, search", BASE_SEARCH, "BASE, export", BASE_EXPORT));
        assertSameAsLinear(upstream, linear(tmp, "linear-upstream", BASE, BASE_SEARCH, BASE_EXPORT), Map.of(
                "BASE", BASE, "BASE, search", BASE_SEARCH, "BASE, export", BASE_EXPORT));
    }

    /**
     * UC-12 SA6 (FR-D8c): the two repositories read LilyPond differently - the one created with
     * musical tokens off (JAVA_OPTS=-Decco.lilypond.musicalTokens=false) - so a pull of LilyPond
     * files is refused, naming the setting. Needs the LilyPond adapter's Python (lymodel).
     */
    @Test
    @Timeout(600)
    public void aPullBetweenRepositoriesThatReadFilesDifferentlyIsRefused(@TempDir Path tmp) throws Exception {
        Path music = Files.createDirectories(tmp.resolve("music"));
        Path other = Files.createDirectories(tmp.resolve("other"));
        ok(music, "init");
        Files.writeString(music.resolve("score.ly"), "\\relative c' { c4 d e f }\n");
        Result lilypond = EccoProcess.run(music, "commit", "-c", "BASE", "-m", "score");
        Assumptions.assumeTrue(lilypond.exitCode() == 0, () -> "the LilyPond adapter cannot run here (needs a Python with lymodel): " + lilypond);

        Result created = EccoProcess.run(List.of("-Decco.lilypond.musicalTokens=false"), other, "init");
        assertEquals(0, created.exitCode(), created.toString());
        assertTrue(Files.readString(other.resolve(".ecco/.settings")).contains("lilypond.musicalTokens=false"));
        commit(other, BASE, "BASE", "base");
        ok(other, "remotes", "--add", "music", music.toString(), "--type", "LOCAL");

        Map<String, byte[]> source = tree(music.resolve(".ecco"));
        refused(other, "lilypond.musicalTokens", "pull", "music");
        assertSameFiles(source, tree(music.resolve(".ecco")));
    }
}
