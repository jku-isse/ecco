package at.jku.isse.ecco.adapter.cpp;

import at.jku.isse.ecco.service.EccoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Two variants of a C++ file that differ in one aspect, committed under their own feature into one
 * repository: each checkout gives back its own variant, byte for byte and in its line order. Lines
 * are matched across variants by their exact text (SourceLineArtifactData), scopes by kind and
 * signature (ScopeArtifactData).
 */
public class CppRepositoryRoundTripTest {

    private static void assertEachVariantComesBack(Path work, String name, String a, String b) throws Exception {
        Path content = Files.createDirectories(work.resolve("content"));
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(work.resolve(".ecco"));
            service.init();
            service.setBaseDir(content);
            Files.writeString(content.resolve(name), a);
            service.commit("a", "A");
            Files.writeString(content.resolve(name), b);
            service.commit("b", "B");

            for (String[] variant : new String[][]{{"A", a}, {"B", b}}) {
                Path checkout = Files.createDirectories(work.resolve("checkout-" + variant[0]));
                service.setBaseDir(checkout);
                service.checkout(variant[0]);
                assertEquals(variant[1], Files.readString(checkout.resolve(name)), variant[0]);
            }
        }
    }

    @Test
    @Timeout(120)
    public void aLineInsideAFunction(@TempDir Path work) throws Exception {
        assertEachVariantComesBack(work, "main.cpp",
                "#include <cstdio>\n\nnamespace n {\nint f(int x) {\n    x++;\n    return x;\n}\n}\n",
                "#include <cstdio>\n\nnamespace n {\nint f(int x) {\n    x--;\n    return x;\n}\n}\n");
    }

    @Test
    @Timeout(120)
    public void anInsertedLineKeepsItsPlace(@TempDir Path work) throws Exception {
        assertEachVariantComesBack(work, "main.cpp",
                "int f(int x) {\n    x++;\n    return x;\n}\n",
                "int f(int x) {\n    log(x);\n    x++;\n    return x;\n}\n");
    }

    @Test
    @Timeout(120)
    public void anIfdefBranch(@TempDir Path work) throws Exception {
        assertEachVariantComesBack(work, "main.cpp",
                "int f() {\n#ifdef DEBUG\n    trace();\n#else\n    quiet();\n#endif\n    return 0;\n}\n",
                "int f() {\n#ifdef DEBUG\n    trace(1);\n#else\n    quiet();\n#endif\n    return 0;\n}\n");
    }

    @Test
    @Timeout(120)
    public void anInclude(@TempDir Path work) throws Exception {
        assertEachVariantComesBack(work, "main.cpp",
                "#include <vector>\n#include \"a.hpp\"\n\nint main() {\n    return 0;\n}\n",
                "#include <vector>\n#include \"b.hpp\"\n\nint main() {\n    return 0;\n}\n");
    }

    /** the known open item: include guards around variants of a header (here .hpp and .h) */
    @Test
    @Timeout(120)
    public void includeGuardsOfAHeader(@TempDir Path work) throws Exception {
        assertEachVariantComesBack(work, "shape.hpp",
                "#ifndef SHAPE_HPP\n#define SHAPE_HPP\n\nclass Shape {\npublic:\n    int area() const;\n};\n\n#endif // SHAPE_HPP\n",
                "#ifndef SHAPE_HPP\n#define SHAPE_HPP\n\n#include <string>\n\nclass Shape {\npublic:\n    int area() const;\n    std::string name() const;\n};\n\n#endif // SHAPE_HPP\n");
    }

    /** .h files go to the C++ adapter when the C adapter is not there (as on this classpath) */
    @Test
    @Timeout(120)
    public void differentIncludeGuards(@TempDir Path work) throws Exception {
        assertEachVariantComesBack(work, "shape.h",
                "#ifndef SHAPE_H\n#define SHAPE_H\nint area(void);\n#endif\n",
                "#ifndef GEO_SHAPE_H\n#define GEO_SHAPE_H\nint area(void);\n#endif /* GEO_SHAPE_H */");
    }

    @Test
    @Timeout(120)
    public void indentationAndTrailingWhitespace(@TempDir Path work) throws Exception {
        assertEachVariantComesBack(work, "main.cpp",
                "int f() {\n    return 0;\n}\n",
                "int f() {\n\treturn 0; \n}\n");
    }

    @Test
    @Timeout(120)
    public void aFunctionSignature(@TempDir Path work) throws Exception {
        assertEachVariantComesBack(work, "main.cpp",
                "int f(int x) {\n    return x;\n}\n",
                "int f(long x) {\n    return x;\n}\n");
    }

    /** the same function defined in both branches of an #ifdef: two equal sibling scopes */
    @Test
    @Timeout(120)
    public void aFunctionDefinedInBothBranchesOfAnIfdef(@TempDir Path work) throws Exception {
        assertEachVariantComesBack(work, "main.cpp",
                "#ifdef A\nint f() {\n    return 1;\n}\n#else\nint f() {\n    return 2;\n}\n#endif\n",
                "#ifdef A\nint f() {\n    return 1;\n}\n#else\nint f() {\n    return 3;\n}\n#endif\n");
    }

    /** anonymous structs all have the signature "struct": equal sibling scopes too */
    @Test
    @Timeout(120)
    public void anonymousStructs(@TempDir Path work) throws Exception {
        assertEachVariantComesBack(work, "main.cpp",
                "struct {\n    int a;\n} x;\nstruct {\n    int b;\n} y;\n",
                "struct {\n    int a;\n} x;\nstruct {\n    long b;\n} y;\n");
    }

    @Test
    @Timeout(120)
    public void swappedFunctions(@TempDir Path work) throws Exception {
        assertEachVariantComesBack(work, "main.cpp",
                "int f() {\n    return 1;\n}\n\nint g() {\n    return 2;\n}\n",
                "int g() {\n    return 2;\n}\n\nint f() {\n    return 1;\n}\n");
    }

    /** an #ifdef that makes the parser see the function differently: its lines change parents */
    @Test
    @Timeout(120)
    public void anIfdefAroundAFunctionHeader(@TempDir Path work) throws Exception {
        assertEachVariantComesBack(work, "main.cpp",
                "int f(int a) {\n    return a;\n}\nint g() {\n    return 0;\n}\n",
                "#ifdef X\nint f(int a) {\n#else\nint f(long a) {\n#endif\n    return a;\n}\nint g() {\n    return 0;\n}\n");
    }

    @Test
    @Timeout(120)
    public void repeatedIdenticalLines(@TempDir Path work) throws Exception {
        assertEachVariantComesBack(work, "main.cpp",
                "int f(int x) {\n    switch (x) {\n    case 1:\n        x++;\n        break;\n    }\n\n    return x;\n}\n",
                "int f(int x) {\n    switch (x) {\n    case 1:\n        x++;\n        break;\n    case 2:\n        x--;\n        break;\n    }\n\n\n    return x;\n}\n");
    }

    @Test
    @Timeout(120)
    public void aClassMember(@TempDir Path work) throws Exception {
        assertEachVariantComesBack(work, "main.cpp",
                "class A {\npublic:\n    int x;\n    void f() {\n        x = 1;\n    }\n};\n",
                "class A {\npublic:\n    long x;\n    void f() {\n        x = 1;\n    }\n};\n");
    }
}
