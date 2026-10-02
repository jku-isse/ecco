package at.jku.isse.ecco.cli.command.minimize;

import at.jku.isse.ecco.cli.MainTestAccess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** {@code minimize} stores checkout conditions; {@code checkout --minimized} writes the same files with them. */
public class MinimizeCommandTest {

    @Test
    @Timeout(120)
    public void aMinimizedCheckoutWritesTheSameFiles(@TempDir Path tmp) throws IOException {
        Path home = Files.createDirectories(tmp.resolve("home"));
        assertEquals(0, MainTestAccess.run(new String[]{"init"}, home));
        for (String[] variant : new String[][]{{"A", "a"}, {"A, B", "a", "b"}, {"B", "b"}, {"A, B, C", "a", "b", "c"}}) {
            try (Stream<Path> files = Files.list(home)) {
                for (Path p : files.filter(p -> !p.getFileName().toString().equals(".ecco")).toList()) Files.delete(p);
            }
            for (int i = 1; i < variant.length; i++)
                Files.write(home.resolve(variant[i] + ".txt"), List.of(variant[i]));
            assertEquals(0, MainTestAccess.run(new String[]{"commit", "-c", variant[0]}, home));
        }
        assertEquals(0, MainTestAccess.run(new String[]{"minimize"}, home));

        for (String configuration : List.of("A", "B", "A, B", "A, C", "A, B, C")) {
            Path normal = copyRepository(home, tmp.resolve("normal " + configuration));
            Path minimized = copyRepository(home, tmp.resolve("minimized " + configuration));
            assertEquals(0, MainTestAccess.run(new String[]{"checkout", "-c", configuration}, normal));
            assertEquals(0, MainTestAccess.run(new String[]{"checkout", "--minimized", "-c", configuration}, minimized));
            for (String file : List.of("a.txt", "b.txt", "c.txt", ".warnings"))
                assertEquals(read(normal.resolve(file)), read(minimized.resolve(file)), configuration + ": " + file);
        }
    }

    private static String read(Path file) throws IOException {
        return Files.exists(file) ? Files.readString(file) : "<absent>";
    }

    private static Path copyRepository(Path home, Path target) throws IOException {
        Path from = home.resolve(".ecco");
        try (Stream<Path> paths = Files.walk(from)) {
            for (Path p : paths.sorted(Comparator.naturalOrder()).toList()) {
                Path to = target.resolve(".ecco").resolve(from.relativize(p).toString());
                if (Files.isDirectory(p)) Files.createDirectories(to);
                else Files.copy(p, to);
            }
        }
        return target;
    }
}
