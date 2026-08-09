package at.jku.isse.ecco.storage.ser.dao;

import at.jku.isse.ecco.dao.TransactionStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * reset() (called by rollback()/close()) used to null out writeFileChannel/writeFileLock without
 * closing/releasing them first -- leaking the exclusive write-lock file handle acquired in
 * beginReadWrite(). Every subsequent begin(READ_WRITE) attempt for the rest of the process's
 * lifetime then threw OverlappingFileLockException, permanently write-locking the repository after
 * any failed write transaction (the standard begin(READ_WRITE); ...; catch { rollback(); throw; }
 * pattern used throughout EccoService/RemoteSyncService for essentially every write operation).
 */
public class SerTransactionStrategyRollbackTest {

    @Test
    @Timeout(10)
    public void beginReadWriteSucceedsAgainAfterARollback() throws Exception {
        Path repoDir = Files.createTempDirectory("ser-transaction-strategy-rollback");
        SerTransactionStrategy strategy = new SerTransactionStrategy(repoDir);
        strategy.open();

        strategy.begin(TransactionStrategy.TRANSACTION.READ_WRITE);
        strategy.rollback();

        assertDoesNotThrow(() -> {
            strategy.begin(TransactionStrategy.TRANSACTION.READ_WRITE);
            strategy.end();
        }, "a rolled-back READ_WRITE transaction must release its write lock, not leak it forever");

        strategy.close();
    }
}
