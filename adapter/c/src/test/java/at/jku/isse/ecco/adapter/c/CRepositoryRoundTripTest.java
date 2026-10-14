package at.jku.isse.ecco.adapter.c;

import at.jku.isse.ecco.service.EccoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Two variants of a C file that differ in one aspect, committed under their own feature into one
 * repository: each checkout gives back its own variant, byte for byte and in its line order. Lines
 * are matched across variants by their exact text (LineArtifactData) and functions by their
 * signature (FunctionArtifactData), so a difference the data classes did not tell apart would put one
 * variant's code into the other's checkout.
 */
public class CRepositoryRoundTripTest {

    private static void assertEachVariantComesBack(Path work, String a, String b) throws Exception {
        Path content = Files.createDirectories(work.resolve("content"));
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(work.resolve(".ecco"));
            service.init();
            service.setBaseDir(content);
            Files.writeString(content.resolve("main.c"), a);
            service.commit("a", "A");
            Files.writeString(content.resolve("main.c"), b);
            service.commit("b", "B");

            for (String[] variant : new String[][]{{"A", a}, {"B", b}}) {
                Path checkout = Files.createDirectories(work.resolve("checkout-" + variant[0]));
                service.setBaseDir(checkout);
                service.checkout(variant[0]);
                assertEquals(variant[1], Files.readString(checkout.resolve("main.c")), variant[0]);
            }
        }
    }

    @Test
    @Timeout(120)
    public void aLineInsideAFunction(@TempDir Path work) throws Exception {
        assertEachVariantComesBack(work,
                "#include <stdio.h>\n\nint f(int x) {\n    x++;\n    return x;\n}\n",
                "#include <stdio.h>\n\nint f(int x) {\n    x--;\n    return x;\n}\n");
    }

    @Test
    @Timeout(120)
    public void anInsertedLineKeepsItsPlace(@TempDir Path work) throws Exception {
        assertEachVariantComesBack(work,
                "int f(int x) {\n    x++;\n    return x;\n}\n",
                "int f(int x) {\n    log(x);\n    x++;\n    return x;\n}\n");
    }

    @Test
    @Timeout(120)
    public void anIfdefBranch(@TempDir Path work) throws Exception {
        assertEachVariantComesBack(work,
                "int f(void) {\n#ifdef DEBUG\n    trace();\n#else\n    quiet();\n#endif\n    return 0;\n}\n",
                "int f(void) {\n#ifdef DEBUG\n    trace(1);\n#else\n    quiet();\n#endif\n    return 0;\n}\n");
    }

    @Test
    @Timeout(120)
    public void anInclude(@TempDir Path work) throws Exception {
        assertEachVariantComesBack(work,
                "#include <stdio.h>\n#include \"a.h\"\n\nint main(void) {\n    return 0;\n}\n",
                "#include <stdio.h>\n#include \"b.h\"\n\nint main(void) {\n    return 0;\n}\n");
    }

    /** whitespace is part of a line: indentation and trailing blanks stay with their variant */
    @Test
    @Timeout(120)
    public void indentationAndTrailingWhitespace(@TempDir Path work) throws Exception {
        assertEachVariantComesBack(work,
                "int f(void) {\n    return 0;\n}\n",
                "int f(void) {\n\treturn 0; \n}\n");
    }

    /** a function's signature tells it apart: different parameters make different functions */
    @Test
    @Timeout(120)
    public void aFunctionSignature(@TempDir Path work) throws Exception {
        assertEachVariantComesBack(work,
                "int f(int x) {\n    return x;\n}\n",
                "int f(long x) {\n    return x;\n}\n");
    }

    /** the same function defined in both branches of an #ifdef: two equal sibling function artifacts */
    @Test
    @Timeout(120)
    public void aFunctionDefinedInBothBranchesOfAnIfdef(@TempDir Path work) throws Exception {
        assertEachVariantComesBack(work,
                "#ifdef A\nint f() {\n    return 1;\n}\n#else\nint f() {\n    return 2;\n}\n#endif\n",
                "#ifdef A\nint f() {\n    return 1;\n}\n#else\nint f() {\n    return 3;\n}\n#endif\n");
    }

    /** the same functions in another order */
    @Test
    @Timeout(120)
    public void swappedFunctions(@TempDir Path work) throws Exception {
        assertEachVariantComesBack(work,
                "int f(void) {\n    return 1;\n}\n\nint g(void) {\n    return 2;\n}\n",
                "int g(void) {\n    return 2;\n}\n\nint f(void) {\n    return 1;\n}\n");
    }

    /** an #ifdef that makes the parser see the function differently: its lines change parents */
    @Test
    @Timeout(120)
    public void anIfdefAroundAFunctionHeader(@TempDir Path work) throws Exception {
        assertEachVariantComesBack(work,
                "int f(int a) {\n    return a;\n}\nint g(void) {\n    return 0;\n}\n",
                "#ifdef X\nint f(int a) {\n#else\nint f(long a) {\n#endif\n    return a;\n}\nint g(void) {\n    return 0;\n}\n");
    }

    /** repeated identical lines ("}", blank lines, "break;") in different places */
    @Test
    @Timeout(120)
    public void repeatedIdenticalLines(@TempDir Path work) throws Exception {
        assertEachVariantComesBack(work,
                "int f(int x) {\n    switch (x) {\n    case 1:\n        x++;\n        break;\n    }\n\n    return x;\n}\n",
                "int f(int x) {\n    switch (x) {\n    case 1:\n        x++;\n        break;\n    case 2:\n        x--;\n        break;\n    }\n\n\n    return x;\n}\n");
    }

    /**
     * Through a repository, a line two functions share (CRoundTripTest#aLineSharedByTwoFunctionsIsWrittenOnce)
     * comes back once - it was written twice.
     */
    @Test
    @Timeout(120)
    public void aLineSharedByTwoFunctionsIsCheckedOutOnce(@TempDir Path work) throws Exception {
        Path content = Files.createDirectories(work.resolve("content"));
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(work.resolve(".ecco"));
            service.init();
            service.setBaseDir(content);
            Files.writeString(content.resolve("main.c"), "int f() {\n    return 1;\n} int g() {\n    return 2;\n}\n");
            service.commit("a", "A");
            Path checkout = Files.createDirectories(work.resolve("checkout"));
            service.setBaseDir(checkout);
            service.checkout("A");
            assertEquals("int f() {\n    return 1;\n} int g() {\n    return 2;\n}\n", Files.readString(checkout.resolve("main.c")));
        }
    }

    @Test
    @Timeout(120)
    public void aFinalNewline(@TempDir Path work) throws Exception {
        assertEachVariantComesBack(work, "int x;\n", "int x;");
    }
}
