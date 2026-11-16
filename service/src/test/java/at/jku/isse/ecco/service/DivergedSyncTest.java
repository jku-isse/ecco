package at.jku.isse.ecco.service;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.repository.Repository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static at.jku.isse.ecco.service.SyncHistory.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Histories that diverged after a fork and were joined with pull and push check out as the same
 * commits made one after the other: the pulling repository's own first, then the ones pulled. Found
 * by a random search: committing a configuration again with other content on one side made a pull
 * lose a line of another configuration - "B, E" checked out "e0" in both repositories, committed in
 * order it is "b0 e0" - the pulled associations' counts were rebuilt from their conditions only, and
 * the history both share was counted twice.
 */
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

    @Test
    @Timeout(120)
    public void pullAndPushOverTheNetworkCheckOutAsTheCommitsInOrder(@TempDir Path tmp) throws Exception {
        Path a = commit(tmp.resolve("a"), BASE);
        Path b = forkOverTheNetwork(a, tmp.resolve("b"));
        commitInto(a, A);
        commitInto(b, B);
        pullAndPushOverTheNetwork(b, a);

        List<Step> all = concat(BASE, A, B);
        assertEquals(checkouts(commit(tmp.resolve("base-b-a"), concat(BASE, B, A)), all, tmp.resolve("co-bba")), checkouts(b, all, tmp.resolve("co-b")), "pulled");
        assertEquals(checkouts(commit(tmp.resolve("base-a-b"), concat(BASE, A, B)), all, tmp.resolve("co-bab")), checkouts(a, all, tmp.resolve("co-a")), "pushed to");
    }

    /**
     * Two pushes that asked for the commits missing before either was taken in send the same
     * commits: the second is taken in as nothing new, not as the same commits once more.
     */
    @Test
    @Timeout(120)
    public void commitsPushedTwiceAreTakenInOnce(@TempDir Path tmp) throws Exception {
        Path a = commit(tmp.resolve("a"), BASE);
        Path b = fork(a, tmp.resolve("b"));
        commitInto(b, B);
        Repository.Op stale;
        try (EccoService service = open(b); EccoService origin = open(a)) {
            Repository.Op repository = (Repository.Op) service.getRepository();
            stale = service.commitReplay.copyOfCommits(repository, repository.commitsMissingIn(RemoteSyncService.commitIds((Repository.Op) origin.getRepository()), List.of()));
        }
        push(b, a);
        List<String> commits = commits(a);
        Map<String, Integer> counts = moduleCounts(a);
        try (EccoService service = open(a)) {
            service.replay(stale, new java.util.ArrayList<>(stale.getCommits()));
        }

        assertEquals(commits, commits(a));
        assertEquals(counts, moduleCounts(a));
    }

    /**
     * A merge commit - a fork, pull or push of an older version recorded one per association, without
     * a configuration - cannot be committed again, and merging two repositories counts the history they
     * share twice and loses lines (gap #96): a pull or push that would bring one the receiver lacks is
     * refused and changes nothing. One the receiver has is no hindrance (sharedMergeCommitsLeaveSyncToReplay,
     * SyncPropertyTest), nor is one the sender lacks; a fork into a new repository shares nothing and merges.
     */
    @Test
    @Timeout(120)
    public void aPullOrPushBringingAMergeCommitIsRefused(@TempDir Path tmp) throws Exception {
        Path a = commit(tmp.resolve("a"), BASE);
        Path b = fork(a, tmp.resolve("b"));
        try (EccoService service = open(a)) {
            Repository.Op repository = (Repository.Op) service.getRepository();
            repository.addCommit(repository.getEntityFactory().createCommit(Repository.MERGE));
            service.store();
        }
        commitInto(a, A);
        commitInto(b, B);
        List<String> before = commits(b);

        // b pulling a's merge commit: refused, b as it was
        EccoException refused = org.junit.jupiter.api.Assertions.assertThrows(EccoException.class, () -> pull(b, a));
        assertTrue(messages(refused).contains("merge commits"), messages(refused));
        assertEquals(before, commits(b), "the refused pull changed nothing");

        // b pushing its own commits to a: none is a merge commit, so they are committed again
        push(b, a);
        assertTrue(checkout(a, "F", tmp.resolve("co-f-a")).contains("f0"), "b's content arrived");

        // a pushing to b would bring the merge commit: refused
        EccoException pushed = org.junit.jupiter.api.Assertions.assertThrows(EccoException.class, () -> push(a, b));
        assertTrue(messages(pushed).contains("merge commits"), messages(pushed));

        // a fork into a new repository merges, sharing nothing
        Path c = fork(a, tmp.resolve("c"));
        assertTrue(checkout(c, "B, E", tmp.resolve("co-c")).contains("e0"), "a's content arrived in the fork");
    }

    private static String messages(Throwable t) {
        StringBuilder text = new StringBuilder();
        for (; t != null; t = t.getCause())
            text.append(t.getMessage()).append(" / ");
        return text.toString();
    }
}
