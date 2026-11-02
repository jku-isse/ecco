package at.jku.isse.ecco.test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Starts a main class in a JVM of its own, for the end-to-end tests of the use cases: the
 * command line, the sync server, the REST server, each as a user starts it.
 */
public final class TestJvm {

    private TestJvm() {
    }

    /** The command line that runs the main class with the test's classpath and settings. */
    public static List<String> java(String mainClass, String... args) {
        return java(List.of(), mainClass, args);
    }

    /** ... with JVM options of its own, as a user gives them in JAVA_OPTS (e.g. {@code -Dkey=value}). */
    public static List<String> java(List<String> jvmOptions, String mainClass, String... args) {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("-Xss64m");
        command.add("-Xmx1g");
        // the test JVM's settings: preferences in memory, not the user's (MemoryPreferencesFactory),
        // the checks of unchanged artifacts, the suggestion log in the build directory
        for (String name : System.getProperties().stringPropertyNames())
            if (name.startsWith("ecco.") || name.equals("java.util.prefs.PreferencesFactory"))
                command.add("-D" + name + "=" + System.getProperty(name));
        command.addAll(jvmOptions);
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(mainClass);
        command.addAll(List.of(args));
        return command;
    }

    /** The stream's text, read to its end; for a process's output, read while it runs. */
    public static String read(InputStream stream) {
        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "<" + e + ">";
        }
    }
}
