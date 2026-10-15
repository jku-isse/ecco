package at.jku.isse.ecco.service;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.feature.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A write that cannot begin - the repository's write lock cannot be opened - reports which
 * repository and why. Several operations rolled back unconditionally after a begin() that had
 * failed before a transaction existed, and "No transaction active" replaced the reason.
 */
public class TransactionFailureMessageTest {

    @Test
    @Timeout(60)
    public void aWriteThatCannotBeginNamesTheRepositoryAndTheReason(@TempDir Path tmp) throws Exception {
        Path repositoryDir = Files.createDirectories(tmp.resolve("work")).resolve(".ecco");
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repositoryDir);
            service.init();
            Path content = Files.createDirectories(tmp.resolve("v"));
            Files.writeString(content.resolve("a.txt"), "a\n");
            service.setBaseDir(content);
            service.commit("v", "A.1");
            Configuration configuration = service.parseConfigurationString("A.1");

            Path writeLock = repositoryDir.resolve("write");
            assumeTrue(writeLock.toFile().setWritable(false) && !Files.isWritable(writeLock), "file permissions not enforced (root?)");
            try {
                Map<String, Executable> writes = Map.of(
                        "addVariant", () -> service.addVariant(configuration, "v"),
                        "store", service::store,
                        "removeVariant", () -> service.removeVariant("none"));
                for (Map.Entry<String, Executable> write : writes.entrySet()) {
                    EccoException failure = assertThrows(EccoException.class, write.getValue(), write.getKey());
                    String messages = "";
                    for (Throwable t = failure; t != null; t = t.getCause())
                        messages += t.getMessage() + "\n";
                    assertTrue(messages.contains("Could not open for writing the repository " + repositoryDir.toAbsolutePath().normalize()), write.getKey() + ": " + messages);
                    assertFalse(messages.contains("No transaction active"), write.getKey() + ": " + messages);
                }
            } finally {
                writeLock.toFile().setWritable(true);
            }
        }
    }
}
