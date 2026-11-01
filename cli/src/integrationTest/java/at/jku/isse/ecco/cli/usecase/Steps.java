package at.jku.isse.ecco.cli.usecase;

import at.jku.isse.ecco.cli.usecase.EccoProcess.Result;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * What a user does on the command line in the end-to-end tests, each command an {@code ecco}
 * process ({@link EccoProcess}), with the checks every use case shares.
 */
final class Steps {

    /** What a checkout writes besides the variant (UC-5 B11) and a commit does not read. */
    static final Set<String> CHECKOUT_FILES = Set.of(".config", ".warnings", ".hashes");

    private Steps() {
    }

    static Result ok(Path dir, String... args) throws Exception {
        Result result = EccoProcess.run(dir, args);
        assertEquals(0, result.exitCode(), () -> "ecco " + String.join(" ", args) + " in " + dir.getFileName() + "\n" + result);
        return result;
    }

    /** Exit 1 with the message on stderr (IF-C3b), and the repository left byte for byte. */
    static void refused(Path dir, String message, String... args) throws Exception {
        Map<String, byte[]> repository = tree(dir.resolve(".ecco"));
        Result result = EccoProcess.run(dir, args);
        assertEquals(1, result.exitCode(), () -> "ecco " + String.join(" ", args) + " in " + dir.getFileName() + "\n" + result);
        assertTrue(result.err().startsWith("ERROR: ") && result.err().contains(message), result.toString());
        assertSameFiles(repository, tree(dir.resolve(".ecco")));
    }

    /** Runs the command and checks that it left the repository in {@code dir} byte for byte. */
    static Result leavesRepository(Path dir, Path commandDir, String... args) throws Exception {
        Map<String, byte[]> repository = tree(dir.resolve(".ecco"));
        Result result = ok(commandDir, args);
        assertSameFiles(repository, tree(dir.resolve(".ecco")));
        return result;
    }

    /** UC-3: the variant into the emptied working directory, committed; returns the committed configuration (IF-C9). */
    static String commit(Path work, Map<String, byte[]> variant, String configuration, String message) throws Exception {
        writeVariant(work, variant);
        Result result = ok(work, "commit", "-c", configuration, "-m", message);
        String out = result.out().strip();
        assertTrue(out.startsWith("Committed ") && out.contains(" as "), result.toString());
        assertFalse(out.contains("CONSTRAINT:"), result.toString());
        return out.substring(out.indexOf(" as ") + 4);
    }

    /** The id of the feature's revision in a configuration as the CLI prints it ({@code search.d678240}). */
    static String revision(String configuration, String feature) {
        for (String part : configuration.split(",\\s*"))
            if (part.startsWith(feature + "."))
                return part;
        throw new AssertionError(feature + " is not in " + configuration);
    }

    /**
     * UC-5: the configuration checked out into the emptied working directory; returns the warnings
     * per kind the CLI prints (UC-5 SA4), "" for none.
     */
    static String checkout(Path work, String configuration, Map<String, byte[]> expected) throws Exception {
        clear(work);
        Result result = ok(work, "checkout", "-c", configuration);
        assertTrue(result.out().startsWith("Checked out "), result.toString());
        if (expected != null)
            assertSameFiles(expected, workingFiles(work));
        // UC-5 B11
        assertTrue(Files.exists(work.resolve(".config")), configuration);
        assertTrue(Files.exists(work.resolve(".warnings")), configuration);
        String out = result.out();
        int warnings = out.indexOf("Warnings: ");
        return warnings < 0 ? "" : out.substring(warnings + "Warnings: ".length(), out.indexOf(" -- details in .warnings"));
    }

    static void writeVariant(Path work, Map<String, byte[]> variant) throws IOException {
        clear(work);
        for (Map.Entry<String, byte[]> file : variant.entrySet()) {
            Path path = work.resolve(file.getKey());
            Files.createDirectories(path.getParent());
            Files.write(path, file.getValue());
        }
    }

    /** Everything but the repository, as a variant developer empties a working directory. */
    static void clear(Path work) throws IOException {
        try (Stream<Path> top = Files.list(work)) {
            for (Path path : top.filter(p -> !p.getFileName().toString().equals(".ecco")).toList())
                deleteRecursively(path);
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        try (Stream<Path> walk = Files.walk(path)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList())
                Files.delete(p);
        }
    }

    /** The variant in the working directory: without the repository and what a checkout adds. */
    static Map<String, byte[]> workingFiles(Path work) throws IOException {
        Map<String, byte[]> files = tree(work);
        files.keySet().removeIf(path -> path.startsWith(".ecco/") || CHECKOUT_FILES.contains(path));
        return files;
    }

    static Map<String, byte[]> tree(Path root) throws IOException {
        Map<String, byte[]> files = new TreeMap<>();
        if (!Files.exists(root))
            return files;
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path path : walk.filter(Files::isRegularFile).toList())
                files.put(root.relativize(path).toString().replace('\\', '/'), Files.readAllBytes(path));
        }
        return files;
    }

    static void assertSameFiles(Map<String, byte[]> expected, Map<String, byte[]> actual) {
        assertEquals(expected.keySet(), actual.keySet());
        for (String path : expected.keySet())
            assertArrayEquals(expected.get(path), actual.get(path), () -> path + " differs:\n"
                    + new String(actual.get(path), StandardCharsets.UTF_8));
    }
}
