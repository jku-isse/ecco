package at.jku.isse.ecco.service;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.core.Remote;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * `ecco fork <dir>` given a repository's working directory instead of its .ecco directory opened
 * the working directory itself as a repository: open() only rejected an empty directory, so it
 * wrote .adapters and .ignores into the SOURCE working directory and loaded an empty repository -
 * the fork then failed with "Feature with name does not exist" (or, without --exclude, forked
 * nothing). Local remotes of fetch/pull/push were opened the same way. open() now refuses a
 * directory without repository data, and fork and local remotes accept the working directory.
 */
public class ForkFromWorkingDirectoryTest {

    private static Set<String> names(Path dir) throws Exception {
        try (Stream<Path> files = Files.list(dir)) {
            return files.map(p -> p.getFileName().toString()).collect(Collectors.toSet());
        }
    }

    private static Path originWithOneCommit(Path workDir) throws Exception {
        Path origin = Files.createDirectories(workDir.resolve("origin"));
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(origin.resolve(".ecco"));
            service.init();
            service.setBaseDir(origin);
            Files.writeString(origin.resolve("a.txt"), "a\n");
            service.commit("a", "A");
            Files.writeString(origin.resolve("b.txt"), "b\n");
            service.commit("ab", "A, B");
        }
        return origin;
    }

    @Test
    @Timeout(60)
    public void openingADirectoryWithFilesButNoRepositoryFailsAndWritesNothing() throws Exception {
        Path dir = Files.createTempDirectory("open-working-dir");
        Files.writeString(dir.resolve("a.txt"), "a\n");
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(dir);
            assertThrows(EccoException.class, service::open);
            assertFalse(service.isInitialized());
        }
        assertEquals(Set.of("a.txt"), names(dir));
    }

    @Test
    @Timeout(60)
    public void forkingFromAWorkingDirectoryForksItsRepositoryAndLeavesTheSourceAlone() throws Exception {
        Path workDir = Files.createTempDirectory("fork-working-dir");
        Path origin = originWithOneCommit(workDir);
        Set<String> before = names(origin);

        Path target = Files.createDirectories(workDir.resolve("target"));
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(target.resolve(".ecco"));
            service.fork(origin, "");
            assertEquals(2, service.getRepository().getFeatures().size());
            assertFalse(service.getRepository().getAssociations().isEmpty());
        }
        assertEquals(before, names(origin), "the fork source must not be written to");
    }

    @Test
    @Timeout(60)
    public void fetchingFromALocalRemoteGivenAsWorkingDirectoryLeavesItAlone() throws Exception {
        Path workDir = Files.createTempDirectory("fetch-working-dir");
        Path origin = originWithOneCommit(workDir);
        Set<String> before = names(origin);

        Path target = Files.createDirectories(workDir.resolve("target"));
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(target.resolve(".ecco"));
            service.init();
            service.addRemote("origin", origin.toString(), Remote.Type.LOCAL);
            service.fetch("origin");
            assertEquals(2, service.getRemote("origin").getFeatures().size());
        }
        assertEquals(before, names(origin), "a local remote must not be written to");
    }
}
