package at.jku.isse.ecco.service;

import at.jku.isse.ecco.core.Remote;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Remote addresses were recognized as host:port only if the host consisted of letters
 * ("[a-zA-Z]+:[0-9]+"), so "127.0.0.1:3770" or "my-host.example.org:80" were silently stored as
 * LOCAL remotes - paths - and every later fetch/pull/push against them failed.
 */
public class RemoteAddressTest {

    @Test
    public void hostPortAddressesAreRecognized() {
        assertHostPort("localhost:3770", "localhost", 3770);
        assertHostPort("127.0.0.1:3770", "127.0.0.1", 3770);
        assertHostPort("my-host.example.org:80", "my-host.example.org", 80);
        assertHostPort("[::1]:3770", "::1", 3770);
        assertTrue(RemoteAddress.parseHostPort("../other/.ecco").isEmpty());
        assertTrue(RemoteAddress.parseHostPort("/some/repo").isEmpty());
        assertTrue(RemoteAddress.parseHostPort("host:99999").isEmpty(), "port out of range");
        assertTrue(RemoteAddress.parseHostPort("host:").isEmpty());
    }

    @Test
    @Timeout(30)
    public void anIpAddressRemoteIsStoredAsRemoteNotAsLocalPath() throws Exception {
        Path repoDir = Files.createTempDirectory("remote-address").resolve(".ecco");
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repoDir);
            service.init();
            assertEquals(Remote.Type.REMOTE, service.addRemote("ip", "127.0.0.1:3770").getType());
            assertEquals(Remote.Type.REMOTE, service.addRemote("dotted", "my-host.example.org:80").getType());
            assertEquals(Remote.Type.LOCAL, service.addRemote("path", "../other/.ecco").getType());
        }
    }

    private static void assertHostPort(String address, String host, int port) {
        InetSocketAddress parsed = RemoteAddress.parseHostPort(address).orElseThrow(() -> new AssertionError("not recognized: " + address));
        assertEquals(host, parsed.getHostString());
        assertEquals(port, parsed.getPort());
    }
}
