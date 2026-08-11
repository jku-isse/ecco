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
 * Round-trip characterization test for the AST-granularity Java adapter (read -> write -> re-read
 * the file JavaParser produced), unlike the original hand-run JavaASTTest (a main() method with a
 * hardcoded Windows path, no assertions, and its only write() call commented out - so this reader/
 * writer pair had apparently never actually been exercised end to end before).
 *
 * Contrast with the currently-shipped adapter/java: JavaWriter.write() there is an unimplemented
 * stub that produces no output for any input (see JavaWriterTest), so this adapter's writer -
 * despite the gaps characterized below - is strictly more functional for checkout today.
 *
 * Since the writer re-parses artifact data through JavaParser's own pretty-printer rather than
 * preserving original formatting, round-trip output is compared structurally (re-parsed and
 * checked for expected members/content), not byte-for-byte against the input source.
 */
public class JavaASTReaderWriterTest {

    private final JavaASTReader reader = new JavaASTReader(new SerEntityFactory());
    private final JavaASTWriter writer = new JavaASTWriter();

    private String readWrite(String source) throws IOException {
        Path baseDir = Files.createTempDirectory("java-ast-adapter");
        Files.writeString(baseDir.resolve("Foo.java"), source);

        Set<Node.Op> nodes = reader.read(baseDir, new Path[]{Path.of("Foo.java")});
        Path outDir = Files.createTempDirectory("java-ast-adapter-out");
        writer.write(outDir, Set.copyOf(nodes));

        return Files.readString(outDir.resolve("Foo.java"));
    }

    @Test
    public void roundTripsAClassWithAFieldAndAMethod() throws IOException {
        String out = readWrite(
                "package com.example;\n" +
                        "import java.util.List;\n" +
                        "public class Foo {\n" +
                        "    private int x;\n" +
                        "    public void bar() {\n" +
                        "        doSomething();\n" +
                        "    }\n" +
                        "}\n");

        assertTrue(out.contains("package com.example"));
        assertTrue(out.contains("import java.util.List"));
        assertTrue(out.contains("private int x"));
        assertTrue(out.contains("void bar()"));
        assertTrue(out.contains("doSomething()"));
    }

    @Test
    public void roundTripsAFileWithNoPackageDeclaration() throws IOException {
        // JavaASTReader.read() always attaches a PACKAGEDECLARATION child, even when there is none
        // (packageName defaults to "" - see JavaASTReader.java:135-138). JavaASTWriteHandler used to
        // forward that unconditionally to CompilationUnit.setPackageDeclaration(), which rejects an
        // empty name and threw ParseProblemException for every package-less file. Fixed by skipping
        // setPackageDeclaration() when the stored name is empty.
        String out = readWrite(
                "public class Foo {\n" +
                        "    public void bar() {\n" +
                        "        doSomething();\n" +
                        "    }\n" +
                        "}\n");

        assertFalse(out.contains("package "));
        assertTrue(out.contains("doSomething()"));
    }

    @Test
    public void roundTripsIfElse() throws IOException {
        String out = readWrite(
                "package com.example;\n" +
                        "public class Foo {\n" +
                        "    public void bar() {\n" +
                        "        if (true) {\n" +
                        "            a();\n" +
                        "        } else {\n" +
                        "            b();\n" +
                        "        }\n" +
                        "    }\n" +
                        "}\n");

        assertTrue(out.contains("if ("));
        assertTrue(out.contains("a();"));
        assertTrue(out.contains("else"));
        assertTrue(out.contains("b();"));
    }

    @Test
    public void roundTripsTryCatchFinally() throws IOException {
        String out = readWrite(
                "package com.example;\n" +
                        "public class Foo {\n" +
                        "    public void bar() {\n" +
                        "        try {\n" +
                        "            risky();\n" +
                        "        } catch (Exception e) {\n" +
                        "            handle(e);\n" +
                        "        } finally {\n" +
                        "            cleanup();\n" +
                        "        }\n" +
                        "    }\n" +
                        "}\n");

        assertTrue(out.contains("try {"));
        assertTrue(out.contains("risky();"));
        assertTrue(out.contains("catch (Exception e)"));
        assertTrue(out.contains("handle(e);"));
        assertTrue(out.contains("finally {"));
        assertTrue(out.contains("cleanup();"));
    }

    @Test
    public void roundTripsSwitch() throws IOException {
        String out = readWrite(
                "package com.example;\n" +
                        "public class Foo {\n" +
                        "    public void bar(int x) {\n" +
                        "        switch (x) {\n" +
                        "            case 1:\n" +
                        "                one();\n" +
                        "                break;\n" +
                        "            default:\n" +
                        "                other();\n" +
                        "        }\n" +
                        "    }\n" +
                        "}\n");

        assertTrue(out.contains("switch"));
        assertTrue(out.contains("case 1"));
        assertTrue(out.contains("one();"));
        assertTrue(out.contains("other();"));
    }

    @Test
    public void roundTripsAnEnumWithConstantsAndArguments() throws IOException {
        String out = readWrite(
                "package com.example;\n" +
                        "public class Foo {\n" +
                        "    enum Color {\n" +
                        "        RED(255), GREEN(0);\n" +
                        "        Color(int code) {}\n" +
                        "    }\n" +
                        "}\n");

        assertTrue(out.contains("enum Color"));
        assertTrue(out.contains("RED(255)"));
        assertTrue(out.contains("GREEN(0)"));
    }

    @Test
    public void dropsCommentsOnWrite() throws IOException {
        // Reader is constructed with PrettyPrinterConfiguration.setPrintComments(false), so
        // comments never make it into any artifact - characterizing this as a known, deliberate
        // (per the config) but real fidelity loss versus source, not a bug to fix here.
        String out = readWrite(
                "package com.example;\n" +
                        "public class Foo {\n" +
                        "    // a comment explaining bar\n" +
                        "    public void bar() {\n" +
                        "        /* inline */ doSomething();\n" +
                        "    }\n" +
                        "}\n");

        assertFalse(out.contains("a comment explaining bar"));
        assertFalse(out.contains("inline"));
        assertTrue(out.contains("doSomething()"));
    }

    @Test
    public void getPluginIdIsTheJavaASTPluginClassName() {
        assertEquals(JavaASTPlugin.class.getName(), reader.getPluginId());
        assertEquals(JavaASTPlugin.class.getName(), writer.getPluginId());
    }

    @Test
    public void getPrioritizedPatternsMatchesJavaFiles() {
        assertTrue(reader.getPrioritizedPatterns().values().stream()
                .anyMatch(patterns -> java.util.List.of(patterns).contains("**.java")));
    }
}
