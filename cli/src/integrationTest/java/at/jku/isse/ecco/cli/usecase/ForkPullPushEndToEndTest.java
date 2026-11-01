package at.jku.isse.ecco.cli.usecase;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static at.jku.isse.ecco.cli.usecase.Shop.*;
import static at.jku.isse.ecco.cli.usecase.Steps.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * UC-11 Fork repository and UC-12 Exchange features with a remote (doc/requirements.md), through
 * the command line with local paths as remotes: collaborators fork a repository, commit on both
 * sides, and pull and push. Every command is an {@code ecco} process of its own, so every step
 * opens the repositories from disk. The steps in the comments are those of the use cases' flows.
 * <p>
 * The oracle is a linear history: after a fork, pull or push, a repository checks out every
 * configuration - committed or not, files and warnings - as a repository does that committed the
 * same variants in the same order (its own commits first, then the other side's new ones). The
 * side a command only reads is left byte for byte.
 */
public class ForkPullPushEndToEndTest {

    @Test
    @Timeout(600)
    public void collaboratorsForkPullAndPush(@TempDir Path tmp) throws Exception {
        Path upstream = Files.createDirectories(tmp.resolve("upstream"));
        Path alice = Files.createDirectories(tmp.resolve("alice"));
        Path bob = Files.createDirectories(tmp.resolve("bob"));

        ok(upstream, "init");
        commit(upstream, BASE, "BASE", "base");
        String search = revision(commit(upstream, BASE_SEARCH, "BASE, search", "search"), "search");

        // UC-11 BA1: a remote that holds no repository is refused, and nothing is created
        Path nowhere = Files.createDirectories(tmp.resolve("nowhere"));
        refused(nowhere, "", "fork", tmp.resolve("missing").toString());
        assertSameFiles(Map.of(), tree(nowhere));

        // UC-11 B1-B9: alice forks upstream - a copy, commits included; upstream unchanged (FR-D7)
        leavesRepository(upstream, alice, "fork", upstream.toString());
        assertTrue(ok(alice, "remotes").out().startsWith("origin: "));
        assertSameAsLinear(alice, linear(tmp, "linear-fork", BASE, BASE_SEARCH),
                Map.of("BASE", BASE, "BASE, search", BASE_SEARCH));

        // UC-11 SA2: a fork where a repository is already is refused, and the repository unchanged
        refused(alice, "A repository already exists", "fork", upstream.toString());

        // UC-11 B6: bob forks without search - its commit is left out, and with it the feature
        leavesRepository(upstream, bob, "fork", "--exclude", search, upstream.toString());
        assertFalse(ok(bob, "features").out().contains("search"));
        assertSameAsLinear(bob, linear(tmp, "linear-fork-without-search", BASE), Map.of("BASE", BASE));

        // both sides go on: alice adds export, upstream logging
        commit(alice, BASE_EXPORT, "BASE, export", "export");
        commit(upstream, BASE_LOGGING, "BASE, logging", "logging");

        // UC-12 SA3: an unknown remote is refused, both repositories unchanged
        refused(alice, "Remote 'elsewhere' does not exist.", "pull", "elsewhere");

        // UC-12 SA2: fetch reads the remote's features only, the remote unchanged; alice cannot
        // check out logging yet
        leavesRepository(upstream, alice, "fetch");
        clear(alice);
        refused(alice, "", "checkout", "-c", "BASE, logging");

        // UC-12 B1-B9: alice pulls - upstream's logging commit after her own; upstream unchanged
        leavesRepository(upstream, alice, "pull");
        Map<String, Map<String, byte[]>> all = all();
        Path linearAlice = linear(tmp, "linear-alice", BASE, BASE_SEARCH, BASE_EXPORT, BASE_LOGGING);
        assertSameAsLinear(alice, linearAlice, all);
        // pulling again changes nothing - in what alice checks out: the repository is stored
        // again all the same, a pull takes over the remote's constraint decisions without commits
        leavesRepository(upstream, alice, "pull");
        assertSameAsLinear(alice, linearAlice, all);

        // UC-12 SA1: alice pushes - her export commit after upstream's own; alice unchanged
        leavesRepository(alice, alice, "push");
        assertSameAsLinear(upstream, linear(tmp, "linear-upstream", BASE, BASE_SEARCH, BASE_LOGGING, BASE_EXPORT), all);

        // UC-12 B5: bob pulls without search - logging and export, in upstream's order
        leavesRepository(upstream, bob, "pull", "--exclude", search);
        assertFalse(ok(bob, "features").out().contains("search"));
        Map<String, Map<String, byte[]>> withoutSearch = new LinkedHashMap<>();
        withoutSearch.put("BASE", BASE);
        withoutSearch.put("BASE, logging", BASE_LOGGING);
        withoutSearch.put("BASE, export", BASE_EXPORT);
        withoutSearch.put("BASE, export, logging", BASE_EXPORT_LOGGING);
        assertSameAsLinear(bob, linear(tmp, "linear-bob", BASE, BASE_LOGGING, BASE_EXPORT), withoutSearch);
    }

    /** The configurations checked: the committed ones and two never committed as such. */
    private static Map<String, Map<String, byte[]>> all() {
        Map<String, Map<String, byte[]>> configurations = new LinkedHashMap<>();
        configurations.put("BASE", BASE);
        configurations.put("BASE, search", BASE_SEARCH);
        configurations.put("BASE, export", BASE_EXPORT);
        configurations.put("BASE, logging", BASE_LOGGING);
        configurations.put("BASE, export, logging", BASE_EXPORT_LOGGING);
        configurations.put("BASE, search, export, logging", BASE_SEARCH_EXPORT_LOGGING);
        return configurations;
    }

    /** A repository that committed the variants in this order, with the configurations they have in {@link Shop}. */
    @SafeVarargs
    private static Path linear(Path tmp, String name, Map<String, byte[]>... variants) throws Exception {
        Path work = Files.createDirectories(tmp.resolve(name));
        ok(work, "init");
        for (Map<String, byte[]> variant : variants)
            commit(work, variant, configuration(variant), "linear");
        return work;
    }

    private static String configuration(Map<String, byte[]> variant) {
        if (variant == BASE) return "BASE";
        if (variant == BASE_SEARCH) return "BASE, search";
        if (variant == BASE_EXPORT) return "BASE, export";
        if (variant == BASE_LOGGING) return "BASE, logging";
        throw new IllegalArgumentException("not a committed variant");
    }

    /**
     * Each configuration checks out from the repository as from the linear one - the same files
     * and the same warnings - and as the expected files.
     */
    private static void assertSameAsLinear(Path work, Path linear, Map<String, Map<String, byte[]>> configurations) throws Exception {
        for (Map.Entry<String, Map<String, byte[]>> configuration : configurations.entrySet()) {
            String warnings = checkout(work, configuration.getKey(), configuration.getValue());
            String linearWarnings = checkout(linear, configuration.getKey(), configuration.getValue());
            assertEquals(linearWarnings, warnings, work.getFileName() + ": " + configuration.getKey());
            if (List.of("BASE", "BASE, search", "BASE, export", "BASE, logging").contains(configuration.getKey()))
                assertEquals("", warnings, work.getFileName() + ": " + configuration.getKey());
        }
    }
}
