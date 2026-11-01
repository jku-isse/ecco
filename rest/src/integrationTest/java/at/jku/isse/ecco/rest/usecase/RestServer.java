package at.jku.isse.ecco.rest.usecase;

import at.jku.isse.ecco.rest.authorisation.PasswordHash;
import at.jku.isse.ecco.test.TestJvm;

import java.io.IOException;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * The REST server as it is installed (rest/README.md): {@code at.jku.isse.ecco.rest.Application} in
 * a JVM of its own, with a JWT secret, a users file and a storage directory from the environment -
 * not in development mode, so with none of the demonstration users.
 */
final class RestServer implements AutoCloseable {

    static final String SECRET = "end-to-end-test-secret-0123456789abcdefghijklmnop";

    private final Process process;
    private final CompletableFuture<String> output = new CompletableFuture<>();
    private final int port;

    /** Serves the repositories in {@code storage}; each user is {@code name, password}. Returns once it answers. */
    RestServer(Path storage, Path usersFile, String... namesAndPasswords) throws Exception {
        StringBuilder users = new StringBuilder("# end-to-end test\n");
        for (int i = 0; i < namesAndPasswords.length; i += 2)
            users.append(namesAndPasswords[i]).append(':').append(PasswordHash.hash(namesAndPasswords[i + 1].toCharArray())).append(":User\n");
        Files.writeString(usersFile, users);
        try (ServerSocket free = new ServerSocket(0)) {
            port = free.getLocalPort();
        }

        ProcessBuilder builder = new ProcessBuilder(TestJvm.java("at.jku.isse.ecco.rest.Application"))
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.PIPE);
        builder.environment().remove("ECCO_REST_DEV");
        builder.environment().put("ECCO_STORAGE_DIR", storage.toString());
        builder.environment().put("ECCO_REST_USERS_FILE", usersFile.toString());
        builder.environment().put("JWT_GENERATOR_SIGNATURE_SECRET", SECRET);
        builder.environment().put("PORT", Integer.toString(port));
        process = builder.start();
        process.getOutputStream().close();
        Thread.ofVirtual().start(() -> output.complete(TestJvm.read(process.getInputStream())));

        HttpClient client = HttpClient.newHttpClient();
        long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(1);
        while (true) {
            try {
                client.send(HttpRequest.newBuilder(uri("/api/repository/all")).GET().build(), HttpResponse.BodyHandlers.discarding());
                return;
            } catch (ConnectException e) {
                if (!process.isAlive() || System.nanoTime() > deadline) {
                    process.destroyForcibly();
                    throw new AssertionError("the REST server did not start\n" + output.join(), e);
                }
                Thread.sleep(100);
            }
        }
    }

    URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    /** The server's log so far is only complete once it has stopped. */
    String log() {
        return output.getNow("<the server is running>");
    }

    @Override
    public void close() throws IOException, InterruptedException {
        process.destroy();
        if (!process.waitFor(1, TimeUnit.MINUTES))
            process.destroyForcibly();
        output.join();
    }
}
