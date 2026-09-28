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
