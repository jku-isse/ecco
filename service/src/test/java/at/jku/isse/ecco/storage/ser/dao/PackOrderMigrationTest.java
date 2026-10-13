package at.jku.isse.ecco.storage.ser.dao;

import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.storage.ser.repository.SerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A core written before packs were ordered holds the artifact index itself - two entries per
 * artifact, written again with every commit. Such a repository (written by an earlier build,
 * 2026-10-13: three variants of two text files, in two packs) still checks out; its next write puts
 * every artifact into one new pack, drops the index from the core, and the repository checks out the
 * same after reopening.
 */
@Timeout(60)
public class PackOrderMigrationTest {

    private static final List<Map<String, String>> VERSIONS = List.of(
            Map.of("a.txt", "one\ntwo\n", "b.txt", "b\n"),
            Map.of("a.txt", "one\ntwo\nthree\n", "b.txt", "b\n"),
            Map.of("a.txt", "one\nthree\n", "b.txt", "b2\n"));

    @Test
    public void aRepositoryWithTheIndexInItsCoreIsMigratedByItsNextWrite(@TempDir Path tmp) throws Exception {
        Path repo = copy(Path.of("src/test/resources/repositories/before-pack-order/.ecco"), tmp.resolve("old"));

        try (EccoService service = open(repo)) {
            assertTrue(((SerRepository) service.getRepository()).hasLegacyArtifactIndex(), "precondition: the index is in the core");
            assertCheckouts(service, tmp.resolve("before"));
            Path v3 = Files.createDirectories(tmp.resolve("v3"));
            Files.writeString(v3.resolve("a.txt"), "one\nthree\nfour\n");
            Files.writeString(v3.resolve("b.txt"), "b2\n");
            service.setBaseDir(v3);
            service.commit("v3", "BASE, V3");
        }

        try (EccoService service = open(repo)) {
            assertFalse(((SerRepository) service.getRepository()).hasLegacyArtifactIndex(), "the core has no index anymore");
            assertCheckouts(service, tmp.resolve("after"));
            Path out = Files.createDirectories(tmp.resolve("after-v3"));
            service.setBaseDir(out);
            service.checkout("BASE, V3");
            assertEquals("one\nthree\nfour\n", Files.readString(out.resolve("a.txt")));
        }
        try (Stream<Path> packs = Files.list(repo.resolve(".ecco/artifacts"))) {
            assertEquals(1, packs.count(), "every artifact in one pack, the old ones gone");
        }
    }

    private static void assertCheckouts(EccoService service, Path root) throws Exception {
        for (int i = 0; i < VERSIONS.size(); i++) {
            Path out = Files.createDirectories(root.resolve("v" + i));
            service.setBaseDir(out);
            service.checkout("BASE, V" + i);
            Map<String, String> files = new TreeMap<>();
            for (String name : VERSIONS.get(i).keySet())
                files.put(name, Files.readString(out.resolve(name)));
            assertEquals(new TreeMap<>(VERSIONS.get(i)), files, "v" + i);
        }
    }

    private static EccoService open(Path repo) {
        EccoService service = new EccoService();
        service.setRepositoryDir(repo.resolve(".ecco"));
        service.open();
        return service;
    }

    private static Path copy(Path from, Path repo) throws Exception {
        try (Stream<Path> paths = Files.walk(from)) {
            for (Path p : paths.toList()) {
                Path target = repo.resolve(".ecco").resolve(from.relativize(p).toString());
                if (Files.isDirectory(p)) Files.createDirectories(target);
                else Files.copy(p, target);
            }
        }
        return repo;
    }
}
