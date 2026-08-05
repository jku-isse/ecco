package at.jku.isse.ecco.storage.mem.dao;

import at.jku.isse.ecco.core.Commit;
import at.jku.isse.ecco.storage.ser.core.SerCommit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class MemCommitDaoTest {

    private MemCommitDao dao;

    @BeforeEach
    public void setUp() {
        MemTransactionStrategy transactionStrategy = new MemTransactionStrategy();
        transactionStrategy.open();
        dao = new MemCommitDao(transactionStrategy);
    }

    @Test
    public void saveAssignsIdAndMakesCommitLoadable() {
        SerCommit commit = new SerCommit("alice");
        assertNull(commit.getId());

        Commit saved = dao.save(commit);

        assertNotNull(saved.getId());
        assertSame(commit, saved);
        assertSame(commit, dao.load(saved.getId()));
    }

    @Test
    public void saveOfAlreadyPersistedCommitKeepsItsId() {
        SerCommit commit = new SerCommit("alice");
        String firstId = dao.save(commit).getId();

        String secondId = dao.save(commit).getId();

        assertEquals(firstId, secondId);
    }

    @Test
    public void loadAllCommitsReturnsEverySavedCommit() {
        dao.save(new SerCommit("alice"));
        dao.save(new SerCommit("bob"));

        List<Commit> commits = dao.loadAllCommits();

        assertEquals(2, commits.size());
    }

    @Test
    public void loadOfUnknownIdReturnsNull() {
        assertNull(dao.load("does-not-exist"));
    }

    @Test
    public void removeByIdDeletesCommit() {
        SerCommit commit = new SerCommit("alice");
        String id = dao.save(commit).getId();

        dao.remove(id);

        assertNull(dao.load(id));
        assertTrue(dao.loadAllCommits().isEmpty());
    }

    @Test
    public void removeByEntityDeletesCommit() {
        SerCommit commit = new SerCommit("alice");
        dao.save(commit);

        dao.remove(commit);

        assertTrue(dao.loadAllCommits().isEmpty());
    }
}
