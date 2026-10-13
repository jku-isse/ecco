package at.jku.isse.ecco.service;

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

/**
 * A file that changed and was then removed - a directory renamed in a Git history - was checked out
 * in the variants without it, as a file without content: "Expected exactly one child node
 * (FileArtifactData) for plugin node". Its node, unique in one association, kept in the others the
 * condition it had while it was unique there (BASE, after the first commit), and the main tree took
 * that as a condition of the file.
 */
@Timeout(120)
public class RemovedFileCheckoutTest {

    @Test
    public void aFileThatChangedAndWasRemovedIsNotCheckedOutWhereItIsGone(@TempDir Path tmp) throws Exception {
        List<Map<String, String>> versions = List.of(
                Map.of("prod/Makefile", "one\n", "readme.txt", "r\n"),
                Map.of("prod/Makefile", "two\n", "readme.txt", "r\n"),
                Map.of("prod/Makefile", "two\n", "readme.txt", "r\n", "notes.txt", "n\n"),
                Map.of("f1/Makefile", "two\n", "readme.txt", "r\n", "notes.txt", "n\n"),
                Map.of("f1/Makefile", "two\n", "readme.txt", "r\n", "notes.txt", "n2\n"));
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(tmp.resolve(".ecco"));
            service.init();
            for (int i = 0; i < versions.size(); i++) {
                Path dir = Files.createDirectories(tmp.resolve("v" + i));
                for (Map.Entry<String, String> file : versions.get(i).entrySet()) {
                    Files.createDirectories(dir.resolve(file.getKey()).getParent());
                    Files.writeString(dir.resolve(file.getKey()), file.getValue());
                }
                service.setBaseDir(dir);
                service.commit("v" + i, "BASE, C" + i);
            }
            for (int i = 0; i < versions.size(); i++) {
                Path out = Files.createDirectories(tmp.resolve("out" + i));
                service.setBaseDir(out);
                service.checkout("BASE, C" + i);
                assertEquals(new TreeMap<>(versions.get(i)), files(out), "v" + i);
            }
        }
    }

    private static Map<String, String> files(Path dir) throws Exception {
        Map<String, String> files = new TreeMap<>();
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path file : walk.filter(Files::isRegularFile).toList()) {
                String name = dir.relativize(file).toString();
                if (!name.startsWith("."))
                    files.put(name, Files.readString(file));
            }
        }
        return files;
    }
}
