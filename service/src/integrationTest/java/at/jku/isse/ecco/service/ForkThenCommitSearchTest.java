package at.jku.isse.ecco.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static at.jku.isse.ecco.service.SyncHistory.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Random histories of many equal lines ("}", "{", empty), seeded: a fork, and its origin, take the same
 * next commit and stay alike - the same conditions and checkouts. A fork's order graphs had their
 * nodes' neighbours in another order than the origin's, and merging, which walks them, matched two
 * equal lines the other way round: a fork of tinycc lost a "}" in its next commit (these histories
 * rarely tie that way - PartialOrderGraphCopyTest checks the order itself).
 */
public class ForkThenCommitSearchTest {

    private static final int SEEDS = 60;
    private static final String[] LINES = {"}", "}", "{", "", "a", "b", "c", "d"};
    private static final String[] FEATURES = {"A", "B", "C", "D"};

    @Test
    @Timeout(1200)
    public void aForkAndItsOriginTakeTheSameCommitAlike(@TempDir Path tmp) throws Exception {
        List<String> wrong = new ArrayList<>();
        for (int seed = 0; seed < SEEDS; seed++) {
            Random random = new Random(seed);
            List<Step> history = new ArrayList<>();
            for (int i = 0, n = 4 + random.nextInt(5); i < n; i++)
                history.add(step(random));
            List<Step> base = history.subList(0, history.size() - 1);
            List<Step> next = history.subList(history.size() - 1, history.size());

            Path origin = commit(tmp.resolve("origin-" + seed), base);
            Path fork = fork(origin, tmp.resolve("fork-" + seed));
            commitInto(origin, next);
            commitInto(fork, next);
            if (!withoutRevisionIds(conditions(origin)).equals(withoutRevisionIds(conditions(fork)))
                    || !checkouts(origin, history, tmp.resolve("co-o-" + seed)).equals(checkouts(fork, history, tmp.resolve("co-f-" + seed))))
                wrong.add("seed " + seed + ": " + history);
        }
        assertEquals(List.of(), wrong);
    }

    /** a feature first committed in the next commit gets a revision id of its own in each repository */
    private static String withoutRevisionIds(java.util.Set<String> conditions) {
        return String.valueOf(conditions).replaceAll("\\.[0-9a-f]{7}\\b", ".*");
    }

    private static Step step(Random random) {
        List<String> configuration = new ArrayList<>();
        for (String feature : FEATURES)
            if (random.nextInt(2) == 0)
                configuration.add(feature);
        if (configuration.isEmpty())
            configuration.add("A");
        List<String> lines = new ArrayList<>();
        for (int i = 0, n = 3 + random.nextInt(8); i < n; i++)
            lines.add(LINES[random.nextInt(LINES.length)]);
        return new Step(String.join(", ", configuration), String.join("|", lines));
    }
}
