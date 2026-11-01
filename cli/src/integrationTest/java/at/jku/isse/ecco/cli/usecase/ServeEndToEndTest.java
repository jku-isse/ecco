package at.jku.isse.ecco.cli.usecase;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static at.jku.isse.ecco.cli.usecase.Shop.*;
import static at.jku.isse.ecco.cli.usecase.Steps.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * UC-13 Serve repository for synchronization (doc/requirements.md), with UC-11 and UC-12 over the
 * socket: upstream's collaborator serves the repository, alice and bob fork, pull and push with
 * {@code host:port} remotes. The server runs in a JVM of its own ({@link EccoServer}) - in ECCO it
 * runs in the GUI, which the command line has not - and every client command is an {@code ecco}
 * process of its own. The steps in the comments are those of the use cases' flows.
 * <p>
 * The oracle is that of {@link ForkPullPushEndToEndTest}, linear histories, and the repository
 * served is left byte for byte by every request but a push. After the server stops, upstream is
 * opened from disk and holds what was pushed (UC-13 postcondition).
 */
public class ServeEndToEndTest {

    @Test
    @Timeout(600)
    public void collaboratorsSyncWithAServedRepository(@TempDir Path tmp) throws Exception {
        Path upstream = Files.createDirectories(tmp.resolve("upstream"));
        Path alice = Files.createDirectories(tmp.resolve("alice"));
        Path bob = Files.createDirectories(tmp.resolve("bob"));

        ok(upstream, "init");
        commit(upstream, BASE, "BASE", "base");
        String search = revision(commit(upstream, BASE_SEARCH, "BASE, search", "search"), "search");

        // UC-13 B1-B5: upstream is served on a port of the loopback interface
        int port;
        try (EccoServer server = new EccoServer(upstream, 0)) {
            port = server.port();
            // UC-13 SA1: a second start is refused, the running server unchanged
            assertEquals("REFUSED Server is already running.", server.startAgain());

            // UC-13 B6-B9 with UC-11 B1-B9: alice forks, bob forks without search; served unchanged
            Map<String, byte[]> served = tree(upstream.resolve(".ecco"));
            ok(alice, "fork", server.address());
            ok(bob, "fork", "--exclude", search, server.address());
            assertSameFiles(served, tree(upstream.resolve(".ecco")));
            assertEquals("origin: " + server.address() + " [REMOTE]", ok(alice, "remotes").out().strip());

            // UC-13 B5, B9: the log - started on the port bound, a copy and a fork that replays
            List<String> log = server.events();
            assertEquals("Server started on port " + port + " (local connections only).", log.get(0));
            assertEquals(List.of("CLONE", "PULL_COMMITS"), transfers(log));
        }
        // UC-13 B10-B11: stopped, the port is free again
        try (java.net.ServerSocket free = new java.net.ServerSocket(port, 1, java.net.InetAddress.getLoopbackAddress())) {
            assertEquals(port, free.getLocalPort());
        }

        assertSameAsLinear(alice, linear(tmp, "linear-fork", BASE, BASE_SEARCH),
                Map.of("BASE", BASE, "BASE, search", BASE_SEARCH));
        assertFalse(ok(bob, "features").out().contains("search"));
        assertSameAsLinear(bob, linear(tmp, "linear-fork-without-search", BASE), Map.of("BASE", BASE));

        // both sides go on, the server stopped: alice adds export, upstream logging
        commit(alice, BASE_EXPORT, "BASE, export", "export");
        commit(upstream, BASE_LOGGING, "BASE, logging", "logging");

        // served again on the same port, the one alice's and bob's origin names
        try (EccoServer server = new EccoServer(upstream, port)) {
            // UC-12 SA2 and B1-B9: alice fetches and pulls, twice; served unchanged
            Map<String, byte[]> served = tree(upstream.resolve(".ecco"));
            ok(alice, "fetch");
            ok(alice, "pull");
            ok(alice, "pull");
            assertSameFiles(served, tree(upstream.resolve(".ecco")));

            // UC-12 SA1: alice pushes, unchanged herself
            leavesRepository(alice, alice, "push");

            // UC-12 B5: bob pulls without search, after alice's push
            ok(bob, "pull", "--exclude", search);
            // UC-13 B9: fetch, pull, pull, push, pull - each a connection of its own, all local
            List<String> log = server.events();
            assertEquals(List.of("FETCH", "PULL_COMMITS", "PULL_COMMITS", "PUSH_COMMITS", "PULL_COMMITS"), transfers(log));
            assertTrue(log.stream().skip(1).allMatch(event -> event.startsWith("New connection from /127.0.0.1:")), log.toString());
        }

        // UC-13 postcondition: upstream holds what was pushed, opened from disk
        assertSameAsLinear(upstream, linear(tmp, "linear-upstream", BASE, BASE_SEARCH, BASE_LOGGING, BASE_EXPORT), ALL);
        assertSameAsLinear(alice, linear(tmp, "linear-alice", BASE, BASE_SEARCH, BASE_EXPORT, BASE_LOGGING), ALL);
        assertFalse(ok(bob, "features").out().contains("search"));
        assertSameAsLinear(bob, linear(tmp, "linear-bob", BASE, BASE_LOGGING, BASE_EXPORT), WITHOUT_SEARCH);
    }

    /**
     * The requests of the log that move features or commits, in their order - without those that
     * ask for settings or commit ids before them.
     */
    private static List<String> transfers(List<String> log) {
        return log.stream()
                .map(event -> event.replaceFirst(".* with command '(\\w+)'\\.$", "$1"))
                .filter(List.of("CLONE", "FETCH", "PULL", "PUSH", "PULL_COMMITS", "PUSH_COMMITS")::contains)
                .toList();
    }
}
