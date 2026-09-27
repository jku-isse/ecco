package at.jku.isse.ecco.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import syncfiltertest.EvilPayload;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The fetch/pull/push server used to (1) deserialize whatever a peer sent with a plain
 * ObjectInputStream - any serializable class on the classpath, i.e. a remote-code-execution vector
 * via deserialization gadgets, (2) listen on all network interfaces with no authentication, and (3)
 * run its whole accept loop inside the synchronized EccoService.startServer(), holding the service
 * monitor for the server's lifetime so every other synchronized call (commit, checkout,
 * getRepository, ...) blocked until the server was stopped.
 */
public class RemoteSyncServerHardeningTest {

    @Test
    @Timeout(60)
    public void otherServiceCallsAreNotBlockedWhileTheServerRuns() throws Exception {
        try (Server server = Server.start()) {
            assertTimeoutPreemptively(Duration.ofSeconds(10), () -> server.service.getRepository(),
                    "getRepository() must not wait for the server to stop");
        }
    }

    @Test
    @Timeout(60)
    public void payloadsOfNonEccoClassesAreNotDeserialized() throws Exception {
        EvilPayload.deserialized = false;
        try (Server server = Server.start()) {
            try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), server.port)) {
                ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
                oos.writeObject("PUSH");
                oos.writeObject(new EvilPayload());
                oos.flush();
                socket.shutdownOutput();
                // wait for the server to finish with the connection (it closes it after handling,
                // possibly with a reset since it stops reading after rejecting the payload)
                try {
                    socket.getInputStream().readAllBytes();
                } catch (IOException ignored) {
                }
            }
        }
        assertFalse(EvilPayload.deserialized, "a class outside the sync allow-list must be rejected before its readObject() runs");
    }

    @Test
    @Timeout(60)
    public void theServerOnlyListensOnLoopbackByDefault() throws Exception {
        Optional<InetAddress> externalAddress = Collections.list(NetworkInterface.getNetworkInterfaces()).stream()
                .filter(i -> {
                    try {
                        return i.isUp() && !i.isLoopback();
                    } catch (IOException e) {
                        return false;
                    }
                })
                .flatMap(i -> Collections.list(i.getInetAddresses()).stream())
                .filter(a -> a instanceof Inet4Address && !a.isLoopbackAddress())
                .findFirst();
        assumeTrue(externalAddress.isPresent(), "needs a non-loopback IPv4 address");

        try (Server server = Server.start()) {
            assertThrows(IOException.class, () -> {
                try (Socket socket = new Socket()) {
                    socket.connect(new InetSocketAddress(externalAddress.get(), server.port), 2000);
                }
            }, "the sync server must not be reachable from other hosts unless explicitly enabled");
        }
    }

    private static final class Server implements AutoCloseable {
        final EccoService service;
        final int port;
        final Thread thread;

        private Server(EccoService service, int port, Thread thread) {
            this.service = service;
            this.port = port;
            this.thread = thread;
        }

        static Server start() throws Exception {
            Path workDir = Files.createTempDirectory("remote-sync-hardening");
            EccoService service = new EccoService();
            service.setRepositoryDir(workDir.resolve(".ecco"));
            service.init();
            Path content = Files.createDirectories(workDir.resolve("content"));
            Files.writeString(content.resolve("core.txt"), "core\n");
            service.setBaseDir(content);
            service.commit("commit Core", "Core");

            int port;
            try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
                port = socket.getLocalPort();
            }
            Thread thread = new Thread(() -> service.startServer(port), "test-ecco-server");
            thread.setDaemon(true);
            thread.start();
            long deadline = System.currentTimeMillis() + 10_000;
            while (!service.serverRunning() && System.currentTimeMillis() < deadline) Thread.sleep(20);
            Thread.sleep(200); // serverRunning is set just before bind()
            return new Server(service, port, thread);
        }

        @Override
        public void close() throws Exception {
            this.service.stopServer();
            this.thread.join(10_000);
            this.service.close();
        }
    }
}
