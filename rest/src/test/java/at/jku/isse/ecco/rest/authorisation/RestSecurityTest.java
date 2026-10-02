package at.jku.isse.ecco.rest.authorisation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The server ran with five hard-coded users with plaintext passwords and a published default JWT
 * secret. Outside development mode it now refuses to start without a real secret and a users file
 * holding password hashes.
 */
public class RestSecurityTest {

    private static final String GOOD_SECRET = "0123456789abcdef0123456789abcdef-x";

    @Test
    public void refusesToStartWithTheDemonstrationSetupOutsideDevelopmentMode(@TempDir Path tmp) throws IOException {
        Path users = usersFile(tmp);
        assertRefused(null, Optional.of(users), "no JWT signing secret");
        assertRefused(RestSecurity.DEFAULT_SECRET, Optional.of(users), "published default");
        assertRefused("too-short", Optional.of(users), "shorter than 32");
        assertRefused(GOOD_SECRET, Optional.empty(), "no users file");
    }

    @Test
    public void startsWithARealSecretAndAUsersFile(@TempDir Path tmp) throws IOException {
        RestSecurity security = new RestSecurity(GOOD_SECRET, false, Optional.of(usersFile(tmp)));
        assertTrue(security.findUser("alice").orElseThrow().passwordMatches("s3cret"));
        assertFalse(security.findUser("alice").orElseThrow().passwordMatches("admin"));
        assertEquals(java.util.List.of("Admin", "User"), security.findUser("alice").orElseThrow().getRoles());
        assertTrue(security.findUser("Tobias").isEmpty(), "the demonstration users must not exist");
    }

    @Test
    public void developmentModeKeepsTheDemonstrationUsers() {
        RestSecurity security = new RestSecurity(RestSecurity.DEFAULT_SECRET, true, Optional.empty());
        assertTrue(security.findUser("Tobias").orElseThrow().passwordMatches("admin"));
    }

    @Test
    public void anInvalidUsersFileIsReportedWithItsLine(@TempDir Path tmp) throws IOException {
        Path file = Files.writeString(tmp.resolve("users"), "# comment\n\nbob:plaintext:User\n");
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> new RestSecurity(GOOD_SECRET, false, Optional.of(file)));
        assertTrue(e.getMessage().contains("Line 3"), e.getMessage());
    }

    @Test
    public void passwordHashesAreSaltedAndVerifiable() {
        String a = PasswordHash.hash("pw".toCharArray(), 1_000);
        String b = PasswordHash.hash("pw".toCharArray(), 1_000);
        assertNotEquals(a, b);
        assertTrue(PasswordHash.matches("pw".toCharArray(), a));
        assertFalse(PasswordHash.matches("pW".toCharArray(), a));
    }

    private static void assertRefused(String secret, Optional<Path> users, String reason) {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> new RestSecurity(secret, false, users));
        assertTrue(e.getMessage().contains(reason), e.getMessage());
    }

    private static Path usersFile(Path dir) throws IOException {
        return Files.writeString(dir.resolve("users"), "# test users\nalice:" + PasswordHash.hash("s3cret".toCharArray(), 1_000) + ":Admin,User\n");
    }
}
