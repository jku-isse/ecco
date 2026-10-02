package at.jku.cdl.ecco.adapter.java;

import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.tree.Node;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.printer.configuration.PrettyPrinterConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Files that could be committed but not checked out - the writer failed to parse parts of them
 * again (11% of this repository's own sources): nested types with private/protected/static
 * modifiers, try-with-resources statements, and multi-catch clauses. Written back, their code must
 * be the same.
 */
public class JavaASTCheckoutCrashTest {

    private static final JavaParser PARSER = new JavaParser(new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21));
    private static final PrettyPrinterConfiguration NO_COMMENTS = new PrettyPrinterConfiguration().setPrintComments(false);

    private static void assertSameCodeAfterRoundTrip(String source) throws IOException {
        Path base = Files.createTempDirectory("java-ast-crash");
        Files.writeString(base.resolve("F.java"), source);
        Set<Node.Op> nodes = new JavaASTReader(new SerEntityFactory()).read(base, new Path[]{Path.of("F.java")});
        Path out = Files.createTempDirectory("java-ast-crash-out");
        new JavaASTWriter().write(out, Set.copyOf(nodes));
        String written = Files.readString(out.resolve("F.java"));
        assertEquals(PARSER.parse(source).getResult().orElseThrow().toString(NO_COMMENTS),
                PARSER.parse(written).getResult().orElseThrow().toString(NO_COMMENTS));
    }

    @Test
    public void nestedTypesWithModifiers() throws IOException {
        assertSameCodeAfterRoundTrip("public class Outer {\n"
                + "    private static final class Inner {\n        int x;\n    }\n"
                + "    protected interface Callback {\n        void call();\n    }\n"
                + "    static enum Mode {\n        A, B\n    }\n"
                + "    private record Point(int x, int y) {\n    }\n"
                + "}\n");
    }

    @Test
    public void tryWithResources() throws IOException {
        assertSameCodeAfterRoundTrip("import java.io.*;\n\npublic class R {\n"
                + "    void f(Reader in) throws IOException {\n"
                + "        try (BufferedReader br = new BufferedReader(in); var w = new StringWriter()) {\n            w.write(br.readLine());\n        }\n"
                + "        try (in) {\n            in.read();\n        }\n"
                + "    }\n}\n");
    }

    @Test
    public void multiCatch() throws IOException {
        assertSameCodeAfterRoundTrip("public class M {\n"
                + "    void f() {\n"
                + "        try {\n            g();\n        } catch (IllegalStateException | UnsupportedOperationException e) {\n            throw e;\n        } catch (final RuntimeException e) {\n            h();\n        }\n"
                + "    }\n\n    void g() {\n    }\n\n    void h() {\n    }\n}\n");
    }
}
