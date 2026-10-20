package at.jku.isse.ecco.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Artifacts compare by their data, so two distinct artifacts with equal content - the same line in
 * two files, twice in one file, or committed again later - are equal without being the same. Each
 * must still be stored and loaded as itself (NFR-R4): a set keyed by equality once kept only one of
 * them, whose file was then missing on the next load (da14de14). Every committed configuration,
 * checked out after reopening, gives back what was committed - also after a commit made in a later
 * session, when equal artifacts are loaded ones.
 */
public class EqualContentArtifactsTest {

    @Test
    @Timeout(120)
    public void distinctArtifactsWithEqualContentSurviveReopening(@TempDir Path tmp) throws Exception {
        Path work = Files.createDirectories(tmp.resolve("work"));
        Map<String, Map<String, String>> committed = new LinkedHashMap<>();

        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(work.resolve(".ecco"));
            service.init();
            service.setBaseDir(work);
            commit(service, work, committed, "A", Map.of("a.txt", "same\nx\nsame\n"));
            commit(service, work, committed, "A, B", Map.of("a.txt", "same\nx\nsame\n", "b.txt", "same\nsame\n"));
            commit(service, work, committed, "A, B, C", Map.of("a.txt", "same\nx\nsame\n", "b.txt", "same\nsame\n", "c.txt", "x\nsame\n"));
        }
        assertCheckouts(work, committed, tmp.resolve("first"));

        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(work.resolve(".ecco"));
            service.open();
            service.setBaseDir(work);
            commit(service, work, committed, "A, B, C, D", Map.of("a.txt", "same\nx\nsame\n", "b.txt", "same\nsame\n", "c.txt", "x\nsame\n", "d.txt", "same\n"));
        }
        assertCheckouts(work, committed, tmp.resolve("second"));
    }

    private static void commit(EccoService service, Path work, Map<String, Map<String, String>> committed,
                               String configuration, Map<String, String> files) throws Exception {
        for (Map.Entry<String, String> file : files.entrySet())
            Files.writeString(work.resolve(file.getKey()), file.getValue());
        service.commit(configuration, configuration);
        committed.put(configuration, new TreeMap<>(files));
    }

    private static void assertCheckouts(Path work, Map<String, Map<String, String>> committed, Path outs) throws Exception {
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(work.resolve(".ecco"));
            service.open();
            int i = 0;
            for (Map.Entry<String, Map<String, String>> entry : committed.entrySet()) {
                Path out = Files.createDirectories(outs.resolve("checkout" + i++));
                service.setBaseDir(out);
                service.checkout(entry.getKey());
                Map<String, String> files = new TreeMap<>();
                try (var paths = Files.list(out)) {
                    for (Path p : paths.toList())
                        if (!p.getFileName().toString().startsWith("."))
                            files.put(p.getFileName().toString(), Files.readString(p));
                }
                assertEquals(entry.getValue(), files, entry.getKey());
            }
        }
    }
}
