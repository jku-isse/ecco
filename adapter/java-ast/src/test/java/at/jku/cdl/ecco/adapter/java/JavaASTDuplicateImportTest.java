package at.jku.cdl.ecco.adapter.java;

import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Valid Java that repeats an import (ECCO's own TextFileFormat had two): the imports are an
 * unordered set of children, so the read failed with "An equivalent child is already contained" and
 * the file was committed by the text adapter instead. Two identical initializer blocks in one class
 * still fail the same way - each one runs, so they cannot be read once; such a file goes to the
 * text adapter.
 */
public class JavaASTDuplicateImportTest {

    private static String roundTrip(String source) throws IOException {
        Path base = Files.createTempDirectory("java-ast-duplicates");
        Files.writeString(base.resolve("F.java"), source);
        Set<Node.Op> nodes = new JavaASTReader(new SerEntityFactory()).read(base, new Path[]{Path.of("F.java")});
        Path out = Files.createTempDirectory("java-ast-duplicates-out");
        new JavaASTWriter().write(out, Set.copyOf(nodes));
        return Files.readString(out.resolve("F.java"));
    }

    private static int count(String text, String part) {
        return text.split(java.util.regex.Pattern.quote(part), -1).length - 1;
    }

    @Test
    public void anImportWrittenTwiceIsReadOnce() throws IOException {
        String written = roundTrip("package p;\n\nimport java.util.List;\nimport java.util.Map;\nimport static java.lang.Math.max;\n\n"
                + "import java.util.List;\nimport static java.lang.Math.max;\n\nclass C {\n    List<Map<String, Integer>> xs;\n}\n");
        assertEquals(1, count(written, "import java.util.List;"), written);
        assertEquals(1, count(written, "import static java.lang.Math.max;"), written);
        assertTrue(written.contains("import java.util.Map;"), written);
    }
}
