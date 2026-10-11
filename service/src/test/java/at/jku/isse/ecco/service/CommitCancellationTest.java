package at.jku.isse.ecco.service;

import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.service.listener.EccoListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Cancel while committing (Known gap #38): {@link EccoService#cancelCommit()} stops the running
 * commit before it stores anything, so the repository is as before and takes the next commit.
 */
public class CommitCancellationTest {

    @Test
    @Timeout(60)
    public void aCancelledCommitLeavesTheRepositoryAsItWas() throws Exception {
        Path workDir = Files.createTempDirectory("commit-cancellation");
        Path repoDir = workDir.resolve(".ecco");
        Path content = Files.createDirectories(workDir.resolve("content"));
        Files.writeString(content.resolve("a.txt"), "a\n");
        Files.writeString(content.resolve("b.txt"), "b\n");

        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repoDir);
            service.init();
            service.setBaseDir(content);
            service.commit("first", "A");

            Files.writeString(content.resolve("a.txt"), "a\nmore\n");
            AtomicInteger reads = new AtomicInteger();
            EccoListener cancelOnFirstRead = new EccoListener() {
                @Override
                public void fileReadEvent(Path file, ArtifactReader reader) {
                    if (reads.incrementAndGet() == 1)
                        service.cancelCommit();
                }
            };
            service.addListener(cancelOnFirstRead);
            assertThrows(CommitCancelledException.class, () -> service.commit("second", "B"));
            assertEquals(1, reads.get(), "the commit stops at the next file read");
            service.removeListener(cancelOnFirstRead);

            assertEquals(1, service.getCommits().size());
            service.commit("second", "B");
            assertEquals(2, service.getCommits().size());
        }

        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repoDir);
            service.open();
            assertEquals(2, service.getCommits().size());
            Path out = Files.createDirectories(workDir.resolve("out"));
            service.setBaseDir(out);
            service.checkout("A");
            assertEquals("a\n", Files.readString(out.resolve("a.txt")));
        }
    }

    @Test
    @Timeout(60)
    public void cancellingWithoutACommitInProgressDoesNotStopTheNextOne() throws Exception {
        Path workDir = Files.createTempDirectory("commit-cancellation");
        Path content = Files.createDirectories(workDir.resolve("content"));
        Files.writeString(content.resolve("a.txt"), "a\n");

        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(workDir.resolve(".ecco"));
            service.init();
            service.setBaseDir(content);
            service.cancelCommit();
            service.commit("first", "A");
            assertEquals(1, service.getCommits().size());
        }
    }
}
