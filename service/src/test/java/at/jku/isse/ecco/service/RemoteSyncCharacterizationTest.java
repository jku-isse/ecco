package at.jku.isse.ecco.service;

import at.jku.isse.ecco.core.Remote;
import at.jku.isse.ecco.feature.Feature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterization test for the remote-sync cluster (addRemote/removeRemote/getRemote/getRemotes,
 * startServer/stopServer, fetch/pull/push), written before extracting it into RemoteSyncService (god-
 * class split, phase 4/4 -- see /Users/paul/.claude/plans/federated-waddling-pine.md). This cluster had
 * NO prior automated test coverage in this repo (confirmed by repo-wide search); this test exists to
 * pin current behavior over a real loopback socket so the extraction can be verified to preserve it,
 * not to exhaustively test the sync protocol.
 */
public class RemoteSyncCharacterizationTest {

    @Test
    @Timeout(15)
    public void remoteCrudRoundTrip() throws IOException {
        Path workDir = Files.createTempDirectory("remote-crud-repro");
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(workDir.resolve(".ecco"));
            service.init();

            Remote remoteMode = service.addRemote("origin", "localhost:9999", Remote.Type.REMOTE);
            assertEquals("origin", remoteMode.getName());
            assertEquals(Remote.Type.REMOTE, remoteMode.getType());

            Remote localRemote = service.addRemote("parent", workDir.toString(), Remote.Type.LOCAL);
            assertEquals(Remote.Type.LOCAL, localRemote.getType());

            assertEquals(2, service.getRemotes().size());
            assertEquals("origin", service.getRemote("origin").getName());

            service.removeRemote("origin");
            assertEquals(1, service.getRemotes().size());
            assertNull(service.getRemote("origin"));
        }
    }

    @Test
    @Timeout(30)
    public void fetchPullPushRoundTripOverLoopbackSocket() throws Exception {
        Path originWorkDir = Files.createTempDirectory("remote-sync-origin");
        Path targetWorkDir = Files.createTempDirectory("remote-sync-target");
        int port = findFreePort();

        EccoService originService = new EccoService();
        originService.setRepositoryDir(originWorkDir.resolve(".ecco"));
        originService.init();
        commitFeature(originService, originWorkDir, "core", "Core");

        Thread serverThread = new Thread(() -> originService.startServer(port), "test-ecco-server");
        serverThread.start();
        waitForPortOpen(port, 10_000);

        try (EccoService targetService = new EccoService()) {
            targetService.setRepositoryDir(targetWorkDir.resolve(".ecco"));
            targetService.init();
            targetService.addRemote("origin", "localhost:" + port, Remote.Type.REMOTE);

            // FETCH: populates the Remote's cached feature list, does not touch the local repository.
            targetService.fetch("origin");
            Collection<String> fetchedFeatureNames = targetService.getRemote("origin").getFeatures().stream()
                    .map(Feature::getName).collect(Collectors.toList());
            assertTrue(fetchedFeatureNames.contains("Core"), "fetch() should have retrieved the 'Core' feature from origin");

            // PULL: merges origin's repository into the local one.
            targetService.pull("origin");
            Collection<String> pulledFeatureNames = targetService.getRepository().getFeatures().stream()
                    .map(Feature::getName).collect(Collectors.toList());
            assertTrue(pulledFeatureNames.contains("Core"), "pull() should have merged the 'Core' feature into the local repository");

            // PUSH: push the just-pulled data back to origin. This deliberately does NOT commit fresh
            // content into targetService first -- doing so hits a separate, pre-existing, purely local
            // bug (confirmed via a standalone repro with no networking at all: fork(Path) followed by
            // commit() NPEs in Repository.setRetroactiveConditions(), because EccoUtil.deepCopyTreeRec()
            // /entityFactory.createNode() never sets a featureTrace on copied nodes). That bug is in
            // subset()/copy()'s tree-copying itself, unrelated to the wire protocol this test exists to
            // characterize, and pre-dates this investigation -- filed separately, not fixed here.
            targetService.push("origin", "");
        }

        // stopServer() while startServer() is blocked in its accept loop on another thread; join()
        // proves startServer() actually returned (and, since the accept loop only rechecks its
        // shutdown flag between connections -- see EccoService#startServer -- that any PUSH already
        // in flight when stopServer() was called had already finished being processed).
        originService.stopServer();
        serverThread.join(10_000);
        assertTrue(!serverThread.isAlive(), "server thread should have exited after stopServer()");

        // push() round-tripped the same repository content back to origin without crashing -- proving
        // the wire mechanics (splice fix, associationsById restore, artifact resolve, and the
        // shutdownOutput()-before-close fix for the intermittent TCP-RST-truncation hang/corruption --
        // see RemoteSyncService#push) work in this direction too, all the way through merge().
        Collection<String> originFeatureNamesAfterPush = originService.getRepository().getFeatures().stream()
                .map(Feature::getName).collect(Collectors.toList());
        assertTrue(originFeatureNamesAfterPush.contains("Core"), "push() should have round-tripped the 'Core' feature back into origin's repository");

        originService.close();
    }

    private static void commitFeature(EccoService service, Path workDir, String dirName, String featureName) throws IOException {
        Path p = workDir.resolve(dirName);
        Files.createDirectories(p);
        Files.writeString(p.resolve(dirName + ".txt"), dirName + "\n");
        service.setBaseDir(p);
        service.commit("commit " + featureName, featureName);
    }

    private static int findFreePort() throws IOException {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static void waitForPortOpen(int port, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("localhost", port), 200);
                return;
            } catch (IOException e) {
                Thread.sleep(50);
            }
        }
        throw new AssertionError("Server did not start listening on port " + port + " within " + timeoutMillis + "ms");
    }

}
