package at.jku.isse.ecco.adapter.java;

import at.jku.isse.ecco.adapter.java.data.*;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JavaBlockReader is the reader actually wired to the "**.java" pattern in production
 * (JavaModule.configure() binds JavaBlockReader, not JavaParserReader/JavaRawLinesReader/JavaReader
 * - those appear to be unused alternatives). Its only prior test coverage
 * (adapter/java/src/integrationTest/.../AdapterTest.java) is @Disabled and hardcoded to a path on the
 * original author's Windows desktop that never existed in this repo - effectively zero real coverage.
 * These use real temp .java files (StaticJavaParser.parse() reads from disk, so this can't be tested
 * against an in-memory string) covering the reader's main structural and statement-type branches.
 */
public class JavaBlockReaderTest {

    private final JavaBlockReader reader = new JavaBlockReader(new SerEntityFactory());

    private Node.Op readClass(String source) throws IOException {
        Path baseDir = Files.createTempDirectory("java-block-reader");
        Files.writeString(baseDir.resolve("Foo.java"), source);

        Set<Node.Op> nodes = reader.read(baseDir, new Path[]{Path.of("Foo.java")});
        Node.Op pluginNode = nodes.iterator().next();
        return pluginNode.getChildren().get(0); // the class node
    }

    private String dataOf(Node.Op node) {
        return node.getArtifact().getData().toString();
    }

    @Test
    public void readsClassNameIncludingPackage() throws IOException {
        Node.Op classNode = readClass("package com.example;\npublic class Foo {}\n");

        assertEquals("com.example.Foo", ((ClassArtifactData) classNode.getArtifact().getData()).getName());
    }

    @Test
    public void readsClassNameWithoutAPackageDeclarationAsALeadingDot() throws IOException {
        // packageName defaults to "" when there's no package declaration (JavaBlockReader.java:75-77),
        // and the class name is always built as packageName + "." + className - so a package-less
        // class reads as ".Foo", not "Foo". Characterizing this as-is rather than what might seem more
        // natural, since it's what the code actually does.
        Node.Op classNode = readClass("public class Foo {}\n");

        assertEquals(".Foo", ((ClassArtifactData) classNode.getArtifact().getData()).getName());
    }

    @Test
    public void readsEachImportAsAChildOfTheClassNode() throws IOException {
        Node.Op classNode = readClass("import java.util.List;\nimport java.util.Map;\npublic class Foo {}\n");

        List<? extends Node.Op> children = classNode.getChildren();
        assertEquals(2, children.size());
        assertInstanceOf(ImportArtifactData.class, children.get(0).getArtifact().getData());
        assertEquals("import java.util.List", ((ImportArtifactData) children.get(0).getArtifact().getData()).getImportName());
        assertEquals("import java.util.Map", ((ImportArtifactData) children.get(1).getArtifact().getData()).getImportName());
    }

    @Test
    public void readsAFieldWithoutAnInitializerAsAFieldNode() throws IOException {
        Node.Op classNode = readClass("public class Foo {\n    private int x;\n}\n");

        Node.Op fieldNode = classNode.getChildren().get(0);
        assertInstanceOf(FieldArtifactData.class, fieldNode.getArtifact().getData());
        assertTrue(dataOf(fieldNode).contains("private int x"));
    }

    @Test
    public void readsAMethodSignatureAndItsStatementsAsChildren() throws IOException {
        Node.Op classNode = readClass("public class Foo {\n    public void bar() {\n        doSomething();\n    }\n}\n");

        Node.Op methodNode = classNode.getChildren().get(0);
        assertInstanceOf(MethodArtifactData.class, methodNode.getArtifact().getData());
        // unlike the C adapter's FunctionArtifactData signature (which does include the return type,
        // e.g. "voidfeatureA()"), JavaParser's MethodDeclaration.getSignature() excludes it - this is
        // just "bar()", matching Java's own overload-resolution notion of "signature".
        assertEquals("bar()", ((MethodArtifactData) methodNode.getArtifact().getData()).getSignature());

        Node.Op statementNode = methodNode.getChildren().get(0);
        assertInstanceOf(LineArtifactData.class, statementNode.getArtifact().getData());
        assertTrue(dataOf(statementNode).contains("doSomething()"));
    }

    @Test
    public void readsIfElseAsTwoSiblingBlocksWithNestedStatements() throws IOException {
        Node.Op classNode = readClass(
                "public class Foo {\n" +
                        "    public void bar() {\n" +
                        "        if (true) {\n" +
                        "            a();\n" +
                        "        } else {\n" +
                        "            b();\n" +
                        "        }\n" +
                        "    }\n" +
                        "}\n");
        Node.Op methodNode = classNode.getChildren().get(0);

        List<? extends Node.Op> methodChildren = methodNode.getChildren();
        assertEquals(2, methodChildren.size());

        Node.Op ifBlock = methodChildren.get(0);
        assertInstanceOf(BlockArtifactData.class, ifBlock.getArtifact().getData());
        assertEquals("if (true)", dataOf(ifBlock));
        assertTrue(dataOf(ifBlock.getChildren().get(0)).contains("a()"));

        Node.Op elseBlock = methodChildren.get(1);
        assertEquals("else", dataOf(elseBlock));
        assertTrue(dataOf(elseBlock.getChildren().get(0)).contains("b()"));
    }

    @Test
    public void readsAForLoopAsABlockContainingItsBody() throws IOException {
        Node.Op classNode = readClass(
                "public class Foo {\n" +
                        "    public void bar() {\n" +
                        "        for (int i = 0; i < 10; i++) {\n" +
                        "            a();\n" +
                        "        }\n" +
                        "    }\n" +
                        "}\n");
        Node.Op methodNode = classNode.getChildren().get(0);

        Node.Op forBlock = methodNode.getChildren().get(0);
        assertInstanceOf(BlockArtifactData.class, forBlock.getArtifact().getData());
        assertTrue(dataOf(forBlock).startsWith("for("));
        assertTrue(dataOf(forBlock.getChildren().get(0)).contains("a()"));
    }

    @Test
    public void readsAWhileLoopAsABlockContainingItsBody() throws IOException {
        Node.Op classNode = readClass(
                "public class Foo {\n" +
                        "    public void bar() {\n" +
                        "        while (true) {\n" +
                        "            a();\n" +
                        "        }\n" +
                        "    }\n" +
                        "}\n");
        Node.Op methodNode = classNode.getChildren().get(0);

        Node.Op whileBlock = methodNode.getChildren().get(0);
        assertEquals("while(true)", dataOf(whileBlock));
        assertTrue(dataOf(whileBlock.getChildren().get(0)).contains("a()"));
    }

    @Test
    public void readsAReturnStatementStartingAtTheReturnKeyword() throws IOException {
        Node.Op classNode = readClass("public class Foo {\n    public int bar() {\n        return 42;\n    }\n}\n");
        Node.Op methodNode = classNode.getChildren().get(0);

        Node.Op returnBlock = methodNode.getChildren().get(0);
        assertEquals("return 42;", dataOf(returnBlock));
    }

    @Test
    public void getPluginIdIsTheJavaPluginClassName() {
        assertEquals(JavaPlugin.class.getName(), reader.getPluginId());
    }

    @Test
    public void getPrioritizedPatternsMatchesJavaFiles() {
        assertTrue(List.of(reader.getPrioritizedPatterns().get(Integer.MAX_VALUE)).contains("**.java"));
    }
}
