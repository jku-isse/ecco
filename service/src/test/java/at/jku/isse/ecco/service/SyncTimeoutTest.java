package at.jku.isse.ecco.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.nio.channels.SocketChannel;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A remote that accepts the connection and never answers held a fetch - and the service's lock,
 * which the GUI's Save, Close and Quit wait for - forever: connections to remotes now time out.
 */
public class SyncTimeoutTest {

    @Test
    @Timeout(30)
    public void connectionsToARemoteHaveAReadTimeout() throws Exception {
        try (ServerSocket silent = new ServerSocket(0); SocketChannel channel = SocketChannel.open()) {
            assertTrue(RemoteSyncService.connect(channel, new InetSocketAddress("localhost", silent.getLocalPort())));
            assertEquals(RemoteSyncService.READ_TIMEOUT_MILLIS, channel.socket().getSoTimeout());
            assertTrue(RemoteSyncService.READ_TIMEOUT_MILLIS > 0);

            // a read from the silent remote ends, here after a shortened timeout
            channel.socket().setSoTimeout(200);
            assertThrows(SocketTimeoutException.class, () -> channel.socket().getInputStream().read());
        }
    }

    /**
     * A remote that cannot be reached is named: the failure was a bare "Connection refused", and the
     * "Error connecting to remote" messages were never reached - connect threw before them
     * (FailureFlowsEndToEndTest found it).
     */
    @Test
    @Timeout(30)
    public void aRemoteThatCannotBeReachedIsNamed() throws Exception {
        int closed;
        try (ServerSocket free = new ServerSocket(0)) {
            closed = free.getLocalPort();
        }
        try (SocketChannel channel = SocketChannel.open()) {
            java.net.ConnectException refused = assertThrows(java.net.ConnectException.class,
                    () -> RemoteSyncService.connect(channel, new InetSocketAddress("localhost", closed)));
            assertTrue(refused.getMessage().startsWith("Error connecting to remote localhost:" + closed + ": "), refused.getMessage());
            assertNotNull(refused.getCause());
        }
    }
}
