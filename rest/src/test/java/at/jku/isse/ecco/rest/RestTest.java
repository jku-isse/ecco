package at.jku.isse.ecco.rest;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.EmbeddedApplication;
import io.micronaut.security.authentication.UsernamePasswordCredentials;
import io.micronaut.security.token.render.BearerAccessRefreshToken;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
class RestTest {

    // repositories created through the API go here instead of the real storage under examples/
    private static final Path REPO_STORAGE;

    static {
        try {
            REPO_STORAGE = Files.createTempDirectory("ecco-rest-test");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @MockBean(FileRepositoryService.class)
    FileRepositoryService repositoryService() {
        return new FileRepositoryService(REPO_STORAGE);
    }

    @AfterAll
    static void deleteRepoStorage() throws IOException {
        try (Stream<Path> paths = Files.walk(REPO_STORAGE)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
                Files.delete(path);
        }
    }

    @Inject
    EmbeddedApplication<?> application;

    @Inject
    @Client("/")
    HttpClient client;

    BearerAccessRefreshToken bearerAccessRefreshToken;

    @Test
    void TestsAreRunning() {
        System.out.println("Rest-Test is accessed");
    }


    @Test
    void serverIsRunning() {
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

    /*
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
     */

    @Test
    void createNewRepo() {
        String NEW_REPO = "newTestRepro";
        try {
            String repros = client.toBlocking().retrieve(HttpRequest.PUT("/api/repository/" + NEW_REPO, null)
                    .header("Authorization", "Bearer " + bearerAccessRefreshToken.getAccessToken()), String.class);
            assertTrue(repros.contains(NEW_REPO));
            System.out.println("TBE: returned all repos including the new one");
        } catch (HttpClientResponseException e) {
            System.out.println("-----TBE-------");
            System.out.println(e.getStatus());
            System.out.println(e.getMessage());
            System.out.println(e.getResponse().body().toString());
            System.out.println("------------");
        }
    }
}
