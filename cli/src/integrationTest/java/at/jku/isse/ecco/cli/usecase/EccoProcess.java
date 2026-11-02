package at.jku.isse.ecco.cli.usecase;

import at.jku.isse.ecco.test.TestJvm;

import java.io.IOException;
import java.nio.file.Path;
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
        return run(java.util.List.of(), dir, args);
    }

    /** With JVM options, as a user gives them in JAVA_OPTS (e.g. {@code -Decco.lilypond.musicalTokens=false}). */
    static Result run(java.util.List<String> jvmOptions, Path dir, String... args) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(TestJvm.java(jvmOptions, "at.jku.isse.ecco.cli.Main", args))
                .directory(dir.toFile())
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectError(ProcessBuilder.Redirect.PIPE)
                .start();
        process.getOutputStream().close();
        // read both streams while the process runs: a full pipe would block it
        var out = new java.util.concurrent.CompletableFuture<String>();
        var err = new java.util.concurrent.CompletableFuture<String>();
        Thread.ofVirtual().start(() -> out.complete(TestJvm.read(process.getInputStream())));
        Thread.ofVirtual().start(() -> err.complete(TestJvm.read(process.getErrorStream())));
        if (!process.waitFor(5, TimeUnit.MINUTES)) {
            process.destroyForcibly();
            throw new AssertionError("ecco " + String.join(" ", args) + " did not finish within 5 minutes");
        }
        return new Result(process.exitValue(), out.join(), err.join());
    }

}
