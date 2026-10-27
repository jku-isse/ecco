package at.jku.isse.ecco.service;

import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.repository.Repository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static at.jku.isse.ecco.service.SyncHistory.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Replaying a repository's commits, in their order, into an empty one gives the repository that
 * committing their files gave: the same commits, counts, conditions and checkouts - what pull and push
 * build on (Repository.Op#replay).
 */
public class ReplayTest {

    private static final List<List<Step>> HISTORIES = List.of(
            steps("A=a", "A, B=a|b", "A, C=a|c"),
            // the same configuration again with other content; a line moved; a line removed
            steps("A=a|x", "A, B=a|b|x", "A=a|y", "A, B=b|a|y", "A, C=a|c"),
            steps("B=b0", "B=b0", "B, E=b0|e0", "B=b1", "F=f0"),
            steps("A, B, C, D=a1|b0|c0|d0", "A, B, D=a1|b0|d0", "B, C, E=b1|c0|e0", "B=b1", "A, B, D=a2|b1|d0"));

    @Test
    @Timeout(300)
    public void replayingTheCommitsGivesTheRepositoryCommittingThemGave(@TempDir Path tmp) throws Exception {
        int i = 0;
        for (List<Step> history : HISTORIES) {
            Path committed = commit(tmp.resolve("committed-" + i), history);
            Path replayed = replay(committed, tmp.resolve("replayed-" + i));

            assertEquals(commits(committed), commits(replayed), "commits of " + history);
            assertEquals(moduleCounts(committed), moduleCounts(replayed), "counts of " + history);
            assertEquals(conditions(committed), conditions(replayed), "conditions of " + history);
            assertEquals(checkouts(committed, history, tmp.resolve("co-committed-" + i)), checkouts(replayed, history, tmp.resolve("co-replayed-" + i)), "checkouts of " + history);
            i++;
        }
    }

    /** a new repository with the commits of [from] replayed */
    static Path replay(Path from, Path dir) throws Exception {
        Files.createDirectories(dir);
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(dir.resolve(".ecco"));
            service.init();
        }
        try (EccoService source = open(from); EccoService target = open(dir)) {
            List<Commit> commits = new ArrayList<>(source.getCommits());
            commits.sort(Comparator.comparing(Commit::getDate).thenComparing(Commit::getId));
            target.replay((Repository.Op) source.getRepository(), commits);
        }
        return dir;
    }
}
