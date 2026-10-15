package at.jku.isse.ecco.storage.ser.dao;

import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.service.EccoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A repository written by an older version, converted as a whole (EccoService#convertToCurrentFormat):
 * nothing of the older layout is left - no file per association, no artifact index in the core - and
 * every committed configuration checks out the same files as before.
 */
public class ConvertToCurrentFormatTest {

    @Test
    @Timeout(120)
    public void anOlderRepositoryIsWrittenAgainWhole(@TempDir Path tmp) throws Exception {
        Path repo = tmp.resolve("old/.ecco");
        copy(Path.of("src/test/resources/repositories/before-pack-order/.ecco"), repo);
        Map<String, Map<String, String>> before = checkouts(repo, tmp.resolve("before"));
        assertFalse(before.isEmpty());

        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repo);
            service.open();
            assertTrue(service.convertToCurrentFormat() > 0);
        }

        try (Stream<Path> associations = Files.list(repo.resolve("associations"))) {
            assertTrue(associations.allMatch(p -> p.getFileName().toString().startsWith("pack-")), "only association packs left");
        }
        try (Stream<Path> artifacts = Files.list(repo.resolve("artifacts"))) {
            assertTrue(artifacts.allMatch(p -> p.getFileName().toString().startsWith("pack-")), "only artifact packs left");
        }
        assertTrue(Files.isDirectory(repo.resolve("pieces")), "trees in pieces");
        assertEquals(before, checkouts(repo, tmp.resolve("after")));
    }

    /** The files each committed configuration checks out, by configuration (.hashes holds a date and paths). */
    private static Map<String, Map<String, String>> checkouts(Path repo, Path out) throws Exception {
        Map<String, Map<String, String>> result = new TreeMap<>();
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repo);
            service.open();
            Set<String> configurations = new LinkedHashSet<>();
            for (Commit commit : service.getRepository().getCommits())
                configurations.add(commit.getConfiguration().toString());
            int i = 0;
            for (String configuration : configurations) {
                Path dir = Files.createDirectories(out.resolve("c" + i++));
                service.setBaseDir(dir);
                service.checkout(configuration);
                Map<String, String> files = new TreeMap<>();
                try (Stream<Path> walk = Files.walk(dir)) {
                    for (Path f : walk.filter(Files::isRegularFile).filter(f -> !f.getFileName().toString().equals(".hashes")).toList())
                        files.put(dir.relativize(f).toString(), Files.readString(f));
                }
                result.put(configuration, files);
            }
        }
        return result;
    }

    private static void copy(Path from, Path to) throws Exception {
        try (Stream<Path> walk = Files.walk(from)) {
            for (Path p : walk.sorted().toList()) {
                Path t = to.resolve(from.relativize(p).toString());
                if (Files.isDirectory(p)) Files.createDirectories(t); else Files.copy(p, t);
            }
        }
    }
}
