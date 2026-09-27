package at.jku.isse.ecco.storage.ser.dao;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.dao.TransactionStrategy;
import at.jku.isse.ecco.storage.ser.repository.SerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Repositories written before associations (520155c1) and then artifacts (d92c439e) got their own
 * files deserialize with SerRepository.associationIds / artifactIds == null (the fields didn't exist
 * yet). Loading one failed with a bare NullPointerException; it now fails with an explanation. A real
 * migration isn't possible: the old formats' embedded content lives in fields that are transient now.
 */
public class OldStorageFormatTest {

    @Test
    @Timeout(30)
    public void aRepositoryWithoutAssociationIdsIsReportedAsAnOldFormat() throws Exception {
        assertOldFormatIsReported("associationIds");
    }

    @Test
    @Timeout(30)
    public void aRepositoryWithoutArtifactIdsIsReportedAsAnOldFormat() throws Exception {
        assertOldFormatIsReported("artifactIds");
    }

    private static void assertOldFormatIsReported(String missingField) throws Exception {
        Path repoDir = Files.createTempDirectory("old-storage-format");
        SerTransactionStrategy strategy = new SerTransactionStrategy(repoDir);
        strategy.open();
        strategy.begin(TransactionStrategy.TRANSACTION.READ_WRITE);
        SerRepository repository = (SerRepository) strategy.getDatabase().getRepository();
        Field field = SerRepository.class.getDeclaredField(missingField);
        field.setAccessible(true);
        field.set(repository, null); // as deserialized from a file written before the field existed
        strategy.end();
        strategy.close();

        SerTransactionStrategy reopened = new SerTransactionStrategy(repoDir);
        reopened.open();
        EccoException exception = assertThrows(EccoException.class, () -> reopened.begin(TransactionStrategy.TRANSACTION.READ_ONLY));
        String messages = "";
        for (Throwable t = exception; t != null; t = t.getCause()) messages += t.getMessage() + "\n";
        assertTrue(messages.contains("older storage format"), messages);
    }
}
