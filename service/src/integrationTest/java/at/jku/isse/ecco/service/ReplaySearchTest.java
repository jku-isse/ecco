package at.jku.isse.ecco.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;

import static at.jku.isse.ecco.service.SyncHistory.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Random small histories (seeded): replaying a repository's commits into an empty one gives the same
 * commits, counts and checkouts as committing their files did (ReplayTest, Repository.Op#replay).
 */
public class ReplaySearchTest {

    private static final int SEEDS = 60;

    @Test
    @Timeout(1200)
    public void replayingRandomHistoriesGivesTheRepositoryCommittingThemGave(@TempDir Path tmp) throws Exception {
        List<String> wrong = new ArrayList<>();
        for (int seed = 0; seed < SEEDS; seed++) {
            Random random = new Random(seed);
            List<Step> history = randomSteps(random, new HashMap<>(), new LinkedHashSet<>(), null, 2 + random.nextInt(5));
            Path committed = commit(tmp.resolve("committed-" + seed), history);
            Path replayed = ReplayTest.replay(committed, tmp.resolve("replayed-" + seed));
            if (!commits(committed).equals(commits(replayed)) || !moduleCounts(committed).equals(moduleCounts(replayed))
                    || !checkouts(committed, history, tmp.resolve("co-c-" + seed)).equals(checkouts(replayed, history, tmp.resolve("co-r-" + seed))))
                wrong.add("seed " + seed + ": " + history);
        }
        assertEquals(List.of(), wrong);
    }
}
