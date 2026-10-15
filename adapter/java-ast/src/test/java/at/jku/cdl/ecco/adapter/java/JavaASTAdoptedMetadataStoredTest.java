package at.jku.cdl.ecco.adapter.java;

import at.jku.isse.ecco.service.EccoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A commit stores again only the artifacts that may have changed (gap #49): a stored statement that
 * takes over a newer commit's comment - metadata outside its identity - has changed, and must be
 * stored, also when the repository was reopened between the commits (nothing else then marks it).
 */
public class JavaASTAdoptedMetadataStoredTest {

    private static final String WITHOUT = "class C {\n    void f() {\n        int x = 1;\n        g();\n    }\n}\n";
    private static final String WITH = "class C {\n    void f() {\n        // one\n        int x = 1;\n        g();\n    }\n}\n";

    @Test
    @Timeout(120)
    public void aCommentTakenOverByAStoredStatementIsStored() throws Exception {
        Path work = Files.createTempDirectory("adopted-metadata-stored");
        Path repo = work.resolve(".ecco");
        Path v1 = Files.createDirectories(work.resolve("v1"));
        Path v2 = Files.createDirectories(work.resolve("v2"));
        Files.writeString(v1.resolve("C.java"), WITHOUT);
        Files.writeString(v2.resolve("C.java"), WITH);
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repo);
            service.init();
            service.setBaseDir(v1);
            service.commit("without", "A");
        }
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repo);
            service.open();
            service.setBaseDir(v2);
            service.commit("with", "A");
        }
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repo);
            service.open();
            Path out = Files.createDirectories(work.resolve("out"));
            service.setBaseDir(out);
            service.checkout("A");
            String written = Files.readString(out.resolve("C.java"));
            assertTrue(written.contains("// one"), written);
            assertEquals(WITH, written);
        }
    }
}
