package at.jku.isse.ecco.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The README promised that ecco finds the repository in the current directory or its nearest
 * parent, but the CLI only ever looked in the current directory, so every command run from a
 * subdirectory failed. Commands now search upwards; the repository's directory is the working
 * directory (a commit from a subdirectory still commits the whole variant). init and fork still
 * create a repository in the current directory.
 */
public class MainRepositoryDiscoveryTest {

    @Test
    @Timeout(120)
    public void aCommitFromASubdirectoryCommitsTheWholeVariant(@TempDir Path tmp) throws IOException {
        Path home = Files.createDirectories(tmp.resolve("home"));
        Path sub = Files.createDirectories(home.resolve("sub/deeper"));
        Files.writeString(home.resolve("a.txt"), "a\n");
        Files.writeString(sub.resolve("b.txt"), "b\n");

        assertEquals(0, Main.run(new String[]{"init"}, home));
        assertEquals(0, Main.run(new String[]{"commit", "-c", "A", "-m", "from a subdirectory"}, sub));
        assertEquals(0, Main.run(new String[]{"features"}, sub));

        // a fresh working directory with a copy of the repository gets both files back
        Path copy = Files.createDirectories(tmp.resolve("copy"));
        copyTree(home.resolve(".ecco"), copy.resolve(".ecco"));
        assertEquals(0, Main.run(new String[]{"checkout", "-c", "A"}, copy));
        assertEquals("a\n", Files.readString(copy.resolve("a.txt")));
        assertEquals("b\n", Files.readString(copy.resolve("sub/deeper/b.txt")));
    }

    @Test
    @Timeout(60)
    public void initInsideARepositoryCreatesANewRepositoryThere(@TempDir Path tmp) throws IOException {
        Path outer = Files.createDirectories(tmp.resolve("outer"));
        Path inner = Files.createDirectories(outer.resolve("inner"));

        assertEquals(0, Main.run(new String[]{"init"}, outer));
        assertEquals(0, Main.run(new String[]{"init"}, inner));
        assertTrue(Files.isDirectory(inner.resolve(".ecco")));

        // the nearest repository wins
        Files.writeString(inner.resolve("i.txt"), "i\n");
        assertEquals(0, Main.run(new String[]{"commit", "-c", "I"}, inner));
        String outerFeatures = output(() -> assertEquals(0, Main.run(new String[]{"features"}, outer)));
        assertFalse(outerFeatures.contains("I"), outerFeatures);
    }

    @Test
    @Timeout(60)
    public void withoutARepositoryCommandsFailClearly(@TempDir Path tmp) throws IOException {
        Path nowhere = Files.createDirectories(tmp.resolve("nowhere"));
        String errors = errors(() -> assertEquals(1, Main.run(new String[]{"features"}, nowhere)));
        assertTrue(errors.contains("Repository does not exist"), errors);
    }

    private static String output(Runnable runnable) {
        PrintStream original = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captured, true));
        try {
            runnable.run();
        } finally {
            System.setOut(original);
        }
        return captured.toString();
    }

    private static String errors(Runnable runnable) {
        PrintStream original = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setErr(new PrintStream(captured, true));
        try {
            runnable.run();
        } finally {
            System.setErr(original);
        }
        return captured.toString();
    }

    private static void copyTree(Path from, Path to) throws IOException {
        try (Stream<Path> paths = Files.walk(from)) {
            for (Path p : paths.sorted(Comparator.naturalOrder()).toList()) {
                Path target = to.resolve(from.relativize(p).toString());
                if (Files.isDirectory(p))
                    Files.createDirectories(target);
                else
                    Files.copy(p, target);
            }
        }
    }
}
