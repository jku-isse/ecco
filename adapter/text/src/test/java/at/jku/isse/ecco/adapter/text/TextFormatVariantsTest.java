package at.jku.isse.ecco.adapter.text;

import at.jku.isse.ecco.service.EccoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * Variants of a text file in different charsets or with different line separators each come back
 * as committed: both were recorded on the file's plugin artifact, which its variants share, so
 * every variant was written in the newest commit's - an LF variant with CRLF, a UTF-8 variant in
 * ISO-8859-1. They are a child of the file's node now (TextFormatArtifactData), traced like a line.
 */
public class TextFormatVariantsTest {

    private static final Path FILE = Path.of("notes.txt");

    /** Commits {@code variants} in order, the i-th as feature F{i}, and checks out {@code configuration}. */
    private static byte[] checkout(Path work, String configuration, byte[]... variants) throws Exception {
        Path content = Files.createDirectories(work.resolve("content"));
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(work.resolve(".ecco"));
            service.init();
            service.setBaseDir(content);
            for (int i = 0; i < variants.length; i++) {
                Files.write(content.resolve(FILE), variants[i]);
                service.commit("v" + i, "F" + i);
            }
            Path checkout = Files.createDirectories(work.resolve("checkout-" + configuration.replaceAll("\\W", "")));
            service.setBaseDir(checkout);
            service.checkout(configuration);
            return Files.readAllBytes(checkout.resolve(FILE));
        }
    }

    @Test
    @Timeout(120)
    public void lineSeparators(@TempDir Path work) throws Exception {
        byte[] lf = "a\nb\nc\n".getBytes(StandardCharsets.UTF_8);
        byte[] crlf = "a\r\nb\r\nc\r\n".getBytes(StandardCharsets.UTF_8);
        byte[] cr = "a\rb\rc\r".getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(lf, checkout(work.resolve("1"), "F0", lf, crlf, cr));
        assertArrayEquals(crlf, checkout(work.resolve("2"), "F1", lf, crlf, cr));
        assertArrayEquals(cr, checkout(work.resolve("3"), "F2", lf, crlf, cr));
    }

    @Test
    @Timeout(120)
    public void charsets(@TempDir Path work) throws Exception {
        byte[] utf8 = "café\nnaïve\n".getBytes(StandardCharsets.UTF_8);
        byte[] latin1 = "café\nnaïve\n".getBytes(StandardCharsets.ISO_8859_1);
        assertArrayEquals(utf8, checkout(work.resolve("1"), "F0", utf8, latin1));
        assertArrayEquals(latin1, checkout(work.resolve("2"), "F1", utf8, latin1));
    }

    /** Lines, charset and separator differ together: each variant is its own throughout. */
    @Test
    @Timeout(120)
    public void linesAndFormatTogether(@TempDir Path work) throws Exception {
        byte[] a = "shared\nonly a\n".getBytes(StandardCharsets.UTF_8);
        byte[] b = "shared\r\nonly b é\r\n".getBytes(StandardCharsets.ISO_8859_1);
        assertArrayEquals(a, checkout(work.resolve("1"), "F0", a, b));
        assertArrayEquals(b, checkout(work.resolve("2"), "F1", a, b));
    }

    /**
     * A checkout combining variants in different formats has both formats: it gets the newest
     * commit's, as every checkout did before (the lines are those of both variants).
     */
    @Test
    @Timeout(120)
    public void aCombinationOfFormatsGetsTheNewest(@TempDir Path work) throws Exception {
        byte[] a = "shared\nonly a\n".getBytes(StandardCharsets.UTF_8);
        byte[] b = "shared\r\nonly b\r\n".getBytes(StandardCharsets.UTF_8);
        String combined = new String(checkout(work, "F0, F1", a, b), StandardCharsets.UTF_8);
        org.junit.jupiter.api.Assertions.assertTrue(combined.contains("only a\r\n") && combined.contains("only b"), combined);
        org.junit.jupiter.api.Assertions.assertFalse(combined.replace("\r\n", "").contains("\n"), "CRLF throughout: " + combined);
    }
}
