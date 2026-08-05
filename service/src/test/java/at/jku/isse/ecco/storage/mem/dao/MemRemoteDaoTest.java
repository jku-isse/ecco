package at.jku.isse.ecco.storage.mem.dao;

import at.jku.isse.ecco.core.Remote;
import at.jku.isse.ecco.storage.ser.core.SerRemote;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

public class MemRemoteDaoTest {

    private MemRemoteDao dao;

    @BeforeEach
    public void setUp() {
        MemTransactionStrategy transactionStrategy = new MemTransactionStrategy();
        transactionStrategy.open();
        dao = new MemRemoteDao(transactionStrategy);
    }

    @Test
    public void storeRemoteMakesItLoadableByName() {
        SerRemote remote = new SerRemote("origin", "/some/path", Remote.Type.LOCAL);

        Remote stored = dao.storeRemote(remote);

        assertSame(remote, stored);
        assertSame(remote, dao.loadRemote("origin"));
    }

    @Test
    public void loadAllRemotesReturnsEveryStoredRemote() {
        dao.storeRemote(new SerRemote("origin", "/a", Remote.Type.LOCAL));
        dao.storeRemote(new SerRemote("upstream", "/b", Remote.Type.LOCAL));

        Collection<Remote> remotes = dao.loadAllRemotes();

        assertEquals(2, remotes.size());
    }

    @Test
    public void loadRemoteOfUnknownNameReturnsNull() {
        assertNull(dao.loadRemote("does-not-exist"));
    }

    @Test
    public void loadRemoteRejectsNullName() {
        assertThrows(NullPointerException.class, () -> dao.loadRemote(null));
    }

    @Test
    public void loadRemoteRejectsEmptyName() {
        assertThrows(IllegalArgumentException.class, () -> dao.loadRemote(""));
    }

    @Test
    public void removeRemoteDeletesIt() {
        dao.storeRemote(new SerRemote("origin", "/a", Remote.Type.LOCAL));

        dao.removeRemote("origin");

        assertNull(dao.loadRemote("origin"));
        assertTrue(dao.loadAllRemotes().isEmpty());
    }

    @Test
    public void storeRemoteRejectsNull() {
        assertThrows(NullPointerException.class, () -> dao.storeRemote(null));
    }

    @Test
    public void removeRemoteRejectsNullName() {
        assertThrows(NullPointerException.class, () -> dao.removeRemote(null));
    }
}
