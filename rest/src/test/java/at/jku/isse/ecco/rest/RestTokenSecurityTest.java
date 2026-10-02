package at.jku.isse.ecco.rest;

import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.security.authentication.UsernamePasswordCredentials;
import io.micronaut.security.token.render.BearerAccessRefreshToken;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * application.yml set the JWT secret under a key Micronaut does not read
 * (signatures.secret instead of signatures.secret.generator.secret), so no secret was configured:
 * the server issued unsigned tokens (alg "none") and accepted an unsigned token for any user name,
 * without a password. Tokens are now signed, and unsigned or wrongly signed tokens are refused.
 */
@MicronautTest
@Property(name = "micronaut.security.token.jwt.signatures.secret.generator.secret", value = RestTokenSecurityTest.SECRET)
class RestTokenSecurityTest {

    static final String SECRET = "a-test-secret-that-is-longer-than-32-characters";

    @MockBean(FileRepositoryService.class)
    FileRepositoryService repositoryService() throws IOException {
        return new FileRepositoryService(Files.createTempDirectory("ecco-rest-token-test"));
    }

    @Inject
    @Client("/")
    HttpClient client;

    @Test
    void issuedTokensAreSigned() {
        String token = login("Tobias", "admin").getAccessToken();
        String[] parts = token.split("\\.");
        assertEquals(3, parts.length);
        assertTrue(new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8).contains("HS256"));
        assertEquals(HttpStatus.OK, get(token));
    }

    @Test
    void anUnsignedTokenIsRefused() {
        String token = login("Tobias", "admin").getAccessToken();
        String claims = token.split("\\.")[1];
        String unsigned = b64("{\"alg\":\"none\"}") + "." + claims + ".";
        assertEquals(HttpStatus.UNAUTHORIZED, get(unsigned));
    }

    @Test
    void aTokenSignedWithAnotherSecretIsRefused() throws Exception {
        String header = b64("{\"alg\":\"HS256\"}");
        String claims = b64("{\"sub\":\"Mallory\",\"exp\":9999999999,\"roles\":[\"Admin\"]}");
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("pleaseChangeThisSecretForANewOne".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal((header + "." + claims).getBytes(StandardCharsets.UTF_8)));
        assertEquals(HttpStatus.UNAUTHORIZED, get(header + "." + claims + "." + signature));
    }

    @Test
    void aWrongPasswordAndAnUnknownUserAreRefused() {
        HttpClientResponseException wrong = assertThrows(HttpClientResponseException.class, () -> login("Tobias", "wrong"));
        HttpClientResponseException unknown = assertThrows(HttpClientResponseException.class, () -> login("Nobody", "admin"));
        assertEquals(HttpStatus.UNAUTHORIZED, wrong.getStatus());
        assertEquals(HttpStatus.UNAUTHORIZED, unknown.getStatus());
    }

    private BearerAccessRefreshToken login(String name, String password) {
        return client.toBlocking().retrieve(HttpRequest.POST("/login", new UsernamePasswordCredentials(name, password)), BearerAccessRefreshToken.class);
    }

    private HttpStatus get(String token) {
        try {
            return client.toBlocking().exchange(HttpRequest.GET("/api/repository/all").header("Authorization", "Bearer " + token), String.class).getStatus();
        } catch (HttpClientResponseException e) {
            return e.getStatus();
        }
    }

    private static String b64(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
