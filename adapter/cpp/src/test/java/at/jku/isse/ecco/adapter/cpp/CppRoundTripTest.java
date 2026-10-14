package at.jku.isse.ecco.adapter.cpp;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.cpp.data.LineArtifactData;
import at.jku.isse.ecco.adapter.cpp.data.ScopeArtifactData;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The first C++ adapter split files into includes, defines, fields and functions and wrote them back
 * in that order, dropping comments, namespaces, classes and #if directives. Files now come back
 * exactly as they were; the parser only groups their lines into scopes.
 */
public class CppRoundTripTest {

    private static final String SOURCE = """
            #ifndef SHAPES_HPP
            #define SHAPES_HPP

            #include <string>

            // Shapes and their areas.
            namespace geo {

            enum class Kind { Circle, Square };

            class Shape {
            public:
                virtual ~Shape() = default;
                virtual double area() const = 0;
            };

            template <typename T>
            T twice(T x) {
                return x * 2;
            }

            /* the area of a square */
            double square(double side) {
            #ifdef LOGGING
                log("square");
            #endif
                return side * side;
            }

            }

            extern "C" {
            int legacy(void);
            }

            #endif
            """;

    @Test
    public void filesComeBackExactly() throws IOException {
        byte[] source = SOURCE.getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(source, roundTrip(source));
    }

    @Test
    public void crlfAndMissingFinalNewlineSurvive() throws IOException {
        byte[] source = "#ifndef H\r\n#define H\r\nint f();\r\n#endif".getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(source, roundTrip(source));
    }

    @Test
    public void latin1FilesSurvive() throws IOException {
        byte[] source = "// café\nint f() {\n    return 1;\n}\n".getBytes(StandardCharsets.ISO_8859_1);
        assertArrayEquals(source, roundTrip(source));
    }

    @Test
    public void linesAreGroupedByTheirInnermostScope() throws IOException {
        Node file = read(SOURCE.getBytes(StandardCharsets.UTF_8));
        assertEquals(List.of("namespace geo", "extern \"C\""), scopesOf(file));
        Node geo = scope(file, "namespace geo");
        assertEquals(List.of("enum Kind", "class Shape", "function template T twice(T x)", "function double square(double side)"), scopesOf(geo));
        assertEquals(List.of("function virtual ~Shape()"), scopesOf(scope(geo, "class Shape")));
        assertEquals(List.of("double square(double side) {", "#ifdef LOGGING", "    log(\"square\");", "#endif", "    return side * side;", "}"),
                linesOf(scope(geo, "function double square(double side)")));
    }

    /**
     * A repository committed with the first C++ adapter holds its artifacts; adding to it would mix
     * both formats, so a commit is refused with an explanation - checkouts still work.
     */
    @Test
    @Timeout(120)
    public void aRepositoryInTheRetiredFormatIsNotCommittedTo() throws Exception {
        Path work = Files.createTempDirectory("cpp-retired");
        Path content = Files.createDirectories(work.resolve("content"));
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(work.resolve(".ecco"));
            service.init();
            service.setBaseDir(content);
            Files.writeString(content.resolve("main.cpp"), "int main() {\n    return 0;\n}\n");
            service.commit("first", "A");
            // what the first adapter stored
            SerEntityFactory entityFactory = new SerEntityFactory();
            for (Association association : service.getRepository().getAssociations())
                for (Node file : association.getRootNode().getChildren())
                    ((Node.Op) file).addChild(entityFactory.createNode(entityFactory.createArtifact(new LineArtifactData("int legacy;"))));

            Files.writeString(content.resolve("main.cpp"), "int main() {\n    return 1;\n}\n");
            EccoException refused = assertThrows(EccoException.class, () -> service.commit("second", "A, B"));
            String message = refused.getMessage() + " " + (refused.getCause() == null ? "" : refused.getCause().getMessage());
            assertTrue(message.contains("re-create the repository"), message);

            Path out = Files.createDirectories(work.resolve("out"));
            service.setBaseDir(out);
            service.checkout("A");
            assertTrue(Files.exists(out.resolve("main.cpp")), "checkouts still work");
        }
    }

    /**
     * Include guards: once (d9ce686c) every conditional directive was stripped, so #ifndef/#define/
     * #endif came back as empty lines. Now they are lines like any other - with or without a
     * trailing comment, final newline, CRLF; also #pragma once and a four-line file.
     */
    @Test
    public void includeGuardsComeBackExactly() throws IOException {
        assertExact("#ifndef X_H\n#define X_H\n\nint f();\n\n#endif\n");
        assertExact("#ifndef X_H\n#define X_H\n#include <x>\nint main() {\n  return 0;\n}\n#endif // X_H");
        assertExact("#ifndef X_H\r\n#define X_H\r\nclass A {\r\n};\r\n#endif /* X_H */\r\n");
        assertExact("#if !defined(X_H)\n# define X_H 1\nnamespace n {\nint g();\n}\n#endif\n");
        assertExact("#pragma once\n#include <x>\nint main() {}\n");
        assertExact("#include <x>\nint main() {\n  return 0;\n}\n");
    }

    /** CDT parses inactive branches too: functions opened in both branches of an #if keep their lines */
    @Test
    public void preprocessorDirectivesSurvive() throws IOException {
        assertExact("""
                #define MAX(a, b) \\
                    ((a) > (b) ? (a) : (b))
                #define DECL(n) int n() { \\
                    return 0; }
                DECL(a)
                #if A
                # if B
                int f() {
                # elif C
                int f(int) {
                # endif
                #else
                int g() {
                #endif
                    return MAX(1, 2);
                }
                #ifdef A
                int h() {
                    return 1;
                }
                #else
                int h() {
                    return 2;
                }
                #endif
                """);
    }

    @Test
    public void functionKindsSurvive() throws IOException {
        assertExact("""
                int proto(int);
                int knr(a, b)
                    int a;
                    long b;
                {
                    return a + b;
                }
                main() {
                    return 0;
                }
                static inline int twice(int x) { return 2 * x; }
                int (*pick(void))(int) {
                    return proto;
                }
                void (*handlers[2])(int);
                __attribute__((noreturn)) void die(void) {
                    for (;;) {}
                }
                [[nodiscard]] auto sum(int a, int b) -> int { return a + b; }
                """);
    }

    @Test
    public void statementsSurvive() throws IOException {
        assertExact("""
                int f(int x) {
                    do {
                        x--;
                    } while (x > 0);
                    switch (x) {
                    case 0:
                    case 1:
                        x++;
                        [[fallthrough]];
                    default:
                        goto end;
                    }
                    {
                        { x = 2; }
                    }
                end:
                    return x;
                }
                """);
    }

    @Test
    public void commentsTabsAndEmptyFilesSurvive() throws IOException {
        assertExact("/* first line */\n\tint\tx;   \nint f() {\t\n\treturn 0; // done \n}\n// last line, no newline");
        assertExact("");
        assertExact("\n");
        assertExact("\r\n\r\n");
    }

    /**
     * The first adapter turned what CDT could not parse into problem blocks (and lost parts of it);
     * now such lines are lines like any other, wherever they are.
     */
    @Test
    public void unparseableCodeSurvives() throws IOException {
        assertExact("class {{{ ;;; \nnamespace @ x {\ntemplate<<<\nint f() {\n  return 1;\n}\n");
        assertExact("int f( {\n  @@@ ???\n}}}\nint g() {\n  $$ not C++\n  return 1;\n}\n");
        assertExact("int f() {\n  if (x) {\n    return 1;\n}\n");
    }

    /** a scope starting on the line the previous one ends on is left to that one: no line twice */
    @Test
    public void scopesSharingALineSurvive() throws IOException {
        assertExact("int f() {\n  return 1;\n} int g() {\n  return 2;\n}\n");
        assertExact("int f() { return 1; } int g() { return 2; }\n");
        assertExact("class A { void f() {} }; class B { void g() {} };\nnamespace n { int h() { return 1; } }\n");
        Node file = read("int f() {\n  return 1;\n} int g() {\n  return 2;\n}\n".getBytes(StandardCharsets.UTF_8));
        assertEquals(List.of("function int f()"), scopesOf(file));
        assertEquals(List.of("int f() {", "  return 1;", "} int g() {"), linesOf(scope(file, "function int f()")));
    }

    /** TextFileFormat's documented limitation: mixed line separators become the first. Whitespace only. */
    @Test
    public void mixedLineSeparatorsBecomeTheFirst() throws IOException {
        assertEquals("int a;\r\nint b;\r\nint c;\r\n", new String(roundTrip("int a;\r\nint b;\nint c;\r\n".getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8));
    }

    /**
     * Scope signatures: whitespace-normalized, so reformatting keeps the scope; a parameter type
     * makes another one. An enum is its name only (enum and enum class collide) and anonymous
     * structs are all "struct" - such scopes are aligned as one, their lines still differ.
     */
    @Test
    public void scopeSignatures() throws IOException {
        Node file = read("""
                int  f( int a ,
                        char **b ) {
                    return a;
                }
                int f(long a) {
                    return 0;
                }
                enum E { X };
                struct { int a; } s;
                union U { int a; };
                """.getBytes(StandardCharsets.UTF_8));
        assertEquals(List.of("function int f( int a , char **b )", "function int f(long a)", "enum E", "struct", "union U"), scopesOf(file));
        assertEquals(List.of("enum E"), scopesOf(read("enum class E { X };\n".getBytes(StandardCharsets.UTF_8))));
    }

    private static void assertExact(String source) throws IOException {
        assertEquals(source, new String(roundTrip(source.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8));
    }

    private static byte[] roundTrip(byte[] source) throws IOException {
        Node file = read(source);
        Path outputDir = Files.createTempDirectory("cpp-roundtrip-out");
        Path[] written = new CppWriter().write(outputDir, Set.of(file));
        assertEquals(1, written.length);
        return Files.readAllBytes(written[0]);
    }

    private static Node read(byte[] source) throws IOException {
        Path baseDir = Files.createTempDirectory("cpp-roundtrip");
        Files.write(baseDir.resolve("shapes.hpp"), source);
        Set<Node.Op> read = new CppReader(new SerEntityFactory()).read(baseDir, new Path[]{Path.of("shapes.hpp")});
        assertEquals(1, read.size());
        return read.iterator().next();
    }

    private static List<String> scopesOf(Node node) {
        List<String> scopes = new ArrayList<>();
        for (Node child : node.getChildren())
            if (child.getArtifact().getData() instanceof ScopeArtifactData)
                scopes.add(child.getArtifact().getData().toString());
        return scopes;
    }

    private static List<String> linesOf(Node node) {
        List<String> lines = new ArrayList<>();
        for (Node child : node.getChildren())
            lines.add(child.getArtifact().getData().toString());
        return lines;
    }

    private static Node scope(Node node, String name) {
        for (Node child : node.getChildren())
            if (child.getArtifact().getData() instanceof ScopeArtifactData && child.getArtifact().getData().toString().equals(name))
                return child;
        throw new AssertionError("no scope " + name + " in " + scopesOf(node));
    }
}
