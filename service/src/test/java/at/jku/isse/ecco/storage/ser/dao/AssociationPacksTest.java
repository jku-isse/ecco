package at.jku.isse.ecco.storage.ser.dao;

import at.jku.isse.ecco.service.EccoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The associations a write changed go into one pack (see AssociationStore): a commit writes one
 * file for them however many it changed, and many commits leave few packs.
 */
public class AssociationPacksTest {

    @Test
    @Timeout(180)
    public void aCommitWritesOnePackAndPacksStayFew(@TempDir Path tmp) throws Exception {
        Path repo = tmp.resolve(".ecco");
        Path content = Files.createDirectories(tmp.resolve("content"));
        StringBuilder text = new StringBuilder();
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repo);
            service.init();
            service.setBaseDir(content);
            for (int c = 1; c <= 30; c++) {
                text.append("line ").append(c).append('\n');
                Files.writeString(content.resolve("f.txt"), text.toString());
                Files.writeString(content.resolve("g" + (c % 3) + ".txt"), "g " + c + "\n");
                List<Path> before = files(repo.resolve("associations"));
                service.commit("c" + c, "A." + c + (c % 2 == 0 ? ", B" : ""));
                List<Path> added = files(repo.resolve("associations")).stream().filter(f -> !before.contains(f)).toList();
                assertTrue(added.size() <= 1, "commit " + c + " added " + added);
            }
        }
        List<Path> packs = files(repo.resolve("associations"));
        assertTrue(packs.size() <= 20, packs.size() + " packs");
        assertTrue(packs.stream().allMatch(p -> p.getFileName().toString().startsWith("pack-")), packs.toString());
        Path out = Files.createDirectories(tmp.resolve("out"));
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repo);
            service.open();
            service.setBaseDir(out);
            service.checkout("A.30, B");
        }
        assertEquals(text.toString(), Files.readString(out.resolve("f.txt")));
    }

    private static List<Path> files(Path dir) throws Exception {
        if (!Files.isDirectory(dir))
            return List.of();
        try (Stream<Path> files = Files.list(dir)) {
            return files.sorted(Comparator.naturalOrder()).toList();
        }
    }
}
