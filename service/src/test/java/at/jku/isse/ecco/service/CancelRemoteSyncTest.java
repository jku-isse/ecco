package at.jku.isse.ecco.service;

import at.jku.isse.ecco.core.Remote;
import at.jku.isse.ecco.feature.Feature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fetch, pull and push can be cancelled: they ask whether to stop at safe points - before anything is
 * written - and a cancelled one changes nothing, in either repository; the transaction is rolled
 * back. They could not be stopped at all. Cancelled at each of their checkpoints in turn, until one
 * runs through.
 */
public class CancelRemoteSyncTest {

    @Test
    @Timeout(120)
    public void aCancelledPullChangesNothing(@TempDir Path tmp) throws Exception {
        Path origin = origin(tmp);
        Map<String, String> originFiles = fingerprint(origin.resolve(".ecco"));
        Path target = Files.createDirectories(tmp.resolve("target"));
        try (EccoService service = repository(target)) {
            service.addRemote("origin", origin.toString(), Remote.Type.LOCAL);
            Map<String, String> targetFiles = fingerprint(target.resolve(".ecco"));
            int checkpoints = cancelAtEach(at -> service.pull("origin", "", at),
                    () -> {
                        assertEquals(Set.of(), featureNames(service), "nothing pulled");
                        assertEquals(targetFiles, fingerprint(target.resolve(".ecco")), "nothing written");
                    });
            assertTrue(checkpoints >= 3, "asked before reading, before merging and before writing: " + checkpoints);
            assertEquals(Set.of("A", "B"), featureNames(service), "pulled when not cancelled");
        }
        try (EccoService reopened = reopen(target)) {
            assertEquals(Set.of("A", "B"), featureNames(reopened));
        }
        assertEquals(originFiles, fingerprint(origin.resolve(".ecco")));
    }

    @Test
    @Timeout(120)
    public void aCancelledFetchChangesNothing(@TempDir Path tmp) throws Exception {
        Path origin = origin(tmp);
        Path target = Files.createDirectories(tmp.resolve("target"));
        try (EccoService service = repository(target)) {
            service.addRemote("origin", origin.toString(), Remote.Type.LOCAL);
            int checkpoints = cancelAtEach(at -> service.fetch("origin", at),
                    () -> assertEquals(Set.of(), service.getRemote("origin").getFeatures().stream().map(Feature::getName).collect(Collectors.toSet())));
            assertTrue(checkpoints >= 2, "" + checkpoints);
            assertEquals(Set.of("A", "B"), service.getRemote("origin").getFeatures().stream().map(Feature::getName).collect(Collectors.toSet()));
        }
    }

    @Test
    @Timeout(120)
    public void aCancelledPushChangesNothing(@TempDir Path tmp) throws Exception {
        Path origin = origin(tmp);
        Map<String, String> originFiles = fingerprint(origin.resolve(".ecco"));
        Path source = Files.createDirectories(tmp.resolve("source"));
        try (EccoService service = repository(source)) {
            Files.writeString(source.resolve("c.txt"), "c\n");
            service.commit("c", "C");
            service.addRemote("origin", origin.toString(), Remote.Type.LOCAL);
            int checkpoints = cancelAtEach(at -> service.push("origin", "", at),
                    () -> assertEquals(originFiles, fingerprint(origin.resolve(".ecco")), "nothing pushed"));
            assertTrue(checkpoints >= 2, "" + checkpoints);
        }
        try (EccoService reopened = reopen(origin)) {
            assertEquals(Set.of("A", "B", "C"), featureNames(reopened), "pushed when not cancelled");
        }
    }

    interface Operation {
        void run(BooleanSupplier cancelled) throws Exception;
    }

    interface Check {
        void run() throws Exception;
    }

    /**
     * Runs [operation] cancelled at its 1st, 2nd, ... checkpoint - each time it must end cancelled
     * and [unchanged] hold - until it runs through. Returns how many checkpoints it has.
     */
    private static int cancelAtEach(Operation operation, Check unchanged) throws Exception {
        for (int at = 1; ; at++) {
            AtomicInteger asked = new AtomicInteger();
            int cancelAt = at;
            try {
                operation.run(() -> asked.incrementAndGet() >= cancelAt);
            } catch (RuntimeException e) {
                assertTrue(OperationCancelledException.isCause(e), "cancelled at checkpoint " + at + ": " + e);
                unchanged.run();
                continue;
            }
            assertEquals(at - 1, asked.get(), "ran through: no checkpoint left to cancel at");
            return at - 1;
        }
    }

    private static Path origin(Path tmp) throws Exception {
        Path origin = Files.createDirectories(tmp.resolve("origin"));
        try (EccoService service = repository(origin)) {
            Files.writeString(origin.resolve("a.txt"), "a\n");
            service.commit("a", "A");
            Files.writeString(origin.resolve("b.txt"), "b\n");
            service.commit("a and b", "A, B");
        }
        return origin;
    }

    private static EccoService repository(Path dir) {
        EccoService service = new EccoService();
        service.setRepositoryDir(dir.resolve(".ecco"));
        service.init();
        service.setBaseDir(dir);
        return service;
    }

    private static EccoService reopen(Path dir) {
        EccoService service = new EccoService();
        service.setRepositoryDir(dir.resolve(".ecco"));
        service.setBaseDir(dir);
        service.open();
        return service;
    }

    private static Set<String> featureNames(EccoService service) {
        return service.getRepository().getFeatures().stream().map(Feature::getName).collect(Collectors.toSet());
    }

    private static Map<String, String> fingerprint(Path dir) throws Exception {
        Map<String, String> result = new TreeMap<>();
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path p : paths.toList()) {
                String key = dir.relativize(p).toString();
                if (Files.isDirectory(p))
                    result.put(key + "/", "");
                else
                    result.put(key, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p))));
            }
        }
        return result;
    }
}
