package at.jku.cdl.ecco.adapter.java;

import at.jku.cdl.ecco.adapter.java.artifactData.JavaASTConstructorData;
import at.jku.cdl.ecco.adapter.java.artifactData.JavaASTData;
import at.jku.cdl.ecco.adapter.java.artifactData.JavaASTTryData;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Comments in call arguments, before a lambda's body, in try resources, catch and constructor
 * parameters and case labels (#52) are kept as metadata, outside the artifacts' identity: a file
 * read with them has the same artifacts as without them, so existing repositories are not split,
 * and the next commit of a file gives a repository that holds it without them its comments.
 */
public class JavaASTPartCommentTest {

    private static String method(String body) {
        return "class C {\n    void f(int[] xs, Object lock) {\n" + body + "    }\n\n    void g(int x) {\n    }\n}\n";
    }

    /** A file with such comments, and the same file without them. */
    private static final String WITH = method("        g(/* nothing */);\n"
            + "        g(xs.length // len\n        );\n"
            + "        Runnable r = () -> /* c */ g(1);\n"
            + "        try (/* c */ java.io.Reader in = null) {\n            g(1);\n        } catch (/* c */ RuntimeException e) {\n            g(2);\n        }\n"
            + "        switch(xs.length) {\n            case /* c */ 1:\n                g(1);\n        }\n"
            + "        int a = 1 + // one\n        2;\n")
            .replace("class C {\n", "class C {\n    C(/* c */ int a, int b) {\n    }\n\n");
    private static final String WITHOUT = method("        g();\n"
            + "        g(xs.length);\n"
            + "        Runnable r = () -> g(1);\n"
            + "        try (java.io.Reader in = null) {\n            g(1);\n        } catch (RuntimeException e) {\n            g(2);\n        }\n"
            + "        switch(xs.length) {\n            case 1:\n                g(1);\n        }\n"
            + "        int a = 1 + 2;\n")
            .replace("class C {\n", "class C {\n    C(int a, int b) {\n    }\n\n");
    /** What WITH is written as: all as it was, except the comment after "1 +", which JavaParser prints before the "1". */
    private static final String WITH_WRITTEN = WITH.replace("        g(xs.length // len\n        );\n", "        g(// len\n        xs.length);\n")
            .replace("        int a = 1 + // one\n        2;\n", "        int a = // one\n        1 + 2;\n");

    private static Node read(String source) throws IOException {
        Path base = Files.createTempDirectory("java-ast-part-comments");
        Files.writeString(base.resolve("C.java"), source);
        Set<Node.Op> nodes = new JavaASTReader(new SerEntityFactory()).read(base, new Path[]{Path.of("C.java")});
        return nodes.iterator().next();
    }

    private static void collect(Node node, List<Object> data) {
        if (node.getArtifact() != null)
            data.add(node.getArtifact().getData());
        for (Node child : node.getChildren())
            collect(child, data);
    }

    @Test
    public void theCommentsAreNotPartOfTheArtifactsIdentity() throws IOException {
        List<Object> with = new ArrayList<>();
        List<Object> without = new ArrayList<>();
        collect(read(WITH), with);
        collect(read(WITHOUT), without);
        assertEquals(without.size(), with.size());
        for (int i = 0; i < with.size(); i++) {
            assertEquals(without.get(i), with.get(i));
            assertEquals(without.get(i).hashCode(), with.get(i).hashCode());
            assertEquals(without.get(i).toString(), with.get(i).toString());
        }
        // and they are kept, as metadata
        JavaASTConstructorData constructor = with.stream().filter(JavaASTConstructorData.class::isInstance).map(JavaASTConstructorData.class::cast).findFirst().orElseThrow();
        assertEquals(List.of("/* c */ int a", "int b"), constructor.getParametersWithComments());
        JavaASTTryData tryData = with.stream().filter(JavaASTTryData.class::isInstance).map(JavaASTTryData.class::cast).findFirst().orElseThrow();
        assertEquals(List.of("/* c */ java.io.Reader in = null"), tryData.getExpressionsWithComments());
        assertTrue(with.stream().filter(JavaASTData.class::isInstance).map(JavaASTData.class::cast).anyMatch(d -> "/* c */ RuntimeException e".equals(d.getTextWithComments())));
        assertTrue(with.stream().filter(JavaASTData.class::isInstance).map(JavaASTData.class::cast).anyMatch(d -> "/* c */ 1".equals(d.getTextWithComments())));
        assertEquals(WITH_WRITTEN, JavaASTRoundTripTest.readWrite("C.java", WITH));
    }

    private static void forget(Node node) {
        if (node.getArtifact() != null && node.getArtifact().getData() instanceof JavaASTData data) {
            data.setComment(null);
            data.setOrphanComments(null);
            data.setTextWithComments(null);
            if (data instanceof JavaASTTryData tryData)
                tryData.setExpressionsWithComments(null);
            if (data instanceof JavaASTConstructorData constructor)
                constructor.setParametersWithComments(null);
        }
        for (Node child : node.getChildren())
            forget(child);
    }

    private static String checkout(EccoService service, Path work, String name) throws Exception {
        Path checkout = Files.createDirectories(work.resolve(name));
        service.setBaseDir(checkout);
        service.checkout("A");
        return Files.readString(checkout.resolve("C.java"));
    }

    /** The file committed without the comments, then with them: no new associations, and the checkout has them. */
    @Test
    @Timeout(120)
    public void committedWithTheCommentsTheFileGetsThem() throws Exception {
        Path work = Files.createTempDirectory("java-ast-part-comments-repo");
        Path content = Files.createDirectories(work.resolve("content"));
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(work.resolve(".ecco"));
            service.init();
            service.setBaseDir(content);
            Files.writeString(content.resolve("C.java"), WITHOUT);
            service.commit("without", "A");
            int associations = service.getRepository().getAssociations().size();
            assertEquals(WITHOUT, checkout(service, work, "checkout1"));

            service.setBaseDir(content);
            Files.writeString(content.resolve("C.java"), WITH);
            service.commit("with", "A");
            assertEquals(associations, service.getRepository().getAssociations().size());
            assertEquals(WITH_WRITTEN, checkout(service, work, "checkout2"));
        }
    }

    /** What an older adapter stored - the same artifacts without this metadata - gets it on the next commit. */
    @Test
    @Timeout(120)
    public void metadataStoredWithoutIsFilledInByTheNextCommit() throws Exception {
        Path work = Files.createTempDirectory("java-ast-part-comments-repo");
        Path content = Files.createDirectories(work.resolve("content"));
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(work.resolve(".ecco"));
            service.init();
            service.setBaseDir(content);
            Files.writeString(content.resolve("C.java"), WITH);
            service.commit("first", "A");
            for (Association association : service.getRepository().getAssociations())
                forget(association.getRootNode());
            int associations = service.getRepository().getAssociations().size();
            assertEquals(WITHOUT, checkout(service, work, "checkout1"));

            service.setBaseDir(content);
            service.commit("again", "A");
            assertEquals(associations, service.getRepository().getAssociations().size());
            assertEquals(WITH_WRITTEN, checkout(service, work, "checkout2"));
        }
    }
}
