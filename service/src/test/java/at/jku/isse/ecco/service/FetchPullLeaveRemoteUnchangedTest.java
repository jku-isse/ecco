package at.jku.isse.ecco.service;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.core.Remote;
import at.jku.isse.ecco.feature.Feature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Like a local fork (ForkLeavesOriginUnchangedTest), a local fetch and pull open the other
 * repository with an ordinary open() under a "TODO: init read only!". This pins down that they only
 * read it: every file of the remote repository stays as it was, contents and timestamps, also when
 * the pull excludes revisions or fails.
 */
public class FetchPullLeaveRemoteUnchangedTest {

    @Test
    @Timeout(60)
    public void localFetchAndPullDoNotWriteToTheRemote(@TempDir Path tmp) throws Exception {
        Path origin = Files.createDirectories(tmp.resolve("origin"));
        String revisionOfB;
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(origin.resolve(".ecco"));
            service.init();
            service.setBaseDir(origin);
            Files.writeString(origin.resolve("a.txt"), "a\n");
            service.commit("a", "A");
            Files.writeString(origin.resolve("b.txt"), "b\n");
            service.commit("a and b", "A, B");
            revisionOfB = latestRevision(service, "B");
        }
        Map<String, String> before = fingerprint(origin.resolve(".ecco"));

        Path target = Files.createDirectories(tmp.resolve("target"));
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(target.resolve(".ecco"));
            service.init();
            service.setBaseDir(target);
            service.addRemote("origin", origin.toString(), Remote.Type.LOCAL);

            service.fetch("origin");
            service.pull("origin", revisionOfB);
            assertEquals(Set.of("A"), featureNames(service));
            service.pull("origin");
            assertEquals(Set.of("A", "B"), featureNames(service));
            // fails after the remote is opened: the exclusion is parsed against the remote
            assertThrows(EccoException.class, () -> service.pull("origin", "Unknown.1"));
        }

        assertEquals(before, fingerprint(origin.resolve(".ecco")));
    }

    private static String latestRevision(EccoService service, String feature) {
        return service.getRepository().getFeatures().stream().filter(f -> f.getName().equals(feature))
                .findFirst().orElseThrow().getLatestRevision().toString();
    }

    private static Set<String> featureNames(EccoService service) {
        return service.getRepository().getFeatures().stream().map(Feature::getName).collect(Collectors.toSet());
    }

    /** relative path -> content hash and modification time, for every file and directory */
    private static Map<String, String> fingerprint(Path dir) throws IOException, NoSuchAlgorithmException {
        Map<String, String> result = new TreeMap<>();
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path p : paths.toList()) {
                String key = dir.relativize(p).toString();
                String time = Files.getLastModifiedTime(p).toString();
                if (Files.isDirectory(p)) {
                    result.put(key + "/", time);
                } else {
                    byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p));
                    result.put(key, HexFormat.of().formatHex(digest) + " " + time);
                }
            }
        }
        return result;
    }
}
