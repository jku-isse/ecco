package at.jku.isse.ecco.rest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Where the server keeps its repositories could only be changed by editing Settings: it was guessed
 * from Docker, a "jenkins" user, or the source tree's examples folder. ECCO_STORAGE_DIR (or the
 * system property ecco.storage-dir, which wins) now chooses it; the guess remains the fallback.
 */
public class SettingsTest {

    @Test
    public void theSystemPropertyWinsOverTheEnvironmentVariable(@TempDir Path tmp) {
        Path property = tmp.resolve("property");
        assertEquals(property.toString(), Settings.storageLocation(property.toString(), tmp.resolve("env").toString()));
    }

    @Test
    public void theEnvironmentVariableIsUsedWithoutTheProperty(@TempDir Path tmp) {
        Path env = tmp.resolve("env");
        assertEquals(env.toString(), Settings.storageLocation(null, env.toString()));
        assertEquals(env.toString(), Settings.storageLocation("  ", env.toString()));
    }

    @Test
    public void aRelativeDirectoryIsResolvedAgainstTheWorkingDirectory() {
        assertEquals(Path.of("repos").toAbsolutePath().normalize().toString(), Settings.storageLocation(null, "repos"));
    }

    @Test
    public void withoutConfigurationTheGuessIsUsed(@TempDir Path tmp) {
        String guessed = Settings.storageLocation(null, null);
        assertEquals(guessed, Settings.storageLocation("", ""));
        assertNotEquals(tmp.toString(), guessed);
    }
}
