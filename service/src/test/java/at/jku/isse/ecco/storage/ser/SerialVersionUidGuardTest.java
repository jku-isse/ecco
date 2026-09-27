package at.jku.isse.ecco.storage.ser;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Repositories are persisted with Java serialization, so every serializable class that can end up
 * in a repository file (Ser* entities, feature traces, mergers, and every adapter's ArtifactData)
 * must declare an explicit serialVersionUID. Without one the JVM derives it from the class shape,
 * so almost any edit to such a class - adding a method, changing a signature - silently makes every
 * existing repository fail to load with InvalidClassException.
 * <p>
 * The explicit values were pinned to the previously computed defaults, so repositories written
 * before they were added still load. This guard scans every ECCO class on the test classpath
 * (service, base, and the adapters service tests depend on) so a newly added serializable class
 * can't reintroduce the problem. New classes need a UID of their own; existing values must never
 * change unless the persisted format is deliberately broken.
 */
public class SerialVersionUidGuardTest {

    private static final String PACKAGE_DIR = "at/jku/isse/ecco";

    @Test
    public void everySerializableEccoClassDeclaresASerialVersionUid() throws IOException {
        List<String> missing = new ArrayList<>();
        int checked = 0;
        for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
            Path root = Paths.get(entry);
            if (!Files.isDirectory(root.resolve(PACKAGE_DIR))) continue;
            try (Stream<Path> files = Files.walk(root.resolve(PACKAGE_DIR))) {
                for (Path file : (Iterable<Path>) files.filter(f -> f.toString().endsWith(".class"))::iterator) {
                    String className = root.relativize(file).toString()
                            .replace(File.separatorChar, '.')
                            .replaceAll("\\.class$", "");
                    if (className.endsWith("Test") || className.contains("Test$")) continue;
                    Class<?> clazz;
                    try {
                        clazz = Class.forName(className, false, getClass().getClassLoader());
                    } catch (Throwable e) {
                        continue; // optional third-party dependency not on the test classpath
                    }
                    if (clazz.isInterface() || clazz.isEnum() || clazz.isRecord() || clazz.isAnonymousClass()
                            || !Serializable.class.isAssignableFrom(clazz)) continue;
                    checked++;
                    try {
                        int modifiers = clazz.getDeclaredField("serialVersionUID").getModifiers();
                        if (!Modifier.isStatic(modifiers) || !Modifier.isFinal(modifiers))
                            missing.add(className + " (serialVersionUID not static final)");
                    } catch (NoSuchFieldException e) {
                        missing.add(className);
                    }
                }
            }
        }
        assertTrue(checked > 0, "expected to find serializable ECCO classes on the test classpath");
        assertTrue(missing.isEmpty(), "serializable classes without an explicit serialVersionUID: " + missing);
    }
}
