package at.jku.isse.ecco.service;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.core.Remote;
import at.jku.isse.ecco.service.listener.EccoListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Refusals and skips that had no test: a commit naming no feature (FR-M2, tested through the
 * GUI only), a feature name with a character a name may not hold (FR-F1b), a feature name two features share (FR-F3), a .config that cannot be read (FR-M3b),
 * the files ECCO itself writes into a working directory (FR-M5b), no enabled adapter (FR-L4b) and
 * a second start of the sync server (FR-D6a).
 */
public class SmallRefusalsTest {

    @Test
    @Timeout(60)
    public void aFeatureNameWithAnotherCharacterIsRejected(@TempDir Path tmp) throws Exception {
        try (EccoService service = init(tmp.resolve("work"))) {
            EccoException e = assertThrows(EccoException.class, () -> service.parseConfigurationString("A, B$"));
            assertNotNull(e.getMessage());
            assertTrue(service.getCommits().isEmpty());
        }
    }

    /**
     * A feature made on its own in two repositories, with the same name, is one feature after a pull:
     * the pull replays the other repository's commits on this one's features (Repository.Op#
     * adoptConfiguration). It refused the pull, as two features of one name (FR-F6) - names that
     * conditions cannot tell apart, "a-b" and "a_b", still are.
     */
    @Test
    @Timeout(60)
    public void aFeatureOfTheSameNameMadeOnBothSidesIsOneFeatureAfterAPull(@TempDir Path tmp) throws Exception {
        Path other = tmp.resolve("other");
        try (EccoService service = init(other)) {
            Files.writeString(other.resolve("o.txt"), "o\n");
            service.commit("o", "A");
        }
        Path work = tmp.resolve("work");
        try (EccoService service = init(work)) {
            Files.writeString(work.resolve("w.txt"), "w\n");
            service.commit("w", "A");
            service.addRemote("other", other.toString(), Remote.Type.LOCAL);
            service.pull("other");
            assertEquals(1, service.getRepository().getFeatures().stream().filter(f -> f.getName().equals("A")).count());
            assertEquals(2, service.getCommits().size());
        }
    }

    @Test
    @Timeout(60)
    public void aCommitNamingNoFeatureIsRefused(@TempDir Path tmp) throws Exception {
        Path work = tmp.resolve("work");
        try (EccoService service = init(work)) {
            Files.writeString(work.resolve("a.txt"), "a\n");
            EccoException e = assertThrows(EccoException.class, () -> service.commit("no features", ""));
            assertTrue(messages(e).contains("A commit needs at least one feature"), messages(e));
            assertTrue(service.getCommits().isEmpty());
        }
    }

    @Test
    @Timeout(60)
    public void aConfigFileThatCannotBeReadFailsTheCommit(@TempDir Path tmp) throws Exception {
        Path work = tmp.resolve("work");
        try (EccoService service = init(work)) {
            Files.writeString(work.resolve("a.txt"), "a\n");
            Files.createDirectories(work.resolve(EccoService.CONFIG_FILE_NAME)); // exists, cannot be read as a file
            assertThrows(EccoException.class, () -> service.commit("no configuration given"));
            assertTrue(service.getCommits().isEmpty());
        }
    }

    @Test
    @Timeout(60)
    public void theFilesEccoWritesAreNeverCommitted(@TempDir Path tmp) throws Exception {
        Path work = tmp.resolve("work");
        Set<String> read = new HashSet<>();
        try (EccoService service = init(work)) {
            service.addListener(new EccoListener() {
                @Override
                public void fileReadEvent(Path file, ArtifactReader reader) {
                    read.add(file.getFileName().toString());
                }
            });
            Files.writeString(work.resolve("a.txt"), "a\n");
            for (Path name : new Path[]{EccoService.CONFIG_FILE_NAME, EccoService.WARNINGS_FILE_NAME, EccoService.HASHES_FILE_NAME})
                Files.writeString(work.resolve(name), "left over\n");
            service.commit("a", "A");
        }
        read.remove(""); // the working directory itself
        assertEquals(Set.of("a.txt"), read);
    }

    @Test
    @Timeout(60)
    public void openingWithoutAnEnabledAdapterIsRefused(@TempDir Path tmp) throws Exception {
        Path work = tmp.resolve("work");
        try (EccoService service = init(work)) {
            // a repository to open
        }
        Preferences prefs = Preferences.userNodeForPackage(AdapterPreferences.class);
        String key = "disabledAdapterPluginIds";
        String stored = prefs.get(key, null);
        try {
            AdapterPreferences.setDisabledPluginIds(java.util.Arrays.stream(at.jku.isse.ecco.adapter.ArtifactPlugin.getArtifactPlugins())
                    .map(at.jku.isse.ecco.adapter.ArtifactPlugin::getPluginId).collect(Collectors.toSet()));
            try (EccoService service = new EccoService()) {
                service.setRepositoryDir(work.resolve(".ecco"));
                EccoException e = assertThrows(EccoException.class, service::open);
                assertTrue(messages(e).contains("At least one artifact plugin must be configured."), messages(e));
            }
        } finally {
            if (stored != null)
                prefs.put(key, stored);
            else
                prefs.remove(key);
            try {
                prefs.flush();
            } catch (BackingStoreException ignored) {
            }
        }
    }

    @Test
    @Timeout(60)
    public void aSecondStartOfTheSyncServerIsRefused(@TempDir Path tmp) throws Exception {
        try (EccoService service = init(tmp.resolve("work"))) {
            // startServer blocks, running the accept loop, until the server is stopped
            Thread server = new Thread(() -> service.startServer(0), "test-ecco-server");
            server.start();
            try {
                long deadline = System.currentTimeMillis() + 10_000;
                while (service.serverPort() == -1 && System.currentTimeMillis() < deadline)
                    Thread.sleep(20);
                assertTrue(service.serverPort() > 0, "the first server runs");
                EccoException e = assertThrows(EccoException.class, () -> service.startServer(0));
                assertEquals("Server is already running.", e.getMessage());
            } finally {
                service.stopServer();
                server.join(10_000);
            }
        }
    }

    private static EccoService init(Path dir) throws Exception {
        Files.createDirectories(dir);
        EccoService service = new EccoService();
        service.setRepositoryDir(dir.resolve(".ecco"));
        service.init();
        service.setBaseDir(dir);
        return service;
    }

    private static String messages(Throwable e) {
        StringBuilder sb = new StringBuilder();
        for (Throwable t = e; t != null; t = t.getCause())
            sb.append(t.getMessage()).append(" | ");
        return sb.toString();
    }
}
