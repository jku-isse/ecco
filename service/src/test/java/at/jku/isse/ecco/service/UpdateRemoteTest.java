package at.jku.isse.ecco.service;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.core.Remote;
import at.jku.isse.ecco.feature.Feature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link EccoService#updateRemote}: changes name, address and type of a remote, keeps what was
 * fetched from it, survives reopening, and leaves the remote unchanged when it refuses.
 */
public class UpdateRemoteTest {

    /** A rename with a new address and type is stored, and the fetched features stay with the remote. */
    @Test
    @Timeout(30)
    public void renameAndReaddressKeepFetchedFeaturesAndSurviveReopening(@TempDir Path tmp) throws Exception {
        Path originDir = Files.createDirectories(tmp.resolve("origin"));
        try (EccoService origin = new EccoService()) {
            origin.setRepositoryDir(originDir.resolve(".ecco"));
            origin.init();
            Path variant = Files.createDirectories(tmp.resolve("v1"));
            Files.writeString(variant.resolve("a.txt"), "a\n");
            origin.setBaseDir(variant);
            origin.commit("A", "A.1");
        }

        Path repo = Files.createDirectories(tmp.resolve("repo")).resolve(".ecco");
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repo);
            service.init();
            service.addRemote("origin", originDir.toString(), Remote.Type.LOCAL);
            service.fetch("origin");

            Remote changed = service.updateRemote("origin", "upstream", "localhost:4711", Remote.Type.REMOTE);
            assertEquals("upstream", changed.getName());
        }

        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repo);
            service.open();
            assertNull(service.getRemote("origin"));
            Remote upstream = service.getRemote("upstream");
            assertNotNull(upstream);
            assertEquals("localhost:4711", upstream.getAddress());
            assertEquals(Remote.Type.REMOTE, upstream.getType());
            assertEquals(Set.of("A"), upstream.getFeatures().stream().map(Feature::getName).collect(Collectors.toSet()));
            assertEquals(1, service.getRemotes().size());
        }
    }

    /** An unknown remote, a taken or empty name, and an address that does not fit the type are refused. */
    @Test
    @Timeout(30)
    public void refusalsLeaveTheRemoteUnchanged(@TempDir Path tmp) {
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(tmp.resolve(".ecco"));
            service.init();
            service.addRemote("origin", "localhost:9999", Remote.Type.REMOTE);
            service.addRemote("other", tmp.toString(), Remote.Type.LOCAL);

            assertEquals("Remote 'nope' does not exist.",
                    assertThrows(EccoException.class, () -> service.updateRemote("nope", "x", "localhost:1", Remote.Type.REMOTE)).getMessage());
            assertEquals("Remote with this name already exists.",
                    assertThrows(EccoException.class, () -> service.updateRemote("origin", "other", "localhost:1", Remote.Type.REMOTE)).getMessage());
            assertThrows(EccoException.class, () -> service.updateRemote("origin", " ", "localhost:1", Remote.Type.REMOTE));
            assertThrows(EccoException.class, () -> service.updateRemote("origin", "origin", "no port here", Remote.Type.REMOTE));

            Remote origin = service.getRemote("origin");
            assertEquals("localhost:9999", origin.getAddress());
            assertEquals(Remote.Type.REMOTE, origin.getType());
            assertEquals(2, service.getRemotes().size());

            // keeping the name is no clash with itself
            service.updateRemote("origin", "origin", "localhost:1234", Remote.Type.REMOTE);
            assertEquals("localhost:1234", service.getRemote("origin").getAddress());
        }
    }
}
