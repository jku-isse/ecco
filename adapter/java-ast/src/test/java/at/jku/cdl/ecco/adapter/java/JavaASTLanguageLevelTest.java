package at.jku.cdl.ecco.adapter.java;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Both JavaASTReader (new JavaParser()) and JavaASTWriteHandler (StaticJavaParser) used to default
 * to ParserConfiguration.LanguageLevel.POPULAR = JAVA_11, so common post-11 constructs failed - some
 * loudly, but pattern-matching switch and module-info.java were silently reduced to an empty file
 * with no error at all (see JavaASTReader's PARSER_CONFIGURATION javadoc). Fixed by bumping both to
 * JAVA_18 (the highest non-preview level this JavaParser version, 3.25.8, offers) and by failing
 * loudly - throwing EccoException - on any non-empty ParseResult.getProblems(), instead of silently
 * proceeding with whatever (possibly truncated) CompilationUnit happened to come back.
 *
 * This file characterizes both what that fixed and what it deliberately didn't (records, Java 21's
 * pattern-matching switch, module-info.java support) - those remain broken, but now loudly instead
 * of via silent data loss.
 */
public class JavaASTLanguageLevelTest {

    private final JavaASTReader reader = new JavaASTReader(new SerEntityFactory());
    private final JavaASTWriter writer = new JavaASTWriter();

    private String readWrite(String fileName, String source) throws IOException {
        Path baseDir = Files.createTempDirectory("java-ast-language-level");
        Files.writeString(baseDir.resolve(fileName), source);

        Set<Node.Op> nodes = reader.read(baseDir, new Path[]{Path.of(fileName)});
        Path outDir = Files.createTempDirectory("java-ast-language-level-out");
        writer.write(outDir, Set.copyOf(nodes));

        return Files.readString(outDir.resolve(fileName));
    }

    @Test
    public void sealedInterfaceWithPermitsRoundTrips() throws IOException {
        String out = readWrite("Foo.java",
                "package com.example;\n" +
                        "public sealed interface Shape permits Circle, Square {\n" +
                        "}\n" +
                        "final class Circle implements Shape {\n" +
                        "}\n" +
                        "final class Square implements Shape {\n" +
                        "}\n");

        assertTrue(out.contains("sealed interface Shape permits Circle, Square"));
        assertTrue(out.contains("final class Circle implements Shape"));
    }

    @Test
    public void patternMatchingInstanceofRoundTrips() throws IOException {
        String out = readWrite("Foo.java",
                "package com.example;\n" +
                        "public class Foo {\n" +
                        "    public void bar(Object o) {\n" +
                        "        if (o instanceof String s) {\n" +
                        "            System.out.println(s.length());\n" +
                        "        }\n" +
                        "    }\n" +
                        "}\n");

        assertTrue(out.contains("instanceof String s"));
        assertTrue(out.contains("s.length()"));
    }

    @Test
    public void switchExpressionWithArrowSyntaxRoundTrips() throws IOException {
        String out = readWrite("Foo.java",
                "package com.example;\n" +
                        "public class Foo {\n" +
                        "    public int bar(int x) {\n" +
                        "        return switch (x) {\n" +
                        "            case 1 -> 10;\n" +
                        "            default -> 0;\n" +
                        "        };\n" +
                        "    }\n" +
                        "}\n");

        assertTrue(out.contains("switch"));
        assertTrue(out.contains("case 1"));
        assertTrue(out.contains("->"));
    }

    @Test
    public void textBlockInternalLineStructureIsPreservedNotFlattened() throws IOException {
        // JavaASTData.unformattedString() used to collapse every captured fragment's tabs/newlines
        // to spaces - harmless for most constructs (JavaParser reformats them canonically anyway),
        // but a text block's value IS its internal line structure, and its opening delimiter
        // requires a newline immediately after it. Fixed to normalize line endings without
        // discarding them; this locks down the fix.
        String out = readWrite("Foo.java",
                "public class Foo {\n" +
                        "    String s = \"\"\"\n" +
                        "        hello\n" +
                        "        world\n" +
                        "        \"\"\";\n" +
                        "}\n");

        assertTrue(out.contains("\"\"\"\n        hello\n        world"),
                "the line break between \"hello\" and \"world\" must survive the round trip, not collapse into \"hello     world\"");
    }

    @Test
    public void simpleRecordRoundTrips() throws IOException {
        // StaticJavaParser.parseTypeDeclaration()'s TypeDeclarationParseStart grammar entry point
        // has no "record" production at all, independent of configured LanguageLevel - a JavaParser
        // grammar limitation in that specific entry point, not something the language-level bump
        // alone could fix. Worked around in JavaASTWriteHandler.parseTypeDeclarationText() by
        // parsing as a throwaway compilation unit instead, which does support records.
        String out = readWrite("Foo.java", "public record Point(int x, int y) {\n}\n");

        assertTrue(out.contains("record Point(int x, int y)"));
    }

    @Test
    public void recordCompactConstructorRoundTripsWithItsBody() throws IOException {
        // A compact constructor ("public Point { ... }") is a CompactConstructorDeclaration, a
        // distinct AST node type from ConstructorDeclaration that JavaASTReader's
        // extractConstructors() used to simply not look for - its body was silently dropped on
        // every round trip. Fixed by adding a dedicated RecordDeclaration.getCompactConstructors()
        // branch (see JavaASTConstructorData.isCompact()).
        String out = readWrite("Foo.java",
                "public record Point(int x, int y) {\n" +
                        "    public Point {\n" +
                        "        if (x < 0) throw new IllegalArgumentException();\n" +
                        "    }\n" +
                        "}\n");

        assertTrue(out.contains("public Point {"));
        assertTrue(out.contains("throw new IllegalArgumentException()"));
    }

    @Test
    public void patternMatchingSwitchFailsLoudlyInsteadOfSilentlyTruncatingTheFile() throws IOException {
        // Java 21's finalized pattern-matching switch isn't representable by this JavaParser
        // version's grammar even at JAVA_18 (the highest non-preview level offered) - before this
        // fix, that silently truncated the whole class to nothing with no error at all. Now it's a
        // loud, safe failure instead.
        Path baseDir = Files.createTempDirectory("java-ast-language-level-pms");
        Files.writeString(baseDir.resolve("Foo.java"),
                "public class Foo {\n" +
                        "    public String bar(Object o) {\n" +
                        "        return switch (o) {\n" +
                        "            case String s -> s;\n" +
                        "            default -> \"?\";\n" +
                        "        };\n" +
                        "    }\n" +
                        "}\n");

        assertThrows(EccoException.class, () -> reader.read(baseDir, new Path[]{Path.of("Foo.java")}));
    }
}
