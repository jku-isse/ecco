package at.jku.isse.ecco.rest;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
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

    @BeforeEach
    void testAuthenticatedCanFetchUsername() {
        UsernamePasswordCredentials credentials = new UsernamePasswordCredentials("Tobias", "admin");
        HttpRequest<?> request = HttpRequest.POST("/login", credentials);

        try {
            bearerAccessRefreshToken = client.toBlocking().retrieve(request, BearerAccessRefreshToken.class);
            System.out.println("TBE: returned from login without failure");
        } catch (HttpClientResponseException e) {
            System.out.println("------------");
            System.out.println(e.getStatus());
            System.out.println(e.getMessage());
            System.out.println("------------");
            throw e;
        }
    }

    @Test
    void checkRepositories() {
        try {
            String repros = client.toBlocking().retrieve(HttpRequest.GET("/api/repository/all")
                    .header("Authorization", "Bearer " + bearerAccessRefreshToken.getAccessToken()), String.class);
            assertTrue(repros.contains("BigHistory_full"));
            assertTrue(repros.contains("ImageVariants"));
            System.out.println("TBE: returned from all repositories without failure");
        } catch (HttpClientResponseException e) {
            System.out.println("-----TBE-------");
            System.out.println(e.getStatus());
            System.out.println(e.getMessage());
            System.out.println(e.getResponse().body().toString());
            System.out.println("------------");
        }
    }
}
