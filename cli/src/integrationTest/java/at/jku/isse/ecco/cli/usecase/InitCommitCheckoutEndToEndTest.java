package at.jku.isse.ecco.cli.usecase;

import at.jku.isse.ecco.cli.usecase.EccoProcess.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UC-1 Initialize repository, UC-3 Commit variant and UC-5 Check out variant (doc/requirements.md),
 * through the command line as a variant developer uses it: every command is an {@code ecco} process
 * of its own in the working directory, so every step opens the repository from disk (UC-2). The
 * steps in the comments are those of the use cases' flows.
 * <p>
 * The oracle is the files: a committed variant checks out byte for byte (NFR-R3), and a feature
 * combination never committed as such checks out as the merge of the variants. A refused command
 * leaves the repository as it was, byte for byte.
 */
public class InitCommitCheckoutEndToEndTest {

    /** What a checkout writes besides the variant (UC-5 B11) and a commit does not read. */
    private static final Set<String> CHECKOUT_FILES = Set.of(".config", ".warnings", ".hashes");

    private static final Map<String, byte[]> BASE = files(
            "README.md", "# Shop\n\nA small shop.\n",
            "src/core.txt", "start\nload\nsave\nstop\n",
            "notes.txt", "no final newline",
            "data/logo.bin", binary());

    private static final Map<String, byte[]> BASE_SEARCH = with(BASE,
            "src/core.txt", "start\nload\nsearch\nsave\nstop\n",
            "src/search.txt", "query\nrank\n");

    private static final Map<String, byte[]> BASE_EXPORT = with(BASE,
            "src/core.txt", "start\nload\nsave\nexport\nstop\n",
            "docs/export.md", "# Export\n\nWrites CSV.\n");

    /** Never committed: the search line from one variant, the export line from the other. */
    private static final Map<String, byte[]> BASE_SEARCH_EXPORT = with(BASE,
            "src/core.txt", "start\nload\nsearch\nsave\nexport\nstop\n",
            "src/search.txt", "query\nrank\n",
            "docs/export.md", "# Export\n\nWrites CSV.\n");

    @Test
    @Timeout(600)
    public void aVariantDeveloperInitializesCommitsAndChecksOut(@TempDir Path tmp) throws Exception {
        Path work = Files.createDirectories(tmp.resolve("shop"));

        // UC-1 B1-B5: an empty repository in <directory>/.ecco
        ok(work, "init");
        assertTrue(Files.isDirectory(work.resolve(".ecco")));
        assertEquals("", ok(work, "features").out().strip(), "a new repository has no features");

        // UC-1 SA1: a second init is refused and leaves the repository unchanged
        refused(work, "Repository already exists at this location.", "init");

        // UC-3 B1-B12, three times: the files of the working directory as a variant
        commit(work, BASE, "BASE", "first variant");
        commit(work, BASE_SEARCH, "BASE, search", "search");
        commit(work, BASE_EXPORT, "BASE, export", "export");

        String features = ok(work, "features").out();
        for (String feature : new String[]{"BASE", "search", "export"})
            assertTrue(features.contains(feature), features);

        // UC-3 SA1: a configuration that is not well-formed or names no feature is refused, and
        // the repository is unchanged
        writeVariant(work, BASE);
        refused(work, "", "commit", "-c", "BASE, bad name", "-m", "invalid");
        refused(work, "A commit needs at least one feature", "commit", "-c", "", "-m", "empty");

        // UC-5 B1-B12: every committed variant checks out as it was committed (NFR-R3)...
        checkout(work, "BASE", BASE, "");
        checkout(work, "BASE, search", BASE_SEARCH, "");
        checkout(work, "BASE, export", BASE_EXPORT, "");
        // ...and a combination never committed as such checks out composed from them, with the
        // interaction of the two features reported as possibly missing - it was never committed
        checkout(work, "BASE, search, export", BASE_SEARCH_EXPORT, "1 MISSING");
        assertTrue(Files.readString(work.resolve(".warnings")).contains("MISSING: export + search"));

        // UC-5 SA2: a working directory that is not empty is refused and left as it was
        Map<String, byte[]> before = workingFiles(work);
        refused(work, "", "checkout", "-c", "BASE");
        assertSameFiles(before, workingFiles(work));

        // UC-5 SA1: a configuration error is refused, nothing is written
        clear(work);
        refused(work, "", "checkout", "-c", "BASE, bad name");
        assertSameFiles(Map.of(), workingFiles(work));
    }

    private static void commit(Path work, Map<String, byte[]> variant, String configuration, String message) throws Exception {
        writeVariant(work, variant);
        Result result = ok(work, "commit", "-c", configuration, "-m", message);
        // IF-C9: the commit's id and the configuration it was recorded with
        assertTrue(result.out().startsWith("Committed "), result.toString());
        assertFalse(result.out().contains("CONSTRAINT:"), result.toString());
    }

    /** {@code warnings}: the counts per kind the CLI prints (UC-5 SA4), "" for none. */
    private static void checkout(Path work, String configuration, Map<String, byte[]> expected, String warnings) throws Exception {
        clear(work);
        Result result = ok(work, "checkout", "-c", configuration);
        assertTrue(result.out().startsWith("Checked out "), result.toString());
        if (warnings.isEmpty())
            assertFalse(result.out().contains("Warnings:"), result.toString());
        else
            assertTrue(result.out().contains("Warnings: " + warnings + " -- details in .warnings"), result.toString());
        assertSameFiles(expected, workingFiles(work));
        // UC-5 B11
        assertTrue(Files.exists(work.resolve(".config")), configuration);
        assertTrue(Files.exists(work.resolve(".warnings")), configuration);
    }

    private static Result ok(Path dir, String... args) throws Exception {
        Result result = EccoProcess.run(dir, args);
        assertEquals(0, result.exitCode(), () -> "ecco " + String.join(" ", args) + "\n" + result);
        return result;
    }

    /** Exit 1 with the message on stderr (IF-C3b), and the repository left byte for byte. */
    private static void refused(Path dir, String message, String... args) throws Exception {
        Map<String, byte[]> repository = tree(dir.resolve(".ecco"));
        Result result = EccoProcess.run(dir, args);
        assertEquals(1, result.exitCode(), () -> "ecco " + String.join(" ", args) + "\n" + result);
        assertTrue(result.err().startsWith("ERROR: ") && result.err().contains(message), result.toString());
        assertSameFiles(repository, tree(dir.resolve(".ecco")));
    }

    private static void writeVariant(Path work, Map<String, byte[]> variant) throws IOException {
        clear(work);
        for (Map.Entry<String, byte[]> file : variant.entrySet()) {
            Path path = work.resolve(file.getKey());
            Files.createDirectories(path.getParent());
            Files.write(path, file.getValue());
        }
    }

    /** Everything but the repository, as a variant developer empties a working directory. */
    private static void clear(Path work) throws IOException {
        try (Stream<Path> top = Files.list(work)) {
            for (Path path : top.filter(p -> !p.getFileName().toString().equals(".ecco")).toList())
                deleteRecursively(path);
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        try (Stream<Path> walk = Files.walk(path)) {
            for (Path p : walk.sorted(java.util.Comparator.reverseOrder()).toList())
                Files.delete(p);
        }
    }

    /** The variant in the working directory: without the repository and what a checkout adds. */
    private static Map<String, byte[]> workingFiles(Path work) throws IOException {
        Map<String, byte[]> files = tree(work);
        files.keySet().removeIf(path -> path.startsWith(".ecco/") || CHECKOUT_FILES.contains(path));
        return files;
    }

    private static Map<String, byte[]> tree(Path root) throws IOException {
        Map<String, byte[]> files = new TreeMap<>();
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path path : walk.filter(Files::isRegularFile).toList())
                files.put(root.relativize(path).toString().replace('\\', '/'), Files.readAllBytes(path));
        }
        return files;
    }

    private static void assertSameFiles(Map<String, byte[]> expected, Map<String, byte[]> actual) {
        assertEquals(expected.keySet(), actual.keySet());
        for (String path : expected.keySet())
            assertArrayEquals(expected.get(path), actual.get(path), () -> path + " differs:\n"
                    + new String(actual.get(path), StandardCharsets.UTF_8));
    }

    private static Map<String, byte[]> files(Object... pathsAndContents) {
        return with(Map.of(), pathsAndContents);
    }

    private static Map<String, byte[]> with(Map<String, byte[]> files, Object... pathsAndContents) {
        Map<String, byte[]> result = new TreeMap<>(files);
        for (int i = 0; i < pathsAndContents.length; i += 2) {
            Object content = pathsAndContents[i + 1];
            result.put((String) pathsAndContents[i], content instanceof String text ? text.getBytes(StandardCharsets.UTF_8) : (byte[]) content);
        }
        return result;
    }

    private static byte[] binary() {
        byte[] bytes = new byte[256];
        for (int i = 0; i < bytes.length; i++)
            bytes[i] = (byte) i;
        return bytes;
    }
}
