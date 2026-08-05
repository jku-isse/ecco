package at.jku.isse.ecco.storage.ser.dao;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.core.Variant;
import at.jku.isse.ecco.dao.TransactionStrategy.TRANSACTION;
import at.jku.isse.ecco.storage.ser.core.SerCommit;
import at.jku.isse.ecco.storage.ser.core.SerVariant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SerCommitDaoTest {

    private SerTransactionStrategy transactionStrategy;
    private SerCommitDao dao;

    @BeforeEach
    public void setUp() throws IOException {
        Path repoDir = Files.createTempDirectory("ser-commit-dao");
        transactionStrategy = new SerTransactionStrategy(repoDir);
        dao = new SerCommitDao(transactionStrategy);
    }

    @AfterEach
    public void tearDown() {
        if (transactionStrategy.getTransaction() != null) {
            transactionStrategy.end();
        }
    }

    @Test
    public void saveAssignsIdAndMakesCommitLoadable() {
        transactionStrategy.begin(TRANSACTION.READ_WRITE);

        SerCommit commit = new SerCommit("alice");
        assertNull(commit.getId());

        Commit saved = dao.save(commit);

        assertNotNull(saved.getId());
        assertSame(commit, dao.load(saved.getId()));

        transactionStrategy.end();
    }

    @Test
    public void loadAllCommitsReturnsEverySavedCommit() {
        transactionStrategy.begin(TRANSACTION.READ_WRITE);

        dao.save(new SerCommit("alice"));
        dao.save(new SerCommit("bob"));

        List<Commit> commits = dao.loadAllCommits();
        assertEquals(2, commits.size());

        transactionStrategy.end();
    }

    @Test
    public void removeByIdRequiresReadWriteTransaction() {
        transactionStrategy.begin(TRANSACTION.READ_ONLY);

        assertThrows(EccoException.class, () -> dao.remove("some-id"));

        transactionStrategy.end();
    }

    @Test
    public void removeByEntityRequiresReadWriteTransaction() {
        transactionStrategy.begin(TRANSACTION.READ_ONLY);

        assertThrows(EccoException.class, () -> dao.remove(new SerCommit("alice")));

        transactionStrategy.end();
    }

    @Test
    public void removeByIdDeletesCommitUnderReadWriteTransaction() {
        transactionStrategy.begin(TRANSACTION.READ_WRITE);
        SerCommit commit = new SerCommit("alice");
        String id = dao.save(commit).getId();

        dao.remove(id);

        assertNull(dao.load(id));
        transactionStrategy.end();
    }

    @Test
    public void removeByEntityDeletesCommitUnderReadWriteTransaction() {
        transactionStrategy.begin(TRANSACTION.READ_WRITE);
        SerCommit commit = new SerCommit("alice");
        dao.save(commit);

        dao.remove(commit);

        assertTrue(dao.loadAllCommits().isEmpty());
        transactionStrategy.end();
    }

    @Test
    public void saveVariantAssignsIdAndStoresItInDatabase() {
        transactionStrategy.begin(TRANSACTION.READ_WRITE);

        SerVariant variant = new SerVariant("release-1", null, null);
        assertNull(variant.getId());

        Variant saved = dao.save((Variant) variant);

        assertNotNull(saved.getId());
        assertSame(variant, transactionStrategy.getDatabase().getVariantIndex().get(saved.getId()));

        transactionStrategy.end();
    }
}
