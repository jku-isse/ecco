package at.jku.isse.ecco.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link EccoService#getRepositoryHomeDir()} exists specifically because {@link Path#getParent()}
 * returns {@code null} for a bare relative single-segment path like {@code .ecco} -- exactly what
 * {@link EccoService#detectRepository()} sets repositoryDir to when running from the repository's
 * own home directory (the most common real case, not an edge case).
 */
public class RepositoryHomeDirTest {

    @Test
    public void bareRelativeRepositoryDir_resolvesToRealCwd_notNull() {
        // matches detectRepository()'s own construction: Paths.get("").resolve(".ecco") == ".ecco",
        // a single-segment relative path with no parent segment of its own.
        EccoService service = new EccoService(Paths.get(""), Paths.get(".ecco"));

        Path homeDir = service.getRepositoryHomeDir();

        assertEquals(Paths.get("").toAbsolutePath().normalize(), homeDir);
    }

    @Test
    public void absoluteRepositoryDir_resolvesToItsRealParent() throws IOException {
        Path tempDir = Files.createTempDirectory("repository-home-dir-test");
        Path repositoryDir = tempDir.resolve(".ecco");
        Files.createDirectory(repositoryDir);

        EccoService service = new EccoService(tempDir, repositoryDir);

        Path homeDir = service.getRepositoryHomeDir();

        assertEquals(tempDir.toRealPath(), homeDir);
    }
}
