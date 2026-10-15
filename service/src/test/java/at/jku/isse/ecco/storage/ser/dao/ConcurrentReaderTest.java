package at.jku.isse.ecco.storage.ser.dao;

import at.jku.isse.ecco.service.EccoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A reader holds its core's shared lock while it reads the repository (SerTransactionStrategy
 * #loadDatabase), and a commit deletes only the cores - and with them the packs - no reader holds:
 * released right after the core, a commit in another process deleted packs a reader was about to
 * read ("the pack ... is missing" for 37 of 214 opens while another process committed, storage
 * review C2). Here the reader is a lock held on the core, as a load in progress holds it.
 */
public class ConcurrentReaderTest {

    @Test
    @Timeout(120)
    public void aCommitLeavesWhatAReaderOfTheOldCoreNeeds(@TempDir Path tmp) throws Exception {
        Path repo = tmp.resolve(".ecco");
        Path content = Files.createDirectories(tmp.resolve("content"));
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repo);
            service.init();
            service.setBaseDir(content);
            for (int c = 1; c <= 3; c++) {
                Files.writeString(content.resolve("f.txt"), "version " + c + "\n");
                service.commit("c" + c, "A." + c);
            }
            Path oldCore = repo.resolve(Files.readString(repo.resolve("id")).trim() + ".ser.zip");
            Set<Path> filesBefore = files(repo);

            try (FileChannel channel = FileChannel.open(oldCore, StandardOpenOption.READ);
                 FileLock reading = channel.lock(0, Long.MAX_VALUE, true)) {
                Files.writeString(content.resolve("f.txt"), "version 4\n");
                service.commit("c4", "A.4");
                assertTrue(Files.exists(oldCore), "the core being read stays");
                Set<Path> missing = new HashSet<>(filesBefore);
                missing.removeAll(files(repo));
                assertEquals(Set.of(), missing, "nothing the core being read refers to is deleted");
            }

            // no reader anymore: the next commit deletes what only older cores referred to
            Files.writeString(content.resolve("f.txt"), "version 5\n");
            service.commit("c5", "A.5");
            assertFalse(Files.exists(oldCore), "a core nobody reads goes");
            try (Stream<Path> cores = Files.list(repo)) {
                assertEquals(1, cores.filter(p -> p.getFileName().toString().endsWith(".ser.zip")).count(), "one core left");
            }
        }
        Path out = Files.createDirectories(tmp.resolve("out"));
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repo);
            service.open();
            service.setBaseDir(out);
            service.checkout("A.3");
        }
        assertEquals("version 3\n", Files.readString(out.resolve("f.txt")));
    }

    private static Set<Path> files(Path repo) throws Exception {
        try (Stream<Path> walk = Files.walk(repo)) {
            return new HashSet<>(walk.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().endsWith(".zip")).toList());
        }
    }
}
