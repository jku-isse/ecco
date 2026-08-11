package at.jku.isse.ecco.adapter.dispatch;

import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.service.listener.EccoListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * TextReader claims "**.java" (alongside .txt/.xml/...) at priority 1, the same priority
 * JavaASTReader (adapter/java-ast) originally used - a tie broken only by Guice's unspecified
 * Set<ArtifactReader> iteration order, so which one actually handled a .java file depended on the
 * JVM/build rather than on any real precedence rule. Reproduced for real via a fresh EccoService
 * (relies on AdapterPreferences' actual defaults - both TextPlugin and JavaASTPlugin are enabled
 * out of the box, so this exercises the real ambiguity rather than a contrived setup) committing a
 * .java file and recording which reader's fileReadEvent actually fired. Fixed by moving
 * JavaASTReader to Integer.MAX_VALUE, matching the convention adapter/java's JavaBlockReader and
 * adapter/challenge's JavaChallengeReader already use for the same reason.
 */
public class DispatchReaderJavaPluginPriorityTest {

    @Test
    @Timeout(30)
    public void javaFilesAreReadByTheJavaASTAdapterNotTheGenericTextAdapter() throws IOException {
        Path workDir = Files.createTempDirectory("dispatch-reader-java-priority");
        Path repoDir = workDir.resolve(".ecco");
        Path contentDir = workDir.resolve("content");
        Files.createDirectories(contentDir);
        Files.writeString(contentDir.resolve("Foo.java"), "public class Foo {\n}\n");

        Map<Path, String> pluginIdByFile = new HashMap<>();
        EccoListener listener = new EccoListener() {
            @Override
            public void fileReadEvent(Path file, ArtifactReader reader) {
                pluginIdByFile.put(file, reader.getPluginId());
            }
        };

        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repoDir);
            service.init();
            service.setBaseDir(contentDir);
            service.addListener(listener);

            service.commit("commit", "");
        }

        assertEquals("at.jku.cdl.ecco.adapter.java.JavaASTPlugin", pluginIdByFile.get(Path.of("Foo.java")),
                "a .java file should be read by the AST-granularity Java adapter, not a generic catch-all");
    }
}
