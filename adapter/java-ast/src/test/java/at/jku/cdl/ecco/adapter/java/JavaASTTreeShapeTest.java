package at.jku.cdl.ecco.adapter.java;

import at.jku.cdl.ecco.adapter.java.artifactData.ASTNodeType;
import at.jku.cdl.ecco.adapter.java.artifactData.JavaASTData;
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
 * Locks down the exact JavaASTReader tree shapes that
 * {@code adapter/java-ast/.../view/JavaCodeViewer}'s renderNode() relies on but can't itself be
 * unit-tested against (like every other *CodeViewer in this codebase, it extends BorderPane and
 * builds ListView/SplitPane/TextArea in its constructor, which requires an initialized JavaFX
 * toolkit that this module's test setup doesn't provide). In particular:
 * <ul>
 *     <li>IF_STATEMENT's children are a FLAT list of IF_CONDITION nodes, one per if/else-if branch
 *     (not nested), with an ELSE_BRANCH only as a child of the LAST one - see
 *     JavaASTReader.addIfStatement/addIfCondition.</li>
 *     <li>TRYBLOCK's children are catch clauses, then an optional finally, then the try-body
 *     statements LAST, all as flat siblings (the try-body is not under its own sub-node) - see
 *     JavaASTReader.addTryStatement.</li>
 *     <li>SWITCH_ENTRIES' default case is labeled with the literal string "DEFAULT", not the actual
 *     Java keyword text.</li>
 *     <li>ENUM_CONSTANTS' constructor arguments are EXPRESSION children.</li>
 * </ul>
 * If JavaASTReader's tree shape ever changes, this should fail before the viewer silently starts
 * rendering nonsense.
 */
public class JavaASTTreeShapeTest {

    private final JavaASTReader reader = new JavaASTReader(new SerEntityFactory());

    private Node.Op readClass(String source) throws IOException {
        Path baseDir = Files.createTempDirectory("java-ast-tree-shape");
        Files.writeString(baseDir.resolve("Foo.java"), source);

        Set<Node.Op> nodes = reader.read(baseDir, new Path[]{Path.of("Foo.java")});
        Node.Op pluginNode = nodes.iterator().next();
        // pluginNode's children are [PACKAGEDECLARATION, TYPE_DECLARATION] for a package-less,
        // import-less single top-level class - the last child is always the class itself.
        List<? extends Node.Op> fileChildren = pluginNode.getChildren();
        return fileChildren.get(fileChildren.size() - 1);
    }

    private static ASTNodeType typeOf(Node.Op n) {
        return ((JavaASTData) n.getArtifact().getData()).getType();
    }

    private static String textOf(Node.Op n) {
        return n.getArtifact().getData().toString();
    }

    @Test
    public void ifElseIfElseIsAFlatListOfConditionsWithElseBranchOnTheLast() throws IOException {
        Node.Op classNode = readClass(
                "public class Foo {\n" +
                        "    public void bar() {\n" +
                        "        if (a) {\n" +
                        "            x();\n" +
                        "        } else if (b) {\n" +
                        "            y();\n" +
                        "        } else {\n" +
                        "            z();\n" +
                        "        }\n" +
                        "    }\n" +
                        "}\n");
        Node.Op methodNode = classNode.getChildren().get(0);
        Node.Op ifNode = methodNode.getChildren().get(0);
        assertEquals(ASTNodeType.IF_STATEMENT, typeOf(ifNode));

        List<? extends Node.Op> conditions = ifNode.getChildren();
        assertEquals(2, conditions.size(), "one IF_CONDITION per if/else-if branch, flat under IF_STATEMENT");
        assertEquals(ASTNodeType.IF_CONDITION, typeOf(conditions.get(0)));
        assertEquals(ASTNodeType.IF_CONDITION, typeOf(conditions.get(1)));

        Node.Op firstBranchStatement = conditions.get(0).getChildren().get(0);
        assertTrue(textOf(firstBranchStatement).contains("x()"));

        List<? extends Node.Op> secondConditionChildren = conditions.get(1).getChildren();
        assertTrue(textOf(secondConditionChildren.get(0)).contains("y()"));
        Node.Op elseBranch = secondConditionChildren.get(secondConditionChildren.size() - 1);
        assertEquals(ASTNodeType.ELSE_BRANCH, typeOf(elseBranch), "else belongs to the last condition, not the IF_STATEMENT itself");
        assertTrue(textOf(elseBranch.getChildren().get(0)).contains("z()"));
    }

    @Test
    public void tryCatchFinallyChildrenAreCatchThenFinallyThenBodyStatementsAsFlatSiblings() throws IOException {
        Node.Op classNode = readClass(
                "public class Foo {\n" +
                        "    public void bar() {\n" +
                        "        try {\n" +
                        "            risky();\n" +
                        "        } catch (Exception e) {\n" +
                        "            handle();\n" +
                        "        } finally {\n" +
                        "            cleanup();\n" +
                        "        }\n" +
                        "    }\n" +
                        "}\n");
        Node.Op methodNode = classNode.getChildren().get(0);
        Node.Op tryNode = methodNode.getChildren().get(0);
        assertEquals(ASTNodeType.TRYBLOCK, typeOf(tryNode));

        List<? extends Node.Op> children = tryNode.getChildren();
        assertEquals(3, children.size());
        assertEquals(ASTNodeType.CATCHCLAUSE, typeOf(children.get(0)), "catch clauses come first");
        assertEquals(ASTNodeType.FINALLY, typeOf(children.get(1)), "finally comes second");
        assertTrue(textOf(children.get(2)).contains("risky()"), "try-body statements are appended last, as siblings - not nested under their own sub-node");
        assertTrue(textOf(children.get(0).getChildren().get(0)).contains("handle()"));
        assertTrue(textOf(children.get(1)).contains("cleanup()"));
    }

    @Test
    public void defaultSwitchEntryIsLabeledWithTheLiteralStringDEFAULT() throws IOException {
        Node.Op classNode = readClass(
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
        Node.Op methodNode = classNode.getChildren().get(0);
        Node.Op switchNode = methodNode.getChildren().get(0);
        assertEquals(ASTNodeType.SWITCH_STATEMENT, typeOf(switchNode));

        List<? extends Node.Op> entries = switchNode.getChildren();
        assertEquals("1", textOf(entries.get(0)));
        assertEquals("DEFAULT", textOf(entries.get(1)));
    }

    @Test
    public void enumConstantConstructorArgumentsAreExpressionChildren() throws IOException {
        Node.Op classNode = readClass(
                "public class Foo {\n" +
                        "    enum Color {\n" +
                        "        RED(255);\n" +
                        "        Color(int code) {}\n" +
                        "    }\n" +
                        "}\n");
        Node.Op enumNode = classNode.getChildren().get(0);
        assertEquals(ASTNodeType.ENUM_DECLARATION, typeOf(enumNode));

        Node.Op redConstant = enumNode.getChildren().stream()
                .filter(n -> typeOf(n) == ASTNodeType.ENUM_CONSTANTS)
                .findFirst().orElseThrow();
        assertEquals("RED", textOf(redConstant));

        Node.Op argument = redConstant.getChildren().get(0);
        assertEquals(ASTNodeType.EXPRESSION, typeOf(argument));
        assertEquals("255", textOf(argument));
    }

    @Test
    public void abstractMethodHasNoChildrenAndItsSignatureEndsWithASemicolon() throws IOException {
        Node.Op classNode = readClass(
                "public interface Foo {\n" +
                        "    void bar();\n" +
                        "}\n");
        Node.Op methodNode = classNode.getChildren().get(0);
        assertEquals(ASTNodeType.METHOD_DECLARATION, typeOf(methodNode));
        assertTrue(methodNode.getChildren().isEmpty(), "an abstract/interface method has no body to decompose");
        assertTrue(textOf(methodNode).strip().endsWith(";"));
    }
}
