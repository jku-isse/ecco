package at.jku.isse.ecco.adapter.typescript;

import at.jku.isse.ecco.adapter.typescript.data.VariableAssignmentData;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Variable statements committed before the reader kept their ";" are stored without it. Committing
 * the file again - the new artifacts are equal to the stored ones, which are kept - must give the
 * stored statements their ";" back, so checkouts have it too.
 */
public class TypeScriptSemicolonMigrationTest {

    private static void collect(Node node, List<VariableAssignmentData> result) {
        if (node.getArtifact() != null && node.getArtifact().getData() instanceof VariableAssignmentData data)
            result.add(data);
        for (Node child : node.getChildren())
            collect(child, result);
    }

    @Test
    @Timeout(120)
    public void aStatementStoredWithoutItsSemicolonGetsItOnTheNextCommit() throws Exception {
        String source = "let x: number = 1;\nconst y = 2;\n";
        Path work = Files.createTempDirectory("typescript-semicolon");
        Path content = Files.createDirectories(work.resolve("content"));
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(work.resolve(".ecco"));
            service.init();
            service.setBaseDir(content);
            Files.writeString(content.resolve("main.ts"), source);
            service.commit("first", "A");

            // what the previous reader stored
            List<VariableAssignmentData> stored = new ArrayList<>();
            for (Association association : service.getRepository().getAssociations())
                collect(association.getRootNode(), stored);
            assertEquals(2, stored.size());
            stored.forEach(data -> data.setTrailingComment(""));

            service.commit("again", "A");

            for (VariableAssignmentData data : stored)
                assertEquals(";", data.getTrailingComment(), data + " kept no semicolon");
            Path checkout = Files.createDirectories(work.resolve("checkout"));
            service.setBaseDir(checkout);
            service.checkout("A");
            assertEquals(source, Files.readString(checkout.resolve("main.ts")));
        }
    }
}
