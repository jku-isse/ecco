package at.jku.cdl.ecco.adapter.java;

import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The adapter rebuilds a file from its tree and prints it with JavaParser, so the layout is
 * JavaParser's - but no comment may get lost (every checkout used to drop all of them), and a class
 * body does not start with an empty line.
 */
public class JavaASTCommentTest {

    private static String readWrite(String source) throws IOException {
        Path base = Files.createTempDirectory("java-ast-comments");
        Files.writeString(base.resolve("F.java"), source);
        Set<Node.Op> nodes = new JavaASTReader(new SerEntityFactory()).read(base, new Path[]{Path.of("F.java")});
        Path out = Files.createTempDirectory("java-ast-comments-out");
        new JavaASTWriter().write(out, Set.copyOf(nodes));
        return Files.readString(out.resolve("F.java"));
    }

    @Test
    public void aSimpleClassIsWrittenAsItWasRead() throws IOException {
        String source = "public class Main {\n    public static void main(String[] args) {\n        System.out.println(\"hi\");\n    }\n}\n";
        assertEquals(source, readWrite(source));
    }

    @Test
    public void commentsFollowedByAnEmptyLineStayInPlace() throws IOException {
        // JavaParser attributes these to no node; they used to be written at the end of their file/class
        String written = readWrite("/**\n * File header.\n */\n\nimport java.util.List;\n\npublic class H {\n\n"
                + "    void first() {\n    }\n\n    // ---- section ----\n\n    void second() {\n    }\n}\n");
        assertTrue(written.indexOf("File header.") < written.indexOf("import java.util.List"), written);
        assertTrue(written.indexOf("void first()") < written.indexOf("---- section ----")
                && written.indexOf("---- section ----") < written.indexOf("void second()"), written);
    }

    @Test
    public void everyCommentIsKept() throws IOException {
        String source = "/* License header */\npackage p;\n\n// imports\nimport java.util.List;\n\n/** Doc. */\npublic class A {\n\n    // a field\n    private int x = 1;\n\n"
                + "    /** Javadoc of toString. */\n    @Override\n    public String toString() {\n        return \"A\"; // trailing\n    }\n\n"
                + "    /* block */\n    int f(int a) {\n        // before if\n        if (a > 0) {\n            // then\n            return a;\n        } else {\n            return -a; // negated\n        }\n    }\n\n"
                + "    A() {\n        super();\n        // end of constructor\n    }\n\n"
                + "    void g(java.util.List<String> xs) {\n        for (String s : xs) {\n            // in loop\n            System.out.println(s);\n        }\n"
                + "        try {\n            h();\n            // end of try\n        } catch (RuntimeException e) {\n            // swallowed\n        } finally {\n            // finally\n            h();\n        }\n"
                + "        switch (xs.size()) {\n            // first case\n            case 0:\n                h();\n                break;\n            default:\n                h();\n        }\n        // end of method\n    }\n\n"
                + "    void h() {\n    }\n\n    static {\n        // in initializer\n    }\n    // end of class\n}\n// end of file\n";
        String written = readWrite(source);
        for (String comment : new String[]{"License header", "imports", "Doc.", "a field", "Javadoc of toString.", "trailing", "block",
                "before if", "then", "negated", "end of constructor", "in loop", "end of try", "swallowed", "finally", "first case",
                "end of method", "in initializer", "end of class", "end of file"})
            assertTrue(written.contains(comment), "comment \"" + comment + "\" lost:\n" + written);
    }

    @Test
    public void commentsInsideStatementsAreKept() throws IOException {
        // they used to be lost: a statement is identified by its text without comments
        String out = readWrite("class F {\n" +
                "    void f(java.util.List<String> list) {\n" +
                "        list.forEach(s -> {\n" +
                "            // inside a lambda\n" +
                "            System.out.println(s);\n" +
                "        });\n" +
                "        int sum = 1 + /* inside an expression */ 2;\n" +
                "    }\n" +
                "}\n");
        assertTrue(out.contains("// inside a lambda"), out);
        assertTrue(out.contains("/* inside an expression */"), out);
    }

    @Test
    public void innerCommentsDoNotChangeWhichArtifactAStatementIs() throws IOException {
        String with = "class F { void f() { int sum = 1 + /* note */ 2; } }";
        String without = "class F { void f() { int sum = 1 + 2; } }";
        assertEquals(statementData(without), statementData(with));
    }

    private static at.jku.isse.ecco.artifact.ArtifactData statementData(String source) throws IOException {
        Path base = Files.createTempDirectory("java-ast-comments-identity");
        Files.writeString(base.resolve("F.java"), source);
        Set<Node.Op> nodes = new JavaASTReader(new SerEntityFactory()).read(base, new Path[]{Path.of("F.java")});
        java.util.List<at.jku.isse.ecco.artifact.ArtifactData> statements = new java.util.ArrayList<>();
        java.util.ArrayDeque<Node> todo = new java.util.ArrayDeque<>(nodes);
        while (!todo.isEmpty()) {
            Node node = todo.pop();
            if (node.getArtifact() != null && node.getArtifact().getData() instanceof at.jku.cdl.ecco.adapter.java.artifactData.JavaASTData data
                    && data.getType() == at.jku.cdl.ecco.adapter.java.artifactData.ASTNodeType.STATEMENT)
                statements.add(data);
            todo.addAll(node.getChildren());
        }
        assertEquals(1, statements.size());
        return statements.get(0);
    }
}
