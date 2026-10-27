package at.jku.isse.ecco.service;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static at.jku.isse.ecco.service.SyncHistory.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Histories that diverged after a fork and were joined with pull and push check out as the same
 * commits made one after the other: the pulling repository's own first, then the ones pulled. Found
 * by a random search: committing a configuration again with other content on one side made a pull
 * lose a line of another configuration - "B, E" checked out "e0" in both repositories, committed in
 * order it is "b0 e0" - the pulled associations' counts were rebuilt from their conditions only, and
 * the history both share was counted twice.
 */
@Disabled("commit-replay sync, step 4")
public class DivergedSyncTest {

    private static final List<Step> BASE = steps("B=b0");
    private static final List<Step> A = steps("B=b0", "B, E=b0|e0");
    private static final List<Step> B = steps("B=b1", "F=f0");

    @Test
    @Timeout(120)
    public void pullAndPushOfDivergedHistoriesCheckOutAsTheCommitsInOrder(@TempDir Path tmp) throws Exception {
        Path a = commit(tmp.resolve("a"), BASE);
        Path b = fork(a, tmp.resolve("b"));
        commitInto(a, A);
        commitInto(b, B);
        pull(b, a);
        push(b, a);

        List<Step> all = concat(BASE, A, B);
        // b pulled: its own commits first, then a's; a was pushed to: its own first, then b's
        assertEquals(checkouts(commit(tmp.resolve("base-b-a"), concat(BASE, B, A)), all, tmp.resolve("co-bba")), checkouts(b, all, tmp.resolve("co-b")), "pulled");
        assertEquals(checkouts(commit(tmp.resolve("base-a-b"), concat(BASE, A, B)), all, tmp.resolve("co-bab")), checkouts(a, all, tmp.resolve("co-a")), "pushed to");
    }
}
