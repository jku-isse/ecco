package at.jku.isse.ecco.adapter.dispatch;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.service.EccoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * .adapters fixes, per repository and at creation time, which adapter reads which files - on
 * purpose: the adapter determines the shape of a file's artifact tree, so re-routing a file later
 * would make its history look entirely rewritten. But a mapping whose adapter was later disabled
 * (or removed) used to be dropped silently, so its files fell through to the next matching adapter
 * (typically text/file) - exactly that tree-shape change, without any notice. Now the commit fails,
 * naming the file and the adapter.
 */
public class UnavailableAdapterRoutingTest {

    private static final String GONE_PLUGIN = "at.jku.isse.ecco.adapter.gone.GonePlugin";

    /**
     * FR-M6c: a repository's .adapters, once written, stays as it is - enabling another adapter
     * afterwards does not rewrite it, so the repository keeps reading its files as before.
     */
    @Test
    @Timeout(60)
    public void theAdaptersFileIsKeptWhenTheEnabledAdaptersChange() throws Exception {
        Path workDir = Files.createTempDirectory("adapters-file-kept");
        Path repoDir = workDir.resolve(".ecco");
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repoDir);
            service.init();
        }
        Path adaptersFile = repoDir.resolve(".adapters");
        List<String> written = Files.readAllLines(adaptersFile);

        java.util.prefs.Preferences prefs = java.util.prefs.Preferences.userNodeForPackage(at.jku.isse.ecco.service.AdapterPreferences.class);
        String key = "disabledAdapterPluginIds";
        String stored = prefs.get(key, null);
        try {
            java.util.Set<String> disabled = new java.util.HashSet<>(at.jku.isse.ecco.service.AdapterPreferences.getDisabledPluginIds());
            // the challenge adapter, off by default, claims *.java too
            assertTrue(disabled.remove("at.jku.isse.ecco.adapter.challenge.JavaPlugin"), "off by default: " + disabled);
            at.jku.isse.ecco.service.AdapterPreferences.setDisabledPluginIds(disabled);
            Path content = Files.createDirectories(workDir.resolve("content"));
            Files.writeString(content.resolve("Main.java"), "class Main {\n}\n");
            try (EccoService service = new EccoService()) {
                service.setRepositoryDir(repoDir);
                service.open();
                service.setBaseDir(content);
                service.commit("java", "A");
            }
            assertEquals(written, Files.readAllLines(adaptersFile), "written once, kept");
        } finally {
            if (stored != null)
                prefs.put(key, stored);
            else
                prefs.remove(key);
            prefs.flush();
        }
    }

    @Test
    @Timeout(30)
    public void aFileRoutedToAnUnavailableAdapterFailsTheCommitInsteadOfFallingThrough() throws Exception {
        Path workDir = Files.createTempDirectory("unavailable-adapter-routing");
        Path repoDir = workDir.resolve(".ecco");
        Path content = Files.createDirectories(workDir.resolve("content"));
        Files.writeString(content.resolve("notes.txt"), "text\n");

        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repoDir);
            service.init();
        }
        // as if the repository had been created while GonePlugin was enabled
        Path adaptersFile = repoDir.resolve(".adapters");
        List<String> mappings = new ArrayList<>(Files.readAllLines(adaptersFile));
        mappings.add(0, GONE_PLUGIN + ";**.txt");
        Files.write(adaptersFile, mappings);

        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repoDir);
            service.open();
            service.setBaseDir(content);
            EccoException exception = assertThrows(EccoException.class, () -> service.commit("commit", "A"));
            String messages = "";
            for (Throwable t = exception; t != null; t = t.getCause()) messages += t.getMessage() + "\n";
            assertTrue(messages.contains(GONE_PLUGIN) && messages.contains("notes.txt"),
                    "the error must name the adapter and the file: " + messages);
            assertEquals(0, service.getRepository().getCommits().size());
        }
    }

    @Test
    @Timeout(30)
    public void aMalformedAdaptersLineIsReportedClearly() throws Exception {
        Path workDir = Files.createTempDirectory("malformed-adapters-line");
        Path repoDir = workDir.resolve(".ecco");
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repoDir);
            service.init();
        }
        Path adaptersFile = repoDir.resolve(".adapters");
        List<String> mappings = new ArrayList<>(Files.readAllLines(adaptersFile));
        mappings.add(0, "no-separator-here");
        mappings.add(1, "");
        Files.write(adaptersFile, mappings);

        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repoDir);
            Exception exception = assertThrows(Exception.class, service::open);
            String messages = "";
            for (Throwable t = exception; t != null; t = t.getCause()) messages += t.getMessage() + "\n";
            assertTrue(messages.contains("no-separator-here"), "the error must name the malformed line: " + messages);
        }
    }
}
