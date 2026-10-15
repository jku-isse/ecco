package at.jku.isse.ecco.storage.ser.dao;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.dao.TransactionStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Repositories in a storage layout older than the current one - a file per artifact or association,
 * the artifact index in the core, or older still - are refused with an explanation naming the
 * version that converts them, instead of failing somewhere in loading.
 */
public class OldStorageFormatTest {

    /** A repository written by version 0.2.0 (artifact index in the core, a file per association). */
    @Test
    @Timeout(30)
    public void anOlderLayoutIsRefusedNamingTheVersionThatConvertsIt(@TempDir Path tmp) throws Exception {
        Path repoDir = tmp.resolve(".ecco");
        Path fixture = Path.of("src/test/resources/repositories/before-pack-order/.ecco");
        try (Stream<Path> walk = Files.walk(fixture)) {
            for (Path p : walk.sorted().toList()) {
                Path target = repoDir.resolve(fixture.relativize(p).toString());
                if (Files.isDirectory(p)) Files.createDirectories(target); else Files.copy(p, target);
            }
        }
        SerTransactionStrategy strategy = new SerTransactionStrategy(repoDir);
        strategy.open();
        EccoException exception = assertThrows(EccoException.class, () -> strategy.begin(TransactionStrategy.TRANSACTION.READ_ONLY));
        String messages = "";
        for (Throwable t = exception; t != null; t = t.getCause()) messages += t.getMessage() + "\n";
        assertTrue(messages.contains("older layout") && messages.contains(SerTransactionStrategy.CONVERTING_VERSION) && messages.contains("ecco convert"), messages);
    }

    /** A core file without its ecco.ser entry (damaged/truncated) used to end in a NullPointerException. */
    @Test
    @Timeout(30)
    public void aCoreFileWithoutItsEntryIsReportedAsDamaged() throws Exception {
        Path repoDir = Files.createTempDirectory("damaged-core-file");
        SerTransactionStrategy strategy = new SerTransactionStrategy(repoDir);
        strategy.open();
        strategy.begin(TransactionStrategy.TRANSACTION.READ_WRITE);
        strategy.end();
        strategy.close();
        String id = Files.readString(repoDir.resolve("id")).trim();
        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(Files.newOutputStream(repoDir.resolve(id + ".ser.zip")))) {
            zip.putNextEntry(new java.util.zip.ZipEntry("something-else"));
        }

        SerTransactionStrategy reopened = new SerTransactionStrategy(repoDir);
        reopened.open();
        EccoException exception = assertThrows(EccoException.class, () -> reopened.begin(TransactionStrategy.TRANSACTION.READ_ONLY));
        String messages = "";
        for (Throwable t = exception; t != null; t = t.getCause()) messages += t.getMessage() + "\n";
        assertTrue(messages.contains("damaged"), messages);
    }
}
