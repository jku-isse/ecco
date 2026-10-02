package at.jku.isse.ecco.service;

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
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A local fork opens its origin with an ordinary open() (the TODO said it should open it read
 * only). It only reads the origin, in a read-only transaction - this pins that down: forking
 * leaves every file of the origin repository exactly as it was, contents and timestamps.
 */
public class ForkLeavesOriginUnchangedTest {

    @Test
    @Timeout(60)
    public void forkingDoesNotWriteToTheOrigin(@TempDir Path tmp) throws Exception {
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
            revisionOfB = service.getRepository().getFeatures().stream().filter(f -> f.getName().equals("B"))
                    .findFirst().orElseThrow().getLatestRevision().toString();
        }
        Map<String, String> before = fingerprint(origin.resolve(".ecco"));

        Path fork = Files.createDirectories(tmp.resolve("fork"));
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(fork.resolve(".ecco"));
            service.setBaseDir(fork);
            service.fork(origin, "");
        }
        Path forkWithout = Files.createDirectories(tmp.resolve("fork-without-b"));
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(forkWithout.resolve(".ecco"));
            service.setBaseDir(forkWithout);
            service.fork(origin.resolve(".ecco"), revisionOfB);
        }

        assertEquals(before, fingerprint(origin.resolve(".ecco")));
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
