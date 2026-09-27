package at.jku.cdl.ecco.adapter.java;

import at.jku.cdl.ecco.adapter.java.artifactData.JavaASTData;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Repositories committed before the adapter kept comments hold none. Committing the file again -
 * its artifacts are equal to the stored ones, which are kept - must give them their comments, so
 * checkouts have them.
 */
public class JavaASTCommentMigrationTest {

    private static void forget(Node node) {
        if (node.getArtifact() != null && node.getArtifact().getData() instanceof JavaASTData data) {
            data.setComment(null);
            data.setOrphanComments(null);
        }
        for (Node child : node.getChildren())
            forget(child);
    }

    @Test
    @Timeout(120)
    public void commentsStoredWithoutAreFilledInByTheNextCommit() throws Exception {
        String source = "/** Doc. */\npublic class Main {\n    // greets\n    public static void main(String[] args) {\n        System.out.println(\"hi\");\n    }\n}\n";
        Path work = Files.createTempDirectory("java-ast-comment-migration");
        Path content = Files.createDirectories(work.resolve("content"));
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(work.resolve(".ecco"));
            service.init();
            service.setBaseDir(content);
            Files.writeString(content.resolve("Main.java"), source);
            service.commit("first", "A");
            // what the previous reader stored
            for (Association association : service.getRepository().getAssociations())
                forget(association.getRootNode());

            service.commit("again", "A");

            Path checkout = Files.createDirectories(work.resolve("checkout"));
            service.setBaseDir(checkout);
            service.checkout("A");
            String written = Files.readString(checkout.resolve("Main.java"));
            assertTrue(written.contains("Doc.") && written.contains("greets"), written);
        }
    }
}
