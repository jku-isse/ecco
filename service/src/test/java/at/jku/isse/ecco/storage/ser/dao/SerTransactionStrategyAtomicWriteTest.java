package at.jku.isse.ecco.storage.ser.dao;

import at.jku.isse.ecco.service.EccoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * writeStored() (SerTransactionStrategy.java) used to write directly to the target association/
 * artifact file, truncating it in place when it already existed -- not crash-safe for a MODIFIED
 * pre-existing association/artifact (as opposed to a brand new one), since a crash between the
 * truncate and the write completing would corrupt a file the still-current, not-yet-swapped core
 * already depends on. Fixed by writing to a temp file first and atomically renaming it into place.
 * Committing the same content twice under two different configurations forces Repository.extract()
 * to re-slice and re-persist the FIRST commit's association (see OriginalAssociationDirtyTracking
 * RegressionTest/RepositoryOpExtractTest's "shared.txt" pattern), exercising exactly that overwrite
 * path. This can't reproduce the crash itself (that needs killing the process mid-write), but pins
 * that a normal overwrite still succeeds and leaves no stray temp files behind.
 */
public class SerTransactionStrategyAtomicWriteTest {

    @Test
    @Timeout(30)
    public void overwritingAnExistingAssociationLeavesNoStrayTempFiles() throws IOException {
        Path workDir = Files.createTempDirectory("ser-transaction-strategy-atomic-write");
        Path repoDir = workDir.resolve(".ecco");
        Path contentDir = workDir.resolve("shared");
        Files.createDirectories(contentDir);
        Files.writeString(contentDir.resolve("shared.txt"), "shared\n");

        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repoDir);
            service.init();
            service.setBaseDir(contentDir);

            service.commit("commit A", "A");
            // re-commits the SAME content under a different configuration - forces Repository.extract()
            // to slice and re-persist (overwrite) the association the first commit already wrote.
            service.commit("commit B", "B");

            List<String> leftoverTempFiles = Stream.of("associations", "artifacts")
                    .map(repoDir::resolve)
                    .filter(Files::exists)
                    .flatMap(dir -> {
                        try (Stream<Path> files = Files.list(dir)) {
                            return files.map(Path::toString).filter(name -> name.endsWith(".tmp")).collect(Collectors.toList()).stream();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .collect(Collectors.toList());

            assertTrue(leftoverTempFiles.isEmpty(), "no .tmp files should remain after a successful commit: " + leftoverTempFiles);

            // sanity: the repository must still load and contain both configurations' content correctly
            assertTrue(service.getRepository().getFeatures().stream().anyMatch(f -> f.getName().equals("A")));
            assertTrue(service.getRepository().getFeatures().stream().anyMatch(f -> f.getName().equals("B")));
        }
    }
}
