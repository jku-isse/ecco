package at.jku.isse.ecco.rest.usecase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static at.jku.isse.ecco.test.Shop.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * UC-14 Commit through the web client and UC-15 Download variant through the web client
 * (doc/requirements.md): the requests the web client sends, over HTTP to the REST server as it is
 * installed - in a JVM of its own with a users file and a JWT secret ({@link RestServer}). The
 * steps in the comments are those of the use cases' flows.
 * <p>
 * The oracle is the files: a variant downloaded holds the files uploaded for its configuration byte
 * for byte, and one never committed as such their merge. A refused request leaves the repository
 * as it was, byte for byte, and so does a download. The repository is read again by a restarted
 * server.
 */
public class WebClientEndToEndTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    @Timeout(600)
    public void aWebClientUserCommitsAndDownloadsVariants(@TempDir Path tmp) throws Exception {
        Path storage = Files.createDirectories(tmp.resolve("storage"));
        Path users = tmp.resolve("users");
        Path ecco = storage.resolve("shop").resolve(".ecco");

        try (RestServer server = new RestServer(storage, users, "alice", "alice's password")) {
            // UC-14 SA1: a wrong password gets no token
            HttpResponse<String> refused = login(server, "alice", "wrong");
            assertEquals(401, refused.statusCode(), refused.body());
            assertFalse(refused.body().contains("access_token"), refused.body());

            // UC-14 B1-B3: a signed bearer token
            String token = token(login(server, "alice", "alice's password"));

            // UC-14 GA1: no token, or one not signed with the server's secret, is refused
            assertEquals(401, get(server, null, "/api/repository/all").statusCode());
            assertEquals(401, get(server, forged(token), "/api/repository/all").statusCode());

            JsonNode repositories = json(send(server, token, "PUT", "/api/repository/shop", HttpRequest.BodyPublishers.noBody()));
            assertEquals(1, repositories.size(), repositories.toString());
            int id = repositories.get(0).get("repositoryHandlerId").asInt();

            // UC-14 B4-B10, three times: the variant's files with a configuration
            commit(server, token, id, BASE, "BASE");
            commit(server, token, id, BASE_SEARCH, "BASE, search");
            JsonNode repository = commit(server, token, id, BASE_EXPORT, "BASE, export");
            assertEquals(3, repository.get("commits").size(), repository.toString());

            // UC-14 SA2-SA4: an unknown repository, a configuration error, a file name outside the
            // upload folder - refused, nothing written
            Map<String, byte[]> before = tree(ecco);
            assertEquals(404, upload(server, token, id + 1, BASE, "BASE", "/").statusCode());
            assertEquals(400, upload(server, token, id, BASE, "BASE, bad name", "/").statusCode());
            assertEquals(400, upload(server, token, id, BASE, "", "/").statusCode());
            HttpResponse<String> escape = upload(server, token, id, Map.of("../../escape.txt", BASE.get("README.md")), "BASE", "/");
            assertEquals(400, escape.statusCode(), escape.body());
            assertTrue(escape.body().contains("Invalid path"), escape.body());
            // UC-14 GA1 for the upload
            assertEquals(401, upload(server, null, id, BASE, "BASE", "/").statusCode());
            assertSameFiles(before, tree(ecco));
            try (Stream<Path> files = Files.walk(tmp)) {
                assertTrue(files.noneMatch(path -> path.getFileName().toString().equals("escape.txt")));
            }

            // UC-15 B1-B7: variants downloaded as zip files - those the commits recorded (FR-M4) as
            // they were uploaded, one named for a combination never committed as such composed from them
            assertEquals(3, repository.get("variants").size(), repository.toString());
            repository = variant(server, token, id, "both", "BASE, search, export");
            assertEquals("both", repository.get("variants").get(3).get("name").asText(), repository.toString());
            before = tree(ecco);
            assertTrue(download(server, token, id, variantId(repository, "BASE"), BASE).isEmpty());
            assertTrue(download(server, token, id, variantId(repository, "BASE", "search"), BASE_SEARCH).isEmpty());
            assertTrue(download(server, token, id, variantId(repository, "BASE", "export"), BASE_EXPORT).isEmpty());
            String warnings = download(server, token, id, variantId(repository, "BASE", "search", "export"), BASE_SEARCH_EXPORT);
            assertTrue(warnings.contains("MISSING: export + search"), warnings);
            // UC-15 postcondition: the repository unchanged, the checkout folders deleted
            assertSameFiles(before, tree(ecco));
            try (Stream<Path> files = Files.list(ecco.getParent())) {
                assertTrue(files.noneMatch(path -> path.getFileName().toString().startsWith("checkout") && Files.isDirectory(path)));
            }

            // UC-15 SA1 and GA1: an unknown repository, no token
            assertEquals(404, get(server, token, "/api/" + (id + 1) + "/variant/" + variantId(repository, "BASE") + "/checkout").statusCode());
            assertEquals(401, get(server, null, "/api/" + id + "/variant/" + variantId(repository, "BASE") + "/checkout").statusCode());
        }

        // UC-14 postcondition: the repository holds the commits - read by a server started again
        try (RestServer server = new RestServer(storage, users, "alice", "alice's password")) {
            String token = token(login(server, "alice", "alice's password"));
            JsonNode repositories = json(get(server, token, "/api/repository/all"));
            int id = repositories.get(0).get("repositoryHandlerId").asInt();
            JsonNode repository = json(get(server, token, "/api/repository/" + id));
            assertEquals(3, repository.get("commits").size(), repository.toString());
            assertTrue(download(server, token, id, variantId(repository, "BASE", "search"), BASE_SEARCH).isEmpty());
            assertTrue(download(server, token, id, variantId(repository, "BASE", "search", "export"), BASE_SEARCH_EXPORT).contains("MISSING: export + search"));
        }
    }

    private HttpResponse<String> login(RestServer server, String name, String password) throws Exception {
        String body = JSON.writeValueAsString(Map.of("username", name, "password", password));
        return http.send(HttpRequest.newBuilder(server.uri("/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String token(HttpResponse<String> login) throws IOException {
        assertEquals(200, login.statusCode(), login.body());
        return JSON.readTree(login.body()).get("access_token").asText();
    }

    /** The token with the signature of another secret: the same header and claims. */
    private static String forged(String token) throws Exception {
        String unsigned = token.substring(0, token.lastIndexOf('.'));
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec("another-secret-of-at-least-32-characters".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signature = mac.doFinal(unsigned.getBytes(StandardCharsets.US_ASCII));
        return unsigned + "." + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
    }

    private HttpResponse<String> get(RestServer server, String token, String path) throws Exception {
        return send(server, token, "GET", path, HttpRequest.BodyPublishers.noBody());
    }

    private HttpResponse<String> send(RestServer server, String token, String method, String path, HttpRequest.BodyPublisher body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(server.uri(path)).method(method, body);
        if (token != null)
            request.header("Authorization", "Bearer " + token);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static JsonNode json(HttpResponse<String> response) throws IOException {
        assertEquals(200, response.statusCode(), response.body());
        return JSON.readTree(response.body());
    }

    private JsonNode commit(RestServer server, String token, int id, Map<String, byte[]> variant, String configuration) throws Exception {
        return json(upload(server, token, id, variant, configuration, "/"));
    }

    /**
     * {@code POST /api/{id}/commit/add} as the web client sends it: one {@code file} part per file,
     * its name after a leading separator, and the message, configuration and user name.
     */
    private HttpResponse<String> upload(RestServer server, String token, int id, Map<String, byte[]> files, String configuration, String separator) throws Exception {
        String boundary = "ecco-" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (Map.Entry<String, byte[]> file : files.entrySet()) {
            part(body, boundary, "Content-Disposition: form-data; name=\"file\"; filename=\"" + separator + file.getKey() + "\"\r\nContent-Type: application/octet-stream");
            body.write(file.getValue());
            body.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        for (Map.Entry<String, String> field : Map.of("message", "uploaded", "config", configuration, "username", "alice").entrySet()) {
            part(body, boundary, "Content-Disposition: form-data; name=\"" + field.getKey() + "\"");
            body.write((field.getValue() + "\r\n").getBytes(StandardCharsets.UTF_8));
        }
        body.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpRequest.Builder request = HttpRequest.newBuilder(server.uri("/api/" + id + "/commit/add"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()));
        if (token != null)
            request.header("Authorization", "Bearer " + token);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static void part(ByteArrayOutputStream body, String boundary, String headers) throws IOException {
        body.write(("--" + boundary + "\r\n" + headers + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
    }

    private JsonNode variant(RestServer server, String token, int id, String name, String configuration) throws Exception {
        String body = JSON.writeValueAsString(Map.of("configuration", configuration, "description", name));
        HttpRequest request = HttpRequest.newBuilder(server.uri("/api/" + id + "/variant/" + name))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body)).build();
        return json(http.send(request, HttpResponse.BodyHandlers.ofString()));
    }

    /** The id of the variant whose configuration has these features. */
    private static String variantId(JsonNode repository, String... features) {
        java.util.Set<String> wanted = java.util.Set.of(features);
        for (JsonNode variant : repository.get("variants")) {
            java.util.Set<String> names = new java.util.HashSet<>();
            for (JsonNode revision : variant.get("configuration").get("featureRevisions"))
                names.add(revision.get("featureName").asText());
            if (names.equals(wanted))
                return variant.get("id").asText();
        }
        throw new AssertionError("no variant of " + wanted + " in " + repository);
    }

    /** UC-15: the zip holds the variant's files and {@code .config} and {@code .warnings}; returns the warnings. */
    private String download(RestServer server, String token, int id, String variantId, Map<String, byte[]> expected) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(server.uri("/api/" + id + "/variant/" + variantId + "/checkout"))
                .header("Authorization", "Bearer " + token).GET().build();
        HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, response.statusCode(), () -> new String(response.body(), StandardCharsets.UTF_8));

        Map<String, byte[]> files = new TreeMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(response.body()))) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null; )
                files.put(entry.getName().replace('\\', '/'), zip.readAllBytes());
        }
        byte[] config = files.remove(".config");
        byte[] warnings = files.remove(".warnings");
        assertNotNull(config, files.keySet().toString());
        assertNotNull(warnings, files.keySet().toString());
        files.remove(".hashes");
        assertSameFiles(expected, files);
        return new String(warnings, StandardCharsets.UTF_8).strip();
    }

    private static Map<String, byte[]> tree(Path root) throws IOException {
        Map<String, byte[]> files = new TreeMap<>();
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path path : walk.filter(Files::isRegularFile).toList())
                files.put(root.relativize(path).toString().replace('\\', '/'), Files.readAllBytes(path));
        }
        return files;
    }

    private static void assertSameFiles(Map<String, byte[]> expected, Map<String, byte[]> actual) {
        assertEquals(expected.keySet(), actual.keySet());
        for (String path : expected.keySet())
            assertArrayEquals(expected.get(path), actual.get(path), () -> path + " differs:\n"
                    + new String(actual.get(path), StandardCharsets.UTF_8));
    }
}
