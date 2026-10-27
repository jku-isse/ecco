package at.jku.isse.ecco.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static at.jku.isse.ecco.service.SyncHistory.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pulling again with nothing new changes nothing: a pull sent the whole remote, and merging it added
 * every count again, as if each of its commits had been made once more.
 */
public class RepeatedPullTest {

    private static final List<Step> HISTORY = steps("A=a", "A, B=a|b");
    private static final List<Step> MORE = steps("A, C=a|c", "A=a|y");

    @Test
    @Timeout(120)
    public void aSecondPullWithNothingNewChangesNothing(@TempDir Path tmp) throws Exception {
        Path origin = commit(tmp.resolve("origin"), HISTORY);
        Path fork = fork(origin, tmp.resolve("fork"));
        commitInto(origin, MORE);

        pull(fork, origin);
        List<String> commits = commits(fork);
        var counts = moduleCounts(fork);
        var conditions = conditions(fork);

        pull(fork, origin);
        assertEquals(commits, commits(fork));
        assertEquals(counts, moduleCounts(fork));
        assertEquals(conditions, conditions(fork));
    }
}
