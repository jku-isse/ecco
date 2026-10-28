package at.jku.isse.ecco.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.util.List;

import static at.jku.isse.ecco.service.SyncHistory.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A fork is a copy of its origin: the same commits (ids, configurations, messages, and what each
 * holds), the same counts and the same conditions - so pulling into it later can tell which commits
 * both have, and counts each once. It had one merge commit per association, without a configuration,
 * and its counts rebuilt from the conditions, counted once.
 */
public class ForkCopyTest {

    // the same configuration committed again with other content, and a feature added later
    private static final List<Step> HISTORY = steps("A=a", "A, B=a|b", "A=a|y", "A, B=a|y|b", "A, C=a|y|c");

    @Test
    @Timeout(120)
    public void aForkHasTheCommitsCountsAndConditionsOfItsOrigin(@TempDir Path tmp) throws Exception {
        Path origin = commit(tmp.resolve("origin"), HISTORY);
        Path fork = fork(origin, tmp.resolve("fork"));

        assertEquals(commits(origin), commits(fork));
        assertEquals(moduleCounts(origin), moduleCounts(fork));
        assertEquals(conditions(origin), conditions(fork));
        assertEquals(checkouts(origin, HISTORY, tmp.resolve("co-origin")), checkouts(fork, HISTORY, tmp.resolve("co-fork")));
    }

    @Test
    @Timeout(120)
    public void aForkOverTheNetworkHasTheCommitsCountsAndConditionsOfItsOrigin(@TempDir Path tmp) throws Exception {
        Path origin = commit(tmp.resolve("origin"), HISTORY);
        Path fork = forkOverTheNetwork(origin, tmp.resolve("fork"));

        assertEquals(commits(origin), commits(fork));
        assertEquals(moduleCounts(origin), moduleCounts(fork));
        assertEquals(conditions(origin), conditions(fork));
        assertEquals(checkouts(origin, HISTORY, tmp.resolve("co-origin")), checkouts(fork, HISTORY, tmp.resolve("co-fork")));
    }

    /**
     * A server too old to know CLONE closes the connection without an answer: the fork is then made
     * by merging, as before - without the origin's commits, but with its content.
     */
    @Test
    @Timeout(120)
    public void aForkFromAServerTooOldToSendACopyIsMerged(@TempDir Path tmp) throws Exception {
        Path origin = commit(tmp.resolve("origin"), HISTORY);
        Path fork = tmp.resolve("fork");
        try (EccoService originService = open(origin); ServerSocket server = new ServerSocket(0)) {
            Thread thread = new Thread(() -> serveAsAnOlderServer(server, originService), "older-server");
            thread.start();
            try (EccoService service = new EccoService()) {
                service.setRepositoryDir(java.nio.file.Files.createDirectories(fork).resolve(".ecco"));
                service.fork("localhost", server.getLocalPort(), "");
            }
            server.close();
            thread.join(10_000);
        }

        assertTrue(commits(fork).stream().noneMatch(commit -> commit.contains(" [A")), "merged: no commit of the origin, " + commits(fork));
        assertEquals(checkouts(origin, HISTORY, tmp.resolve("co-origin")), checkouts(fork, HISTORY, tmp.resolve("co-fork")));
    }

    /**
     * A server too old to know COMMIT_IDS and PULL_COMMITS closes the connection with the commit ids
     * sent still unread: the pull is then made by merging, as before.
     */
    @Test
    @Timeout(120)
    public void aPullFromAServerTooOldToSendCommitsIsMerged(@TempDir Path tmp) throws Exception {
        Path origin = commit(tmp.resolve("origin"), HISTORY);
        Path local = commit(tmp.resolve("local"), steps("D=d"));
        try (EccoService originService = open(origin); ServerSocket server = new ServerSocket(0)) {
            Thread thread = new Thread(() -> serveAsAnOlderServer(server, originService), "older-server");
            thread.start();
            try (EccoService service = open(local)) {
                service.addRemote("older", "localhost:" + server.getLocalPort(), at.jku.isse.ecco.core.Remote.Type.REMOTE);
                service.pull("older");
            }
            server.close();
            thread.join(10_000);
        }

        assertEquals(checkouts(origin, HISTORY, tmp.resolve("co-origin")), checkouts(local, HISTORY, tmp.resolve("co-local")));
    }

    /** answers PULL as a server did before CLONE; closes the connection on anything else, as it did on what it did not know */
    private static void serveAsAnOlderServer(ServerSocket server, EccoService origin) {
        while (!server.isClosed()) {
            try (Socket socket = server.accept()) {
                ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
                ObjectInputStream ois = SyncObjectStreams.newObjectInputStream(socket.getInputStream());
                if (!"PULL".equals(ois.readObject()))
                    continue;
                ois.readObject(); // the revisions left out: none here
                at.jku.isse.ecco.repository.Repository.Op repository = (at.jku.isse.ecco.repository.Repository.Op) origin.getRepository();
                at.jku.isse.ecco.repository.Repository.Op subset = repository.subset(List.of(), repository.getMaxOrder(), repository.getEntityFactory());
                oos.writeObject(0);
                SyncObjectStreams.writeRepository(oos, subset);
                oos.flush();
                socket.shutdownOutput();
                socket.getInputStream().read(); // until the client closes
            } catch (Exception e) {
                // closed
            }
        }
    }
}
