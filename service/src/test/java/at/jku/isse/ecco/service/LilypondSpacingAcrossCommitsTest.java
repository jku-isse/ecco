package at.jku.isse.ecco.service;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A checkout of a committed variant is written with the spacing it was read with, also when its
 * tokens were read in different commits: whether two tokens are written together must not come
 * from where each was read the first time (a token matched in a later commit keeps its old
 * position), which ran {@code \p e} together as {@code \pe} and split {@code e2 f4} as {@code e2f 4}.
 */
public class LilypondSpacingAcrossCommitsTest {

    /** written: as committed - the whitespace a token was read with after the same token */
    @ParameterizedTest(name = "{0}: {1} then {2}")
    @CsvSource(delimiter = '|', value = {
            "true  | { c4 d e f }     | { c4 \\p e f }       | { c4 \\p e f }",
            "false | { c4 d e f }     | { c4 d e2 f4 }       | { c4 d e2 f4 }",
            "false | { c4 d4 e4 f4 }  | { c4 d4 g4 e4 f4 }   | { c4 d4 g4 e4 f4 }",
            "false | { c4 d e f }     | { c4 \\p e f }       | { c4 \\p e f }",
            "true  | { c4 d4 e4 f4 }  | { c4 d4 g4 e4 f4 }   | { c4 d4 g4 e4 f4 }",
    })
    public void theSecondVariantIsCheckedOutAsCommitted(boolean musical, String v1, String v2, String written, @TempDir Path tmp) throws Exception {
        if (musical)
            assumeTrue(Lymusic.available(), Lymusic.NEEDS);
        String previous = System.getProperty("ecco.lilypond.musicalTokens");
        System.setProperty("ecco.lilypond.musicalTokens", String.valueOf(musical));
        try {
            String afterV1 = checkout(tmp.resolve("both"), v2, v1);
            String alone = checkout(tmp.resolve("alone"), v2, null);
            // nothing run together or split (\p e as \pe, e2 f4 as e2f 4), as when committed alone
            assertEquals(written, afterV1);
            assertEquals(alone, afterV1);
        } finally {
            if (previous == null) System.clearProperty("ecco.lilypond.musicalTokens");
            else System.setProperty("ecco.lilypond.musicalTokens", previous);
        }
    }

    /** v2 checked out of a repository it was committed to, after v1 unless null */
    private static String checkout(Path dir, String v2, String v1) throws Exception {
        Files.createDirectories(dir);
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(dir.resolve(".ecco"));
            service.init();
            if (v1 != null)
                commit(service, dir.resolve("v1"), v1, "A");
            commit(service, dir.resolve("v2"), v2, "A, B");
            Path out = Files.createDirectories(dir.resolve("out"));
            service.setBaseDir(out);
            service.checkout("A, B");
            return Files.readString(out.resolve("m.ly")).replaceAll("\\s+", " ").trim();
        }
    }

    private static void commit(EccoService service, Path dir, String music, String configuration) throws Exception {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("m.ly"), music + "\n");
        service.setBaseDir(dir);
        service.commit("", configuration);
    }
}
