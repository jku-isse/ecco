package at.jku.isse.ecco.service;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static at.jku.isse.ecco.service.SyncHistory.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Random small histories (seeded, so a failure names its history): a base, a fork, commits on both
 * sides, then the fork pulls and pushes. Each repository checks out as the same commits made in
 * order - its own first, then the other side's. Features new after the fork are E on the origin's
 * side and F on the fork's; a line changes now and then without a new revision, as in real histories.
 * The search that found DivergedSyncTest's history: 33 of 200 histories lost lines.
 */
@Disabled("commit-replay sync, step 4")
public class DivergedSyncSearchTest {

    private static final int SEEDS = 40;

    @Test
    @Timeout(1200)
    public void syncedRepositoriesCheckOutAsTheirCommitsInOrder(@TempDir Path tmp) throws Exception {
        List<String> wrong = new ArrayList<>();
        for (int seed = 0; seed < SEEDS; seed++) {
            Random random = new Random(seed);
            Map<String, Integer> version = new HashMap<>();
            Set<String> known = new LinkedHashSet<>();
            List<Step> base = randomSteps(random, version, known, null, 1 + random.nextInt(3));
            List<Step> a = randomSteps(random, version, known, "E", 1 + random.nextInt(2));
            List<Step> b = randomSteps(random, version, known, "F", 1 + random.nextInt(2));

            Path root = tmp.resolve("seed-" + seed);
            Path ra = commit(root.resolve("a"), base);
            Path rb = fork(ra, root.resolve("b"));
            commitInto(ra, a);
            commitInto(rb, b);
            pull(rb, ra);
            push(rb, ra);

            List<Step> all = concat(base, a, b);
            Map<String, String> pulled = checkouts(rb, all, root.resolve("co-b"));
            Map<String, String> pushedTo = checkouts(ra, all, root.resolve("co-a"));
            if (!pulled.equals(checkouts(commit(root.resolve("base-b-a"), concat(base, b, a)), all, root.resolve("co-bba"))))
                wrong.add("seed " + seed + " pulled: " + base + " " + a + " " + b);
            if (!pushedTo.equals(checkouts(commit(root.resolve("base-a-b"), concat(base, a, b)), all, root.resolve("co-bab"))))
                wrong.add("seed " + seed + " pushed to: " + base + " " + a + " " + b);
        }
        assertEquals(List.of(), wrong);
    }
}
