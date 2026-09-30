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
 * Statements the adapter changed on the way through the repository (in half of this repository's
 * own sources): braces were added to or removed from single-statement bodies, synchronized
 * statements were dropped, blocks among statements were flattened, arrow switch cases became
 * falling-through "case A:" ones, a label with a comma was split, and empty interface default
 * methods lost their body. Written back, their code must be the same, braces included.
 */
public class JavaASTStatementFidelityTest {

    private static final JavaParser PARSER = new JavaParser(new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_18));
    private static final PrettyPrinterConfiguration NO_COMMENTS = new PrettyPrinterConfiguration().setPrintComments(false);

    private static void assertSameCodeAfterRoundTrip(String source) throws IOException {
        Path base = Files.createTempDirectory("java-ast-fidelity");
        Files.writeString(base.resolve("F.java"), source);
        Set<Node.Op> nodes = new JavaASTReader(new SerEntityFactory()).read(base, new Path[]{Path.of("F.java")});
        Path out = Files.createTempDirectory("java-ast-fidelity-out");
        new JavaASTWriter().write(out, Set.copyOf(nodes));
        String written = Files.readString(out.resolve("F.java"));
        assertEquals(PARSER.parse(source).getResult().orElseThrow().toString(NO_COMMENTS),
                PARSER.parse(written).getResult().orElseThrow().toString(NO_COMMENTS), written);
    }

    private static String method(String body) {
        return "class C {\n    void f(int[] xs, Object lock) {\n" + body + "    }\n\n    void g(int x) {\n    }\n}\n";
    }

    @Test
    public void loopBodiesKeepTheirBraces() throws IOException {
        assertSameCodeAfterRoundTrip(method("        for (int x : xs) g(x);\n"
                + "        for (int x : xs) {\n            g(x);\n        }\n"
                + "        while (xs.length > 0) {\n        }\n"
                + "        for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) g(i + j);\n"
                + "        do g(1); while (xs.length > 5);\n"));
    }

    @Test
    public void ifBranchesKeepTheirBraces() throws IOException {
        assertSameCodeAfterRoundTrip(method("        if (xs.length > 0) g(1);\n        else g(2);\n"
                + "        if (xs.length > 1) {\n            g(3);\n        } else if (xs.length > 2) g(4);\n        else {\n            g(5);\n        }\n"
                + "        if (xs.length > 3) for (int x : xs) if (x > 0) g(x); else g(-x);\n"
                + "        if (xs.length > 4) {\n            if (xs.length > 5) g(6);\n        } else g(7);\n"));
    }

    @Test
    public void synchronizedStatementsAndBlocksAreKept() throws IOException {
        assertSameCodeAfterRoundTrip(method("        synchronized (lock) {\n            g(1);\n        }\n"
                + "        {\n            int y = 1;\n            g(y);\n        }\n"
                + "        {\n            int y = 2;\n            g(y);\n        }\n"
                + "        switch (xs.length) {\n            case 0: {\n                int z = 0;\n                g(z);\n                break;\n            }\n"
                + "            default: {\n                int z = 1;\n                g(z);\n            }\n        }\n"));
    }

    @Test
    public void switchCasesKeepTheirArrowsAndLabels() throws IOException {
        assertSameCodeAfterRoundTrip(method("        switch (xs.length) {\n            case 1, 2 -> g(1);\n"
                + "            case 3 -> {\n                g(3);\n                g(4);\n            }\n"
                + "            case 4 -> throw new IllegalStateException();\n            default -> g(0);\n        }\n"
                + "        switch (String.valueOf(xs.length)) {\n            case \"a,b\":\n                g(5);\n"
                + "            case \"c\":\n                g(6);\n                break;\n            default:\n        }\n"));
    }

    @Test
    public void methodsHaveABodyExactlyWhenTheirDeclarationSaysSo() throws IOException {
        assertSameCodeAfterRoundTrip("interface I {\n    void a();\n\n    default void b() {\n    }\n\n"
                + "    static void c() {\n    }\n\n    private void d() {\n    }\n}\n");
        assertSameCodeAfterRoundTrip("abstract class A {\n    native void n();\n\n    abstract void a();\n\n    void e() {\n    }\n}\n");
    }
}
