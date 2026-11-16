package at.jku.isse.ecco.cli.usecase;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static at.jku.isse.ecco.cli.usecase.Steps.clear;
import static at.jku.isse.ecco.cli.usecase.Steps.writeVariant;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Commits and checkouts as shipped, each command forcing a commit's files to the disk before the id
 * names them (StorageFiles#FSYNC, NFR-R1a, gap #97): the tests turn that off for speed, so this one
 * turns it on again for its ecco processes - init, two commits, two checkouts.
 */
public class ForcedWritesEndToEndTest {

    private static final List<String> FORCED = List.of("-Decco.fsync=true");

    private static void ok(Path work, String... args) throws Exception {
        EccoProcess.Result result = EccoProcess.run(FORCED, work, args);
        assertEquals(0, result.exitCode(), () -> "ecco " + String.join(" ", args) + "\n" + result);
    }

    @Test
    @Timeout(300)
    public void commitsAndCheckoutsWithForcedWrites(@TempDir Path tmp) throws Exception {
        Path work = Files.createDirectories(tmp.resolve("work"));
        Map<String, byte[]> a = Map.of("a.txt", "one\ntwo\n".getBytes(StandardCharsets.UTF_8));
        Map<String, byte[]> b = Map.of("a.txt", "one\nthree\n".getBytes(StandardCharsets.UTF_8), "b.txt", "b\n".getBytes(StandardCharsets.UTF_8));

        ok(work, "init");
        writeVariant(work, a);
        ok(work, "commit", "-c", "A", "-m", "a");
        writeVariant(work, b);
        ok(work, "commit", "-c", "B", "-m", "b");

        for (Map.Entry<String, Map<String, byte[]>> variant : Map.of("A", a, "B", b).entrySet()) {
            clear(work);
            ok(work, "checkout", "-c", variant.getKey());
            for (Map.Entry<String, byte[]> file : variant.getValue().entrySet())
                assertArrayEquals(file.getValue(), Files.readAllBytes(work.resolve(file.getKey())), variant.getKey() + ": " + file.getKey());
        }
    }
}
