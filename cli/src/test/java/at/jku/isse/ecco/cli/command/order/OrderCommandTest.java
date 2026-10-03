package at.jku.isse.ecco.cli.command.order;

import at.jku.isse.ecco.cli.MainTestAccess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The command line could only resolve an ORDER warning by committing the reordered checkout, which
 * records the whole checkout as a variant of its configuration (Known gap #23). {@code order} records
 * the order of the edited files without a commit.
 */
public class OrderCommandTest {

    @Test
    @Timeout(120)
    public void orderRecordsTheOrderOfAnEditedFile(@TempDir Path tmp) throws IOException {
        Path home = Files.createDirectories(tmp.resolve("home"));
        assertEquals(0, run(home, "init"));
        Files.writeString(home.resolve("f.txt"), "a\nb\n");
        assertEquals(0, run(home, "commit", "-c", "BASE, A"));
        Files.writeString(home.resolve("f.txt"), "a\nc\n");
        assertEquals(0, run(home, "commit", "-c", "BASE, B"));
        Files.delete(home.resolve("f.txt"));

        assertEquals(0, run(home, "checkout", "-c", "BASE, A, B"));
        assertTrue(Files.readString(home.resolve(".warnings")).contains("ORDER:"));
        Files.writeString(home.resolve("f.txt"), "a\nb\nc\n");

        String out = output(() -> assertEquals(0, run(home, "order", "f.txt")));
        assertEquals("Recorded the order of f.txt (1 new precedence).", out.strip());

        // again, from a subdirectory, with the path relative to it
        Path sub = Files.createDirectories(home.resolve("sub"));
        out = output(() -> assertEquals(0, run(sub, "order", "../f.txt")));
        assertEquals("The order of ../f.txt was already recorded.", out.strip());

        assertEquals(1, run(home, "order", "../outside.txt"));
        assertEquals(1, run(home, "order", "nothing.txt"));

        // a fresh checkout of the repository has the order, and no new commit was made
        Path copy = Files.createDirectories(tmp.resolve("copy"));
        copyTree(home.resolve(".ecco"), copy.resolve(".ecco"));
        assertEquals(0, run(copy, "checkout", "-c", "BASE, A, B"));
        assertEquals("a\nb\nc\n", Files.readString(copy.resolve("f.txt")));
        assertFalse(Files.readString(copy.resolve(".warnings")).contains("ORDER:"));
        assertFalse(Files.readString(copy.resolve(".warnings")).isEmpty(), "A + B is still reported missing");
    }

    private static int run(Path dir, String... args) {
        return MainTestAccess.run(args, dir);
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

    private static void copyTree(Path from, Path to) throws IOException {
        try (Stream<Path> paths = Files.walk(from)) {
            for (Path p : paths.toList()) {
                Path target = to.resolve(from.relativize(p).toString());
                if (Files.isDirectory(p)) Files.createDirectories(target);
                else Files.copy(p, target);
            }
        }
    }
}
