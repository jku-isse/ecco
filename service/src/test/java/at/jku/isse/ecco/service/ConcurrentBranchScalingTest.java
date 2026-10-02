package at.jku.isse.ecco.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Variants that each add a line no other variant has, at the same place, leave the partial order
 * graph with one branch per variant whose order is never resolved. Aligning such graphs exactly
 * tries every ordering of the branch - factorial in its width: commits took 4 ms, 20 ms, then 2.3 s
 * within 10 variants, and wider branches ran out of memory. Above 100,000 orderings the alignment now
 * falls back to directPoaAlignment, which is fast but can find fewer matches than the exact one
 * (DirectPoaAlignmentSpikeTest). These pin down that commits stay fast and that every variant still
 * checks out as committed, also when a later variant fixes the order of two of the concurrent lines
 * in either direction.
 */
public class ConcurrentBranchScalingTest {

    @Test
    @Timeout(120)
    public void manyConcurrentUniqueLinesCommitQuicklyAndCheckOutAsCommitted(@TempDir Path tmp) throws IOException {
        Map<String, List<String>> committed = new LinkedHashMap<>();
        try (EccoService service = open(tmp)) {
            Path wd = service.getBaseDir();
            for (int v = 1; v <= 25; v++) {
                List<String> lines = List.of("head", "unique-" + v, "middle", "also-" + v, "tail");
                Files.write(wd.resolve("f.txt"), lines);
                long start = System.nanoTime();
                service.commit("v" + v, "BASE, F" + v);
                long ms = (System.nanoTime() - start) / 1_000_000;
                // generous: about 50 ms here; the exact alignment alone took seconds by the 10th variant
                assertTrue(ms < 10_000, "commit " + v + " took " + ms + " ms");
                committed.put("BASE, F" + v, lines);
            }
            assertCheckouts(service, tmp, committed);
        }
    }

    @Test
    @Timeout(120)
    public void fixingTheOrderOfTwoConcurrentLinesKeepsEveryVariantIntact(@TempDir Path tmp) throws IOException {
        // in both directions, so that whichever order the fallback assumes for the concurrent lines,
        // one of them contradicts it
        for (int[] pair : new int[][]{{2, 7}, {7, 2}, {1, 10}, {10, 1}}) {
            Path dir = Files.createDirectories(tmp.resolve("pair-" + pair[0] + "-" + pair[1]));
            Map<String, List<String>> committed = new LinkedHashMap<>();
            try (EccoService service = open(dir)) {
                Path wd = service.getBaseDir();
                for (int v = 1; v <= 10; v++) {
                    List<String> lines = List.of("head", "unique-" + v, "tail");
                    Files.write(wd.resolve("f.txt"), lines);
                    service.commit("v" + v, "BASE, F" + v);
                    committed.put("BASE, F" + v, lines);
                }
                String both = "BASE, F" + pair[0] + ", F" + pair[1];
                List<String> lines = List.of("head", "unique-" + pair[0], "unique-" + pair[1], "tail");
                Files.write(wd.resolve("f.txt"), lines);
                service.commit("both", both);
                committed.put(both, lines);
                assertCheckouts(service, dir, committed);
            }
        }
    }

    private static EccoService open(Path dir) throws IOException {
        EccoService service = new EccoService();
        service.setRepositoryDir(dir.resolve(".ecco"));
        service.init();
        service.setBaseDir(Files.createDirectories(dir.resolve("wd")));
        return service;
    }

    private static void assertCheckouts(EccoService service, Path dir, Map<String, List<String>> committed) throws IOException {
        int index = 0;
        for (Map.Entry<String, List<String>> variant : committed.entrySet()) {
            Path out = Files.createDirectories(dir.resolve("checkout-" + index++));
            service.setBaseDir(out);
            service.checkout(variant.getKey());
            assertEquals(variant.getValue(), Files.readAllLines(out.resolve("f.txt")), variant.getKey());
        }
    }
}
