package at.jku.cdl.ecco.adapter.java;

import at.jku.cdl.ecco.adapter.java.artifactData.JavaASTData;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.tree.Node;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.comments.Comment;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static at.jku.cdl.ecco.adapter.java.JavaASTRoundTripTest.readWrite;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The comments the Java (AST) adapter still lost after #52 (gap #65): in field initializers, method
 * headers, if conditions, before an else-if, between case labels and inside a case, a file's second
 * header comment, and a comment before another comment - JavaParser gives a node one comment, and
 * the others were written at the end of the block, or not at all in a switch. Like the other
 * comments they are metadata, outside the artifacts' identity.
 */
public class JavaASTRemainingCommentTest {

    private static final JavaParser PARSER = new JavaParser(new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21));

    /** Written back as it was. */
    private static final String EXACT = "/*\n * License.\n */\n/*\n * Header.\n */\npackage p;\n\n"
            + "class C {\n\n    void f(int x) {\n"
            + "        // two lines\n        // of comment\n        g();\n"
            + "        switch(x) {\n            case 1:\n                g();\n                break;\n"
            + "            // case 2:\n            // case 3:\n            default:\n"
            + "                // first\n                // second\n                g();\n        }\n"
            + "        if (x > 0) {\n            g();\n        } else // negative\n        if (x < 0) {\n            h();\n        }\n"
            + "    }\n}\n";

    /** Kept, but some where JavaParser prints them: before the code they were behind, inside a block. */
    private static final String MOVED = "class C {\n"
            + "    static final String[] COLORS = { \"red\", // the first\n    \"blue\" };\n\n"
            + "    /**\n     * Doc.\n     */\n    @Override // why\n    public String toString() {\n"
            + "        for (int i = 0; i < 3; i++) // more\n            g();\n"
            + "        if (x > 0 /* positive */) {\n            g();\n        }\n"
            + "        if (x > 0)\n            g();\n            // else if (x < 0)\n            //     h();\n        else\n            h();\n"
            + "        g(h(new Object() {\n        }) // after the last argument\n        );\n"
            + "        return \"\";\n    }\n\n"
            + "    private enum State {\n\n        ON,\n        OFF\n        //#if feature\n        , WAIT\n        // after the last constant\n    }\n}\n";

    @Test
    public void commentsBeforeCommentsAndInSwitchesAndElseIfsAreWrittenWhereTheyWere() throws IOException {
        // the first line of EXACT's class body is empty in the source only: the writer separates members, it does not start with an empty line
        String source = EXACT.replace("class C {\n\n", "class C {\n");
        assertEquals(source, readWrite("C.java", source));
    }

    @Test
    public void commentsInInitializersHeadersAndConditionsAreKept() throws IOException {
        String written = readWrite("C.java", MOVED);
        assertEquals(comments(MOVED), comments(written), written);
        assertEquals(code(MOVED), code(written));
    }

    @Test
    public void theCommentsAreNotPartOfTheArtifactsIdentity() throws IOException {
        List<Object> with = new ArrayList<>();
        List<Object> without = new ArrayList<>();
        collect(read(EXACT + MOVED.replace("class C", "class D")), with);
        collect(read(code(EXACT) + code(MOVED.replace("class C", "class D"))), without);
        assertEquals(without, with);
        assertEquals(without.stream().map(Object::hashCode).toList(), with.stream().map(Object::hashCode).toList());
        // and kept, as metadata, taken over by the next commit like the comments
        assertFalse(with.stream().map(JavaASTData.class::cast).allMatch(data -> data.getLeadingComments().isEmpty()));
        JavaASTData withLeading = with.stream().map(JavaASTData.class::cast).filter(data -> !data.getLeadingComments().isEmpty()).findFirst().orElseThrow();
        JavaASTData older = (JavaASTData) without.get(with.indexOf(withLeading));
        older.adoptMetadataFrom(withLeading);
        assertEquals(withLeading.getLeadingComments(), older.getLeadingComments());
    }

    private static List<String> comments(String source) {
        CompilationUnit cu = PARSER.parse(source).getResult().orElseThrow();
        // JavaParser gives a line comment behind code to every node ending on its line: each comment once
        Set<Comment> all = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        all.addAll(cu.getAllContainedComments());
        cu.getComment().ifPresent(all::add);
        List<String> comments = new ArrayList<>();
        for (Comment comment : all)
            comments.add(comment.getContent().replaceAll("(?m)^\\s*\\*", "").replaceAll("\\s+", " ").trim());
        comments.sort(null);
        return comments;
    }

    private static String code(String source) {
        return PARSER.parse(source).getResult().orElseThrow()
                .toString(new com.github.javaparser.printer.configuration.PrettyPrinterConfiguration().setPrintComments(false));
    }

    private static Node read(String source) throws IOException {
        Path base = Files.createTempDirectory("java-ast-remaining-comments");
        Files.writeString(base.resolve("C.java"), source);
        Set<Node.Op> nodes = new JavaASTReader(new SerEntityFactory()).read(base, new Path[]{Path.of("C.java")});
        return nodes.iterator().next();
    }

    private static void collect(Node node, List<Object> data) {
        if (node.getArtifact() != null && node.getArtifact().getData() instanceof JavaASTData)
            data.add(node.getArtifact().getData());
        node.getChildren().stream()
                .sorted(java.util.Comparator.comparing(child -> String.valueOf(child.getArtifact().getData())))
                .forEach(child -> collect(child, data));
    }
}
