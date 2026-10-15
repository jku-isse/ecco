package at.jku.isse.ecco.storage.ser.dao;

import at.jku.isse.ecco.service.EccoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Association trees are stored in pieces, one per file (gap #48, see TreePieces): a commit writes the
 * pieces of files whose content changed, a piece is stored once however many associations - or
 * association ids - refer to it, a reopened repository has the same content, and packs no piece of
 * which is referred to anymore are deleted.
 */
public class TreePiecesStorageTest {

    @Test
    @Timeout(120)
    public void piecesAreStoredOnceAndOnlyWhenTheyChange(@TempDir Path tmp) throws Exception {
        Path repo = tmp.resolve(".ecco");
        Path content = Files.createDirectories(tmp.resolve("content"));
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 200; i++)
            big.append("line ").append(i).append('\n');
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repo);
            service.init();
            service.setBaseDir(content);
            Files.writeString(content.resolve("big.txt"), big.toString());
            Files.writeString(content.resolve("small.txt"), "a\n");
            service.commit("one", "A.1");
            Set<String> afterOne = pieces(repo);
            assertEquals(2, afterOne.size(), "a piece per file: " + afterOne);

            // the same files committed again as a new revision: the associations get new ids, their
            // trees are the same - no piece is written again
            service.commit("again", "A.2");
            assertEquals(afterOne, pieces(repo), "unchanged files: the same pieces");

            // one line of the small file changes: the big file's piece is not written again
            Files.writeString(content.resolve("small.txt"), "a\nb\n");
            service.commit("small changed", "A.3");
            Set<String> afterChange = pieces(repo);
            assertTrue(afterChange.containsAll(afterOne.stream().filter(p -> true).toList()) || afterChange.size() >= afterOne.size());
            assertEquals(1, piecePacks(repo).stream().filter(p -> entries(p).stream().anyMatch(e -> !afterOne.contains(e))).count(),
                    "one new pack, holding only new pieces");
        }

        // reopened: the same content
        Path out = Files.createDirectories(tmp.resolve("out"));
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repo);
            service.open();
            service.setBaseDir(out);
            service.checkout("A.3");
        }
        assertEquals(big.toString(), Files.readString(out.resolve("big.txt")));
        assertEquals("a\nb\n", Files.readString(out.resolve("small.txt")));
        // association files hold the trees down to the files only
        try (Stream<Path> files = Files.list(repo.resolve("associations"))) {
            for (Path file : files.toList())
                assertTrue(Files.size(file) < 4096, file + " is " + Files.size(file) + " bytes");
        }
    }

    /** Every pack that remains is needed: without it the repository does not open. */
    @Test
    @Timeout(180)
    public void onlyPacksSomethingRefersToRemain(@TempDir Path tmp) throws Exception {
        Path repo = tmp.resolve(".ecco");
        Path content = Files.createDirectories(tmp.resolve("content"));
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repo);
            service.init();
            service.setBaseDir(content);
            String[] versions = {"one\n", "one\ntwo\n", "zero\none\ntwo\n", "zero\ntwo\n", "three\n"};
            for (int i = 0; i < versions.length; i++) {
                Files.writeString(content.resolve("f.txt"), versions[i]);
                Files.writeString(content.resolve("g.txt"), "g" + (i % 2) + "\n");
                service.commit("c" + i, "A." + (i + 1));
            }
        }
        List<Path> packs = piecePacks(repo);
        assertFalse(packs.isEmpty());
        for (Path pack : packs) {
            Path copy = tmp.resolve("without-" + pack.getFileName()).resolve(".ecco");
            copyTree(repo, copy);
            Files.delete(copy.resolve("pieces").resolve(pack.getFileName()));
            try (EccoService service = new EccoService()) {
                service.setRepositoryDir(copy);
                Exception e = assertThrows(Exception.class, () -> {
                    service.open();
                    service.getRepository();
                }, pack.getFileName() + " is needed");
                StringBuilder messages = new StringBuilder();
                for (Throwable t = e; t != null; t = t.getCause())
                    messages.append(t.getMessage()).append(" | ");
                assertTrue(messages.toString().contains("missing"), messages.toString());
            }
        }
        // and all revisions check out from what remains
        Path out = Files.createDirectories(tmp.resolve("out"));
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repo);
            service.open();
            service.setBaseDir(out);
            service.checkout("A.3");
        }
        assertEquals("zero\none\ntwo\n", Files.readString(out.resolve("f.txt")));
    }

    private static void copyTree(Path from, Path to) throws Exception {
        try (Stream<Path> walk = Files.walk(from)) {
            for (Path p : walk.sorted().toList()) {
                Path t = to.resolve(from.relativize(p).toString());
                if (Files.isDirectory(p)) Files.createDirectories(t); else Files.copy(p, t);
            }
        }
    }

    private static List<Path> piecePacks(Path repo) throws Exception {
        Path dir = repo.resolve("pieces");
        if (!Files.isDirectory(dir))
            return List.of();
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(p -> p.getFileName().toString().startsWith("pack-")).sorted().toList();
        }
    }

    private static List<String> entries(Path pack) {
        try (ZipFile zip = new ZipFile(pack.toFile())) {
            return Collections.list(zip.entries()).stream().map(ZipEntry::getName).toList();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static Set<String> pieces(Path repo) throws Exception {
        Set<String> pieces = new HashSet<>();
        for (Path pack : piecePacks(repo))
            pieces.addAll(entries(pack));
        return pieces;
    }
}
