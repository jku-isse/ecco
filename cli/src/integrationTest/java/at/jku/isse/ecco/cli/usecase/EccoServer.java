package at.jku.isse.ecco.cli.usecase;

import at.jku.isse.ecco.test.TestJvm;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * A sync server (UC-13) serving a repository from a JVM of its own ({@link ServerMain}), as the
 * collaborator's GUI does while others fork, pull and push over the socket.
 */
final class EccoServer implements AutoCloseable {

    private final Process process;
    private final PrintStream in;
    private final BlockingQueue<String> lines = new LinkedBlockingQueue<>();
    private final CompletableFuture<String> err = new CompletableFuture<>();
    private final List<String> events = new ArrayList<>();
    private final int port;

    /** Serves the repository in {@code dir} on the port, 0 for any; returns once it listens. */
    EccoServer(Path dir, int port) throws IOException, InterruptedException {
        process = new ProcessBuilder(TestJvm.java(ServerMain.class.getName(), dir.toString(), Integer.toString(port)))
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectError(ProcessBuilder.Redirect.PIPE)
                .start();
        in = new PrintStream(process.getOutputStream(), true, StandardCharsets.UTF_8);
        Thread.ofVirtual().start(() -> {
            try (BufferedReader out = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                for (String line = out.readLine(); line != null; line = out.readLine())
                    lines.add(line);
            } catch (IOException ignored) {
                // the process ended
            }
        });
        Thread.ofVirtual().start(() -> err.complete(TestJvm.read(process.getErrorStream())));
        String line = next("PORT ");
        this.port = Integer.parseInt(line.substring("PORT ".length()));
    }

    int port() {
        return port;
    }

    String address() {
        return "localhost:" + port;
    }

    /** UC-13 SA1: starts the server again while it runs; returns the server's answer. */
    String startAgain() throws InterruptedException {
        in.println("start");
        return next("REFUSED ", "STARTED AGAIN");
    }

    /** The server log so far (UC-13 B9): the messages the GUI's server section shows. */
    List<String> events() {
        for (String line; (line = lines.poll()) != null; )
            record(line);
        return List.copyOf(events);
    }

    /** UC-13 B10-B11: stops the server and waits for its JVM to end. */
    @Override
    public void close() throws InterruptedException {
        if (!process.isAlive())
            return;
        in.println("stop");
        in.close();
        try {
            next("STOPPED");
            if (!process.waitFor(1, TimeUnit.MINUTES))
                throw new AssertionError("the server did not end");
            if (process.exitValue() != 0)
                throw new AssertionError("the server ended with " + process.exitValue() + "\n" + err.join());
        } finally {
            process.destroyForcibly();
        }
    }

    /** The next line that starts with one of the prefixes; server events on the way are recorded. */
    private String next(String... prefixes) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(1);
        while (true) {
            String line = lines.poll(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            if (line == null) {
                process.destroyForcibly();
                throw new AssertionError("the server did not answer " + List.of(prefixes) + "\n" + events + "\n" + err.join());
            }
            for (String prefix : prefixes)
                if (line.startsWith(prefix))
                    return line;
            record(line);
        }
    }

    private void record(String line) {
        if (line.startsWith("EVENT "))
            events.add(line.substring("EVENT ".length()));
    }
}
