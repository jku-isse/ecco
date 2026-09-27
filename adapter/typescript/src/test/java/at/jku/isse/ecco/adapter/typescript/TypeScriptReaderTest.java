package at.jku.isse.ecco.adapter.typescript;

import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TypeScriptParser located the TypeScript compiler at
 * "<working directory>/../adapter/typescript/src/main/resources/script/node_modules/typescript" -
 * which only exists when the JVM runs one level below a source checkout's root (e.g. `gradle
 * :ecco-gui:run`), not in the packaged app, the CLI, or this module's own tests - and it wrote
 * parse.js into the working directory. Both now come from the classpath (extracted to a temp
 * directory when running from a jar). Also, executing parse.js as a file (getExecutor(File)) moved
 * the process's native working directory to the script folder for the rest of the JVM's life.
 */
public class TypeScriptReaderTest {

    @Test
    @Timeout(120)
    public void aTypeScriptFileIsParsedIndependentlyOfTheWorkingDirectory() throws Exception {
        Path base = Files.createTempDirectory("typescript-reader");
        Files.writeString(base.resolve("main.ts"), "let x: number = 1;\nfunction f(a: string) {\n  return a;\n}\n");
        EntityFactory entityFactory = new SerEntityFactory();
        Path workingDirectory = Paths.get("").toAbsolutePath();

        Set<Node.Op> nodes = new TypeScriptReader(entityFactory).read(base, new Path[]{Paths.get("main.ts")});

        assertEquals(1, nodes.size());
        assertFalse(nodes.iterator().next().getChildren().isEmpty(), "the file must have been parsed into child nodes");
        assertFalse(Files.exists(workingDirectory.resolve("parse.js")), "nothing may be written into the working directory");
        // relative paths are resolved by the OS against the process's native working directory,
        // which executing parse.js as a file used to move to the script folder
        assertTrue(Files.exists(Paths.get("build.gradle")), "the process's working directory must not change");
    }
}
