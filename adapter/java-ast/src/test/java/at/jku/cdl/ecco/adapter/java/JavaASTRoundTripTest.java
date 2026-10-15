package at.jku.cdl.ecco.adapter.java;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.tree.Node;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.printer.configuration.PrettyPrinterConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sources in the layout the adapter writes (JavaParser's, e.g. "switch(x)", an enum's constants
 * after an empty line) read and written back: the result must be the same, byte for byte. Cases
 * that are not are below the table - disabled, with the output they should have.
 */
public class JavaASTRoundTripTest {

    private static final JavaParser PARSER = new JavaParser(new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21));
    private static final PrettyPrinterConfiguration NO_COMMENTS = new PrettyPrinterConfiguration().setPrintComments(false);

    static String readWrite(String fileName, String source) throws IOException {
        Path base = Files.createTempDirectory("java-ast-round-trip");
        Files.writeString(base.resolve(fileName), source);
        Set<Node.Op> nodes = new JavaASTReader(new SerEntityFactory()).read(base, new Path[]{Path.of(fileName)});
        Path out = Files.createTempDirectory("java-ast-round-trip-out");
        new JavaASTWriter().write(out, Set.copyOf(nodes));
        return Files.readString(out.resolve(fileName));
    }

    private static String method(String body) {
        return "class C {\n    void f(int[] xs, Object lock) {\n" + body + "    }\n\n    void g(int x) {\n    }\n}\n";
    }

    private static String withoutComments(String source) {
        return PARSER.parse(source).getResult().orElseThrow().toString(NO_COMMENTS);
    }

    static Stream<Arguments> sources() {
        return Stream.of(
                // constructors
                Arguments.of("constructor with modifiers, type parameters, varargs, throws and annotations",
                        "public class C {\n    private final Object value;\n\n"
                                + "    @Deprecated\n    @SuppressWarnings(\"unchecked\")\n"
                                + "    protected <T extends Number, U> C(T t, final U u, String... rest) throws java.io.IOException, IllegalStateException {\n"
                                + "        this.value = t;\n    }\n\n"
                                + "    C() throws java.io.IOException {\n        this(1, null);\n    }\n}\n"),
                Arguments.of("constructor calling super and this with type arguments",
                        "class C extends Thread {\n    C(String name) {\n        super(name);\n        setDaemon(true);\n    }\n\n"
                                + "    <T> C(T t) {\n        <String>this(\"\");\n    }\n}\n"),
                Arguments.of("comments on and inside a constructor",
                        "class C {\n    /**\n     * Makes a C.\n     */\n    C() {\n        // before\n        int x = 1;\n        // after\n    }\n}\n"),
                Arguments.of("constructor of a nested class",
                        "class C {\n    static class N {\n        private N(int a) {\n            System.out.println(a);\n        }\n    }\n}\n"),
                // records
                Arguments.of("record with a compact constructor",
                        "record Point(int x, int y) {\n    public Point {\n        if (x < 0)\n            throw new IllegalArgumentException();\n    }\n}\n"),
                Arguments.of("compact constructor with an annotation and comments",
                        "record Point(int x, int y) {\n    /**\n     * Checks.\n     */\n    @Deprecated\n    public Point {\n        check(x);\n        // end of compact constructor\n    }\n\n"
                                + "    static void check(int v) {\n    }\n}\n"),
                Arguments.of("generic record with a canonical constructor and a method",
                        "public record Pair<A, B>(A a, B b) implements java.io.Serializable {\n    public Pair(A a, B b) {\n        this.a = a;\n        this.b = b;\n    }\n\n"
                                + "    public A first() {\n        return a;\n    }\n}\n"),
                // enums
                Arguments.of("top-level enum with constructor, field and method",
                        "public enum Planet {\n\n    MERCURY(3.3e23, 2.4e6), EARTH(5.9e24, 6.3e6);\n    private final double mass;\n\n"
                                + "    Planet(double mass, double radius) {\n        this.mass = mass;\n    }\n\n"
                                + "    double mass() {\n        return mass;\n    }\n}\n"),
                Arguments.of("enum constants with bodies",
                        "enum Op {\n\n    PLUS {\n        int apply(int a, int b) {\n            return a + b;\n        }\n    }\n    , MINUS {\n        int apply(int a, int b) {\n            return a - b;\n        }\n    }\n    ;\n"
                                + "    abstract int apply(int a, int b);\n}\n"),
                Arguments.of("enum nested in a class",
                        "class C {\n    enum E {\n\n        A, B\n    }\n}\n"),
                // interfaces and nested types
                Arguments.of("interface with default, static and private methods",
                        "public interface I {\n    int SIZE = 3;\n\n    void a();\n\n    default int b() {\n        return c() + 1;\n    }\n\n"
                                + "    static I of() {\n        return () -> {\n        };\n    }\n\n    private int c() {\n        return SIZE;\n    }\n}\n"),
                Arguments.of("annotated interface members and a sealed interface",
                        "@FunctionalInterface\npublic interface F {\n    @Deprecated\n    int x = 1;\n\n    int apply(@Deprecated int a);\n}\n\n"
                                + "sealed interface S permits A, B {\n}\n"),
                Arguments.of("anonymous class",
                        "class C {\n    Runnable r() {\n        return new Runnable() {\n            @Override\n            public void run() {\n                System.out.println(1);\n            }\n        };\n    }\n}\n"),
                Arguments.of("nested generic class with bounds",
                        "class Outer<T extends Comparable<T>> {\n    static final class Inner<K, V extends java.util.List<? super K>> extends Object implements Runnable {\n"
                                + "        public void run() {\n        }\n    }\n}\n"),
                Arguments.of("initializer blocks with comments",
                        "class C {\n    static int x;\n\n    int y;\n\n    static {\n        // static init\n        x = 1;\n    }\n\n    {\n        y = 2;\n    }\n}\n"),
                // bodies with and without braces
                Arguments.of("dangling else",
                        method("        if (xs.length > 0)\n            if (xs.length > 1)\n                g(1);\n            else\n                g(2);\n")),
                Arguments.of("if with else inside an if with else, unbraced",
                        method("        if (xs.length > 0)\n            if (xs.length > 1)\n                g(1);\n            else\n                g(2);\n        else\n            g(3);\n")),
                Arguments.of("else-if chain without a final else",
                        method("        if (xs.length == 0)\n            g(0);\n        else if (xs.length == 1)\n            g(1);\n        else if (xs.length == 2)\n            g(2);\n")),
                Arguments.of("dangling else, braced",
                        method("        if (xs.length > 0) {\n            if (xs.length > 1)\n                g(1);\n        } else\n            g(2);\n")),
                Arguments.of("else after a loop, a labeled statement and a do",
                        method("        if (xs.length > 0)\n            while (xs.length > 1) g(1);\n        else\n            g(2);\n"
                                + "        if (xs.length > 0)\n            outer: g(1);\n        else\n            g(2);\n"
                                + "        if (xs.length > 0)\n            do {\n                g(1);\n            } while (xs.length > 1);\n        else\n            g(2);\n")),
                Arguments.of("braces kept where an inner short if would take the else",
                        method("        if (xs.length > 0) {\n            while (xs.length > 1) if (xs.length > 2)\n                g(1);\n        } else\n            g(2);\n"
                                + "        if (xs.length > 0) {\n            outer: if (xs.length > 2)\n                g(1);\n        } else\n            g(2);\n")),
                Arguments.of("else-if chain of four",
                        method("        if (xs.length == 0) {\n            g(0);\n        } else if (xs.length == 1) {\n            g(1);\n        } else if (xs.length == 2) {\n            g(2);\n"
                                + "        } else if (xs.length == 3)\n            g(3);\n        else {\n            g(4);\n        }\n")),
                Arguments.of("loops with several statements and nested statements",
                        method("        for (int x : xs) {\n            g(x);\n            g(x + 1);\n        }\n"
                                + "        while (xs.length > 0) {\n            if (xs.length > 1)\n                g(1);\n            g(2);\n        }\n"
                                + "        for (int x : xs) {\n            try {\n                g(x);\n            } finally {\n                g(0);\n            }\n        }\n"
                                + "        do {\n            g(1);\n            switch(xs.length) {\n                case 1:\n                    g(1);\n            }\n        } while (xs.length > 5);\n")),
                Arguments.of("empty bodies",
                        method("        for (; ; ) ;\n        while (xs.length > 0) ;\n        for (int x : xs) {\n        }\n        if (xs.length > 0)\n            ;\n")),
                Arguments.of("statements inside synchronized",
                        method("        synchronized (lock) {\n            if (xs.length > 0)\n                g(1);\n            try {\n                g(2);\n            } catch (RuntimeException e) {\n            }\n            g(3);\n            // end of synchronized\n        }\n")),
                Arguments.of("labeled loops",
                        method("        outer: for (int i = 0; i < 3; i++) {\n            for (int j = 0; j < 3; j++) {\n                if (j == i)\n                    continue outer;\n                if (j > i)\n                    break outer;\n            }\n        }\n")),
                Arguments.of("comments at the end of loop, if and else bodies",
                        method("        for (int x : xs) {\n            g(x);\n            // end of loop\n        }\n"
                                + "        if (xs.length > 0) {\n            g(1);\n            // end of then\n        } else {\n            g(2);\n            // end of else\n        }\n")),
                // try
                Arguments.of("try-with-resources with a catch",
                        method("        try (java.io.Reader r = new java.io.StringReader(\"\");\n            java.io.Reader s = new java.io.StringReader(\"\")) {\n            r.read();\n"
                                + "        } catch (java.io.IOException e) {\n            g(1);\n        }\n")),
                Arguments.of("try-with-resources on a variable",
                        method("        java.io.Reader r = new java.io.StringReader(\"\");\n        try (r) {\n            g(1);\n        }\n")),
                Arguments.of("multi-catch",
                        method("        try {\n            g(1);\n        } catch (IllegalStateException | IllegalArgumentException e) {\n            g(2);\n        } catch (final RuntimeException e) {\n            throw e;\n        }\n")),
                Arguments.of("try with finally only",
                        method("        try {\n            g(1);\n        } finally {\n            g(2);\n        }\n")),
                Arguments.of("comments in try, catch and finally",
                        method("        // the try\n        try {\n            g(1);\n            // end of try\n        } catch (RuntimeException e) {\n            g(2);\n            // end of catch\n"
                                + "        } finally {\n            // in finally\n            g(3);\n        }\n")),
                Arguments.of("nested try",
                        method("        try {\n            try {\n                g(1);\n            } catch (RuntimeException e) {\n                g(2);\n            }\n        } catch (Exception e) {\n            g(3);\n        }\n")),
                // switch
                Arguments.of("switch expression with a comment",
                        method("        int y = switch(xs.length) {\n            // first\n            case 0 ->\n                1;\n            case 1 ->\n                {\n                    yield 2;\n                }\n            default ->\n                3;\n        };\n")),
                Arguments.of("switch statement with fall-through and default in the middle",
                        method("        switch(xs.length) {\n            case 0:\n            case 1:\n                g(1);\n                break;\n            default:\n                g(0);\n            case 2:\n                g(2);\n        }\n")),
                Arguments.of("arrow switch with an empty block and a default block",
                        method("        switch(xs.length) {\n            case 0 ->\n                {\n                }\n            default ->\n                {\n                    g(0);\n                    g(1);\n                }\n        }\n")),
                Arguments.of("pattern switch",
                        method("        Object o = xs;\n        switch(o) {\n            case Integer i ->\n                g(i);\n            case String s ->\n                g(1);\n            default ->\n                g(0);\n        }\n")),
                // statements kept as text
                Arguments.of("local record, class and interface",
                        method("        record R(int a) {\n        }\n        class L {\n        }\n        interface I {\n        }\n        g(new R(1).a());\n")),
                Arguments.of("lambda with a comment, text block, assert, throw, instanceof pattern",
                        method("        java.util.function.IntUnaryOperator op = x -> {\n            // in lambda\n            return x + 1;\n        };\n"
                                + "        String s = \"\"\"\n            text\n            block\"\"\";\n"
                                + "        assert xs.length > 0 : \"empty\";\n"
                                + "        if (lock instanceof int[] a && a.length > 0)\n            g(1);\n"
                                + "        throw new IllegalStateException();\n")),
                // files
                Arguments.of("package with a Javadoc and imports",
                        "/**\n * Package doc.\n */\npackage p;\n\nimport java.util.List;\nimport static java.util.Collections.emptyList;\n\nclass C {\n    List<String> xs = emptyList();\n}\n"),
                Arguments.of("orphan comments at the end of a class",
                        "class C {\n    void f() {\n    }\n    // end of class\n    /* and a block */\n}\n")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sources")
    public void roundTrips(String name, String source) throws IOException {
        assertEquals(source, readWrite("C.java", source));
    }

    @Test
    public void moduleInfoWithStaticRequiresAndQualifiedOpens() throws IOException {
        String source = "module m {\n    requires static java.sql;\n    requires transitive java.logging;\n    opens p to q, r;\n"
                + "    provides s.S with s.A, s.B;\n}\n";
        assertEquals(source, readWrite("module-info.java", source));
    }

    /**
     * Comments JavaParser does not print where they were: they move (a trailing comment above its
     * statement, a comment in a type argument after the type), but none is lost and the code stays
     * the same.
     */
    @Test
    public void commentsInsideExpressionsMoveButTheCodeStays() throws IOException {
        String[][] cases = {
                {"        int a = 1 + // one\n        2;\n", "one"},
                {"        java.util.List</* c */ String> l = null;\n", "/* c */"},
                {"        int[] a = { 1, // one\n        2 };\n", "// one"},
                {"        @SuppressWarnings(/* c */ \"x\")\n        int a = 1;\n", "/* c */"},
                {"        g(xs.length // len\n        );\n", "// len"},
                {"        g(1);// c\n", "// c"},
                {"        if (xs.length > 0 /* c */)\n            g(1);\n", "/* c */"},
                {"        for (int x : xs /* c */) g(x);\n", "/* c */"},
        };
        for (String[] c : cases) {
            String source = method(c[0]);
            String written = readWrite("C.java", source);
            assertTrue(written.contains(c[1]), written);
            assertEquals(withoutComments(source), withoutComments(written), written);
        }
        // JavaParser prints a comment before the code it belongs to: "1 + // one" belongs to "1"
        assertEquals(method("        int a = // one\n        1 + 2;\n"), readWrite("C.java", method("        int a = 1 + // one\n        2;\n")));
    }

    /**
     * A line comment behind code was written twice, or more: JavaParser gives it to every node
     * ending on its line - when read ("g(xs.length // len" to g and xs.length) and when the text
     * printed with it is parsed to be written ("int a = // one" to int and a).
     */
    @Test
    public void aCommentIsWrittenOnce() throws IOException {
        String[][] cases = {
                {"        int a = 1 + // one\n        2;\n", "// one"},
                {"        g(xs.length // len\n        );\n", "// len"},
                {"        g(1); g(2); // c\n", "// c"},
                {"        g(xs.length + // a\n        xs.length + // b\n        1);\n", "// a"},
        };
        for (String[] c : cases) {
            String written = readWrite("C.java", method(c[0]));
            assertEquals(1, written.split(java.util.regex.Pattern.quote(c[1]), -1).length - 1, written);
            assertEquals(withoutComments(method(c[0])), withoutComments(written), written);
        }
        assertEquals(method("        g(// len\n        xs.length);\n"), readWrite("C.java", method("        g(xs.length // len\n        );\n")));
    }

    // ---- what does not round-trip ----

    // ---- fixed: they did not round-trip ----

    /**
     * A constant named DEFAULT was stored like the default entry and written as "default:" - the
     * switch then took that branch for every value it did not name.
     */
    @Test
    public void caseDefaultStaysACaseLabel() throws IOException {
        String[] bodies = {
                "        Object o = xs;\n        switch(m) {\n            case DEFAULT:\n                g(1);\n                break;\n            case OTHER:\n                g(2);\n                break;\n        }\n",
                "        Object o = xs;\n        switch(m) {\n            case DEFAULT:\n                g(1);\n                break;\n            default:\n                g(2);\n        }\n",
                "        Object o = xs;\n        switch(m) {\n            case DEFAULT ->\n                g(1);\n            default ->\n                g(2);\n        }\n",
        };
        for (String body : bodies) {
            String source = method(body);
            assertEquals(source, readWrite("C.java", source));
        }
    }

    /** The Javadoc and comments of enum constants were read but not written. */
    @Test
    public void enumConstantsKeepTheirComments() throws IOException {
        String source = "enum E {\n\n    /**\n     * The first.\n     */\n    A,\n    // the second\n    B\n}\n";
        String written = readWrite("E.java", source);
        assertTrue(written.contains("The first."), written);
        assertTrue(written.contains("// the second"), written);
        assertEquals(withoutComments(source), withoutComments(written), written);
    }

    /** The guard of a pattern case was lost. */
    @Test
    public void aCaseGuardIsKept() throws IOException {
        String source = method("        Object o = xs;\n        switch(o) {\n            case Integer i when i > 0 ->\n                g(i);\n            default ->\n                g(0);\n        }\n");
        assertEquals(source, readWrite("C.java", source));
    }

    /** "case null, default" was written as "case null". */
    @Test
    public void caseNullDefaultIsKept() throws IOException {
        String source = method("        Object o = xs;\n        switch(o) {\n            case String s ->\n                g(1);\n            case null, default ->\n                g(0);\n        }\n");
        assertEquals(source, readWrite("C.java", source));
    }

    /** The annotations of an enum constant were lost. */
    @Test
    public void anEnumConstantKeepsItsAnnotation() throws IOException {
        String source = "enum E {\n\n    @Deprecated\n    OLD, NEW\n}\n";
        assertEquals(source, readWrite("C.java", source));
    }

    /** Was written without "// logging" (#52). */
    @Test
    public void aModuleDirectiveKeepsItsComment() throws IOException {
        String source = "module m {\n    requires java.sql;\n    // logging\n    requires java.logging;\n}\n";
        assertEquals(source, readWrite("module-info.java", source));
    }

    /** These comments were lost: stored as text printed without comments (#52). */
    @Test
    public void commentsInPartsOfStatementsAndDeclarationsAreKept() throws IOException {
        String[] sources = {
                method("        g(/* nothing */);\n"),
                method("        Runnable r = () -> /* c */ g(1);\n"),
                method("        try (/* c */ java.io.Reader r = null) {\n            g(1);\n        }\n"),
                method("        try {\n            g(1);\n        } catch (/* c */ RuntimeException e) {\n            g(2);\n        }\n"),
                method("        switch(xs.length) {\n            case /* c */ 1:\n                g(1);\n        }\n"),
                "class C {\n    C(/* c */ int a) {\n    }\n}\n",
                // more of the same kind
                method("        g(/* a */ 1);\n"),
                method("        new Object(/* nothing */);\n"),
                method("        java.util.function.IntUnaryOperator op = x -> /* c */ x + 1;\n"),
                method("        try (/* a */ java.io.Reader r = null;\n            /* b */ java.io.Reader s = null) {\n            g(1);\n        }\n"),
                method("        try {\n            g(1);\n        } catch (/* c */ IllegalStateException | IllegalArgumentException e) {\n            g(2);\n        }\n"),
                method("        switch(xs.length) {\n            case /* one */ 1, /* two */ 2 ->\n                g(1);\n            default ->\n                g(0);\n        }\n"),
                method("        Object o = xs;\n        switch(o) {\n            case Integer i when /* positive */ i > 0 ->\n                g(i);\n            default ->\n                g(0);\n        }\n"),
                "class C {\n    C(int a, /* c */ int b, int c) {\n    }\n}\n",
                "module m {\n    // sql\n    requires java.sql;\n    /* api */\n    exports p;\n    // reflection\n    opens q;\n    uses s.S;\n    // impl\n    provides s.S with s.A;\n}\n",
        };
        for (String source : sources)
            assertEquals(source, readWrite(source.startsWith("module") ? "module-info.java" : "C.java", source));
    }

    // ---- errors ----

    @Test
    public void aFileThatDoesNotFullyParseIsRefused() throws IOException {
        // a local enum (Java 16) - JavaParser 3.26 does not parse it
        String source = method("        enum L {\n            A\n        }\n");
        EccoException e = assertThrows(EccoException.class, () -> readWrite("C.java", source));
        assertTrue(e.getMessage().contains("Unable to fully parse"), e.getMessage());
    }

    @Test
    public void aMissingFileIsRefused() throws IOException {
        Path base = Files.createTempDirectory("java-ast-round-trip");
        JavaASTReader reader = new JavaASTReader(new SerEntityFactory());
        assertThrows(EccoException.class, () -> reader.read(base, new Path[]{Path.of("Missing.java")}));
    }

    @Test
    public void readingWithoutABaseIsRefused() {
        JavaASTReader reader = new JavaASTReader(new SerEntityFactory());
        assertThrows(EccoException.class, () -> reader.read(new Path[]{Path.of("C.java")}));
    }

    @Test
    public void anAbsolutePathIsStoredRelativeToTheBase() throws IOException {
        Path base = Files.createTempDirectory("java-ast-round-trip");
        String source = "class C {\n}\n";
        Files.writeString(base.resolve("C.java"), source);
        JavaASTReader reader = new JavaASTReader(new SerEntityFactory());
        reader.addListener(null);
        reader.removeListener(null);
        Set<Node.Op> nodes = reader.read(base, new Path[]{base.resolve("C.java")});
        Path out = Files.createTempDirectory("java-ast-round-trip-out");
        new JavaASTWriter().write(out, Set.copyOf(nodes));
        assertEquals(source, Files.readString(out.resolve("C.java")));
    }

    /**
     * Stored before the constants got a group of their own, an enum has them directly below its
     * node - such commits are written as before.
     */
    @Test
    public void enumConstantsStoredWithoutAGroupAreWritten() throws IOException {
        String source = "enum E {\n\n    @Deprecated\n    A(1), B(2) {\n\n        int f() {\n            return 2;\n        }\n    }\n    ;\n\n    E(int x) {\n    }\n\n    int f() {\n        return 1;\n    }\n}\n";
        Path base = Files.createTempDirectory("java-ast-round-trip");
        Files.writeString(base.resolve("E.java"), source);
        Set<Node.Op> nodes = new JavaASTReader(new SerEntityFactory()).read(base, new Path[]{Path.of("E.java")});
        Path out = Files.createTempDirectory("java-ast-round-trip-out");
        new JavaASTWriter().write(out, Set.copyOf(nodes));
        String written = Files.readString(out.resolve("E.java"));

        // the shape of before: constants moved out of their group, directly below the enum
        Node.Op enumNode = nodes.iterator().next().getChildren().stream()
                .filter(n -> ((at.jku.cdl.ecco.adapter.java.artifactData.JavaASTData) n.getArtifact().getData()).getType()
                        == at.jku.cdl.ecco.adapter.java.artifactData.ASTNodeType.ENUM_DECLARATION).findFirst().orElseThrow();
        Node.Op group = enumNode.getChildren().stream()
                .filter(n -> ((at.jku.cdl.ecco.adapter.java.artifactData.JavaASTData) n.getArtifact().getData()).getType()
                        == at.jku.cdl.ecco.adapter.java.artifactData.ASTNodeType.ENUM_CONSTANT_GROUP).findFirst().orElseThrow();
        enumNode.removeChild(group);
        for (Node.Op constant : java.util.List.copyOf(group.getChildren())) {
            group.removeChild(constant);
            enumNode.addChild(constant);
        }
        Path oldOut = Files.createTempDirectory("java-ast-round-trip-out");
        new JavaASTWriter().write(oldOut, Set.copyOf(nodes));
        assertEquals(written, Files.readString(oldOut.resolve("E.java")));
        assertTrue(written.contains("@Deprecated\n    A(1), B(2) {"), written);
    }

    /** A file that cannot be written fails the write - it was printed and ignored, and the checkout went on without it. */
    @Test
    public void aFailedWriteIsReported() throws IOException {
        Path base = Files.createTempDirectory("java-ast-round-trip");
        Files.writeString(base.resolve("C.java"), "class C {\n}\n");
        Set<Node.Op> nodes = new JavaASTReader(new SerEntityFactory()).read(base, new Path[]{Path.of("C.java")});
        Path out = Files.createTempDirectory("java-ast-round-trip-out");
        Files.createDirectory(out.resolve("C.java")); // the file cannot be written
        EccoException failure = assertThrows(EccoException.class, () -> new JavaASTWriter().write(out, Set.copyOf(nodes)));
        assertEquals("Could not write file: " + out.resolve("C.java"), failure.getMessage());
    }
}
