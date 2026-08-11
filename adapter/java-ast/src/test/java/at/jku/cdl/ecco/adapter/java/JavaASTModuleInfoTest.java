package at.jku.cdl.ecco.adapter.java;

import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * module-info.java used to silently read as an empty file - cu.getModule() was never looked at, so
 * a whole module declaration (requires/exports/opens/uses/provides) vanished with no error at all
 * (see JavaASTLanguageLevelTest's history). Now reads into a MODULE_DECLARATION node (structured
 * name + open-flag, since the writer needs those back out - see JavaASTModuleData) with one opaque
 * MODULE_DIRECTIVE child per clause, the same granularity IMPORT_DECLARATION already uses.
 */
public class JavaASTModuleInfoTest {

    private final JavaASTReader reader = new JavaASTReader(new SerEntityFactory());
    private final JavaASTWriter writer = new JavaASTWriter();

    private String readWrite(String source) throws IOException {
        Path baseDir = Files.createTempDirectory("java-ast-module-info");
        Files.writeString(baseDir.resolve("module-info.java"), source);

        Set<Node.Op> nodes = reader.read(baseDir, new Path[]{Path.of("module-info.java")});
        Path outDir = Files.createTempDirectory("java-ast-module-info-out");
        writer.write(outDir, Set.copyOf(nodes));

        return Files.readString(outDir.resolve("module-info.java"));
    }

    @Test
    public void roundTripsEveryDirectiveKind() throws IOException {
        String source =
                "module com.example.foo {\n" +
                        "    requires java.base;\n" +
                        "    requires transitive other.module;\n" +
                        "    exports com.example.foo.api;\n" +
                        "    exports com.example.foo.internal to some.other.module;\n" +
                        "    opens com.example.foo.impl;\n" +
                        "    uses com.example.foo.spi.Service;\n" +
                        "    provides com.example.foo.spi.Service with com.example.foo.impl.ServiceImpl;\n" +
                        "}\n";

        assertEquals(source, readWrite(source));
    }

    @Test
    public void roundTripsAnOpenModule() throws IOException {
        String source = "open module com.example.foo {\n    requires java.base;\n}\n";

        assertEquals(source, readWrite(source));
    }
}
