package at.jku.isse.ecco.adapter.dispatch;

import at.jku.isse.ecco.service.EccoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Known gap #53: a text file that mixes line separators (tinycc's tests/tcctest.c, on purpose) was
 * checked out with its first one throughout. Each variant keeps its separators - after reopening, so
 * they are stored; a checkout combining variants in different formats gets the newest commit's main
 * separator throughout, as before.
 */
public class MixedLineSeparatorsTest {

    @Test
    @Timeout(60)
    public void eachVariantKeepsItsMixedSeparators() throws Exception {
        Path work = Files.createTempDirectory("mixed-line-separators");
        Path repo = work.resolve(".ecco");
        Path a = Files.createDirectories(work.resolve("a"));
        Path b = Files.createDirectories(work.resolve("b"));
        String first = "one\r\ntwo\nthree\r\n";
        String second = "one\r\ntwo\nthree\r\nfour\rfive\n";
        Files.writeString(a.resolve("f.txt"), first);
        Files.writeString(b.resolve("f.txt"), second);
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repo);
            service.init();
            service.setBaseDir(a);
            service.commit("first", "A");
            service.setBaseDir(b);
            service.commit("second", "A, B");
        }

        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repo);
            service.open();
            assertEquals(first, checkout(service, work.resolve("out-a"), "A"));
            assertEquals(second, checkout(service, work.resolve("out-ab"), "A, B"));
        }
    }

    private static String checkout(EccoService service, Path dir, String configuration) throws Exception {
        Files.createDirectories(dir);
        service.setBaseDir(dir);
        service.checkout(configuration);
        return Files.readString(dir.resolve("f.txt"));
    }
}
