package at.jku.isse.ecco.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static at.jku.isse.ecco.service.SyncHistory.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A fork is a copy of its origin: the same commits (ids, configurations, messages, and what each
 * holds), the same counts and the same conditions - so pulling into it later can tell which commits
 * both have, and counts each once. It had one merge commit per association, without a configuration,
 * and its counts rebuilt from the conditions, counted once.
 */
public class ForkCopyTest {

    // the same configuration committed again with other content, and a feature added later
    private static final List<Step> HISTORY = steps("A=a", "A, B=a|b", "A=a|y", "A, B=a|y|b", "A, C=a|y|c");

    @Test
    @Timeout(120)
    public void aForkHasTheCommitsCountsAndConditionsOfItsOrigin(@TempDir Path tmp) throws Exception {
        Path origin = commit(tmp.resolve("origin"), HISTORY);
        Path fork = fork(origin, tmp.resolve("fork"));

        assertEquals(commits(origin), commits(fork));
        assertEquals(moduleCounts(origin), moduleCounts(fork));
        assertEquals(conditions(origin), conditions(fork));
        assertEquals(checkouts(origin, HISTORY, tmp.resolve("co-origin")), checkouts(fork, HISTORY, tmp.resolve("co-fork")));
    }
}
