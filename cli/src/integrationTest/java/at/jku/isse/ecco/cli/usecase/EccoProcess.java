package at.jku.isse.ecco.cli.usecase;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Runs the CLI as a user does: {@code ecco <args>} in a directory, in a JVM of its own. Nothing a
 * command leaves in memory - a cache, an open repository, a static field - reaches the next one, so
 * every step reads the repository from disk.
 */
final class EccoProcess {

    record Result(int exitCode, String out, String err) {
        @Override
        public String toString() {
            return "exit " + exitCode + "\n--- stdout\n" + out + "--- stderr\n" + err;
        }
    }

    private EccoProcess() {
    }

    static Result run(Path dir, String... args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("-Xss64m");
        command.add("-Xmx1g");
        // the test JVM's settings: preferences in memory, not the user's (MemoryPreferencesFactory),
        // the checks of unchanged artifacts, the suggestion log in the build directory
        for (String name : System.getProperties().stringPropertyNames())
            if (name.startsWith("ecco.") || name.equals("java.util.prefs.PreferencesFactory"))
                command.add("-D" + name + "=" + System.getProperty(name));
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add("at.jku.isse.ecco.cli.Main");
        command.addAll(List.of(args));

        Process process = new ProcessBuilder(command)
                .directory(dir.toFile())
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectError(ProcessBuilder.Redirect.PIPE)
                .start();
        process.getOutputStream().close();
        // read both streams while the process runs: a full pipe would block it
        var out = new java.util.concurrent.CompletableFuture<String>();
        var err = new java.util.concurrent.CompletableFuture<String>();
        Thread.ofVirtual().start(() -> out.complete(read(process.getInputStream())));
        Thread.ofVirtual().start(() -> err.complete(read(process.getErrorStream())));
        if (!process.waitFor(5, TimeUnit.MINUTES)) {
            process.destroyForcibly();
            throw new AssertionError("ecco " + String.join(" ", args) + " did not finish within 5 minutes");
        }
        return new Result(process.exitValue(), out.join(), err.join());
    }

    private static String read(java.io.InputStream stream) {
        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "<" + e + ">";
        }
    }
}
