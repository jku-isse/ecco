package at.jku.isse.ecco.adapter.dispatch;

import at.jku.isse.ecco.service.EccoService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Several readers (text, file, cpp, java-ast, and the python/lilypond subprocess parsers) created
 * the file's plugin node first and then only logged/printed a failure to read the content - so an
 * unreadable file was committed as an EMPTY file, and a later checkout silently overwrote the real
 * file with nothing. Same policy as DispatchReaderUnreadableDirectoryTest: the commit must fail
 * loudly instead, and nothing may be recorded.
 */
public class UnreadableFileCommitTest {

    @ParameterizedTest
    @ValueSource(strings = {"unreadable.txt", "unreadable.bin"}) // text adapter, file adapter
    @Timeout(30)
    public void aFileThatCannotBeReadFailsTheCommitInsteadOfBeingCommittedEmpty(String fileName) throws IOException {
        Path workDir = Files.createTempDirectory("unreadable-file-commit");
        Path contentDir = Files.createDirectories(workDir.resolve("content"));
        Files.writeString(contentDir.resolve("normal.txt"), "normal\n");
        Path unreadable = contentDir.resolve(fileName);
        Files.writeString(unreadable, "real content\n");
        Files.setPosixFilePermissions(unreadable, PosixFilePermissions.fromString("---------"));

        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(workDir.resolve(".ecco"));
            service.init();
            service.setBaseDir(contentDir);

            assertThrows(Exception.class, () -> service.commit("commit", "A"),
                    "committing a file whose content can't be read must fail, not commit it empty");
            assertEquals(0, service.getRepository().getCommits().size(), "nothing may have been committed");
        } finally {
            Files.setPosixFilePermissions(unreadable, PosixFilePermissions.fromString("rw-r--r--"));
        }
    }
}
