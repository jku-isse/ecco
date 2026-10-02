package at.jku.isse.ecco.rest;

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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The same as RestTokenSecurityTest, but with the shipped application.yml unchanged: its JWT secret
 * key was misspelled for Micronaut, so tokens went out unsigned and unsigned tokens were accepted.
 */
@MicronautTest
class RestShippedConfigTokenTest {

    @MockBean(FileRepositoryService.class)
    FileRepositoryService repositoryService() throws IOException {
        return new FileRepositoryService(Files.createTempDirectory("ecco-rest-shipped-config-test"));
    }

    @Inject
    @Client("/")
    HttpClient client;

    @Test
    void theShippedConfigurationSignsTokensAndRefusesUnsignedOnes() {
        String token = client.toBlocking().retrieve(HttpRequest.POST("/login", new UsernamePasswordCredentials("Tobias", "admin")),
                BearerAccessRefreshToken.class).getAccessToken();
        String[] parts = token.split("\\.");
        assertEquals(3, parts.length, "the token must carry a signature");
        assertTrue(new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8).contains("HS256"));

        String unsigned = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8))
                + "." + parts[1] + ".";
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> client.toBlocking()
                .exchange(HttpRequest.GET("/api/repository/all").header("Authorization", "Bearer " + unsigned), String.class));
        assertEquals(HttpStatus.UNAUTHORIZED, e.getStatus());
    }
}
