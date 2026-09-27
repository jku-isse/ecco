package at.jku.isse.ecco.adapter.dispatch;

import at.jku.isse.ecco.service.EccoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A text file's recorded format (TextFileFormat) lives on its plugin artifact, which all variants
 * share; the repository keeps the artifact stored first and discards the equal one of a newer
 * commit. Without ArtifactData.adoptMetadataFrom() the FIRST commit's format therefore won forever
 * (and files committed before formats were recorded never got one). The newest commit's format
 * must win - checked after reopening, i.e. it is persisted.
 */
public class TextFormatLastCommitWinsTest {

    @Test
    @Timeout(60)
    public void theNewestCommitsLineSeparatorIsUsedForCheckout() throws Exception {
        Path workDir = Files.createTempDirectory("text-format-last-wins");
        Path repoDir = workDir.resolve(".ecco");
        Path content = Files.createDirectories(workDir.resolve("content"));
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repoDir);
            service.init();
            service.setBaseDir(content);
            Files.writeString(content.resolve("f.txt"), "a\r\nb\r\n");
            service.commit("crlf", "A");
            Files.writeString(content.resolve("f.txt"), "a\nc\n");
            service.commit("lf", "B");
        }

        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repoDir);
            service.open();
            Path checkout = Files.createDirectories(workDir.resolve("checkout"));
            service.setBaseDir(checkout);
            service.checkout("B");
            assertEquals("a\nc\n", Files.readString(checkout.resolve("f.txt")));
        }
    }
}
