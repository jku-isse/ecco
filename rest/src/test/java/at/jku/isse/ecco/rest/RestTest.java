package at.jku.isse.ecco.rest;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.runtime.EmbeddedApplication;
import io.micronaut.security.authentication.UsernamePasswordCredentials;
import io.micronaut.security.token.render.BearerAccessRefreshToken;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
class RestTest {

    @Inject
    EmbeddedApplication<?> application;

    @Inject
    @Client("/")
    HttpClient client;

    BearerAccessRefreshToken bearerAccessRefreshToken;

    @Test
    void test() {
        System.out.println("Rest-Test is accessed");
    }


    @Test
    void testItWorks() {
        assertTrue(application.isRunning());
        System.out.println("Application is running");
    }
    @Test
    @BeforeEach
    void testAuthenticatedCanFetchUsername() {
        UsernamePasswordCredentials credentials = new UsernamePasswordCredentials("Tobias", "admin");
        HttpRequest<?> request = HttpRequest.POST("/login", credentials);

        bearerAccessRefreshToken = client.toBlocking().retrieve(request, BearerAccessRefreshToken.class);

        String repros = client.toBlocking().retrieve(HttpRequest.GET("/api/repository/all")
                .header("Authorization", "Bearer " + bearerAccessRefreshToken.getAccessToken()), String.class);
    }

    @Test
    void checkRepositories() {
       String repros = client.toBlocking().retrieve(HttpRequest.GET("/api/repository/all")
                .header("Authorization", "Bearer " + bearerAccessRefreshToken.getAccessToken()), String.class);

        assertTrue(repros.contains("BigHistory_full"));
        assertTrue(repros.contains("ImageVariants"));
    }
}
