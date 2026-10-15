package at.jku.isse.ecco.adapter.c;

import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class CRoundTripTest {

    @Test
    public void emptyLinesSurviveReadAndWrite() throws IOException {
        String source = """
                #include <stdio.h>

                int main() {
                    int x = 1;

                    printf("%d\\n", x);

                    return 0;
                }

                """;
        assertArrayEquals(source.getBytes(StandardCharsets.UTF_8), roundTrip(source.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void crlfAndMissingFinalNewlineSurvive() throws IOException {
        byte[] source = "#ifndef H\r\n#define H\r\nint f();\r\n#endif".getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(source, roundTrip(source));
    }

    @Test
    public void latin1FilesCanBeCommittedAndSurvive() throws IOException {
        byte[] source = "/* caf\u00e9 */\nint f() {\n    return 1;\n}\n".getBytes(StandardCharsets.ISO_8859_1);
        assertArrayEquals(source, roundTrip(source));
    }

    /**
     * Preprocessor lines are hidden from the parser (C.g4: Directive, MultiLineMacro) and kept as
     * lines, so nesting, guards, continued macros and pragmas come back as they were - even where
     * both branches of an #ifdef open a function.
     */
    @Test
    public void preprocessorDirectivesSurvive() throws IOException {
        assertExact("""
                #ifndef SHAPES_H
                #define SHAPES_H
                #pragma once
                #define MAX(a, b) \\
                    ((a) > (b) ? (a) : (b))
                #if defined(A) && B > 1
                # ifdef C
                int f(int x) {
                # elif D
                int f(long x) {
                # else
                int f(short x) {
                # endif
                #else
                int f(void) {
                #endif
                    return MAX(1, 2);
                }
                #endif /* SHAPES_H */
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
                        /* fallthrough */
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
    public void commentsTabsAndTrailingBlanksSurvive() throws IOException {
        assertExact("/* first line */\n\tint\tx;   \nint f() {\t\n\treturn 0; // done \n}\n// last line, no newline");
    }

    @Test
    public void anEmptyFileStaysEmpty() throws IOException {
        assertExact("");
        assertExact("\n");
        assertExact("\r\n\r\n");
    }

    /** lines the parser does not understand are kept as lines, before and inside functions */
    @Test
    public void unparseableCodeSurvives() throws IOException {
        assertExact("int f( {\n  @@@ ???\n}}}\nint g() {\n  $$ not C\n  return 1;\n}\n");
        assertExact("int f() {\n  if (x) {\n    return 1;\n}\n");
    }

    /** A file mixing line separators keeps each (gap #53): it got the first one throughout - tinycc's tcctest.c mixes them on purpose. */
    @Test
    public void mixedLineSeparatorsAreKept() throws IOException {
        assertExact("int a;\r\nint b;\nint c;\r\n");
        assertExact("int f() {\r\n    return 1;\n}\r\n");
    }

    private static final String SHARED_LINE = "int f() {\n    return 1;\n} int g() {\n    return 2;\n}\n";

    /**
     * A function that starts on the line where the previous one ends stays plain lines, as in the
     * C++ adapter: as a function, the shared line was a child of both function nodes and was
     * written twice ("} int g() {"; a file of one line came back as two copies of it).
     */
    @Test
    public void aLineSharedByTwoFunctionsIsWrittenOnce() throws IOException {
        assertExact(SHARED_LINE);
        assertExact("int f() { return 1; } int g() { return 2; }\n");
    }

    private static void assertExact(String source) throws IOException {
        assertEquals(source, roundTripText(source));
    }

    private static String roundTripText(String source) throws IOException {
        return new String(roundTrip(source.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
    }

    private static byte[] roundTrip(byte[] source) throws IOException {
        Path baseDir = Files.createTempDirectory("c-roundtrip");
        Files.write(baseDir.resolve("main.c"), source);
        Set<Node> read = Set.copyOf(new CReader(new SerEntityFactory()).read(baseDir, new Path[]{Path.of("main.c")}));

        Path outputDir = Files.createTempDirectory("c-roundtrip-out");
        Path[] written = new CWriter().write(outputDir, read);

        assertEquals(1, written.length);
        return Files.readAllBytes(written[0]);
    }
}
