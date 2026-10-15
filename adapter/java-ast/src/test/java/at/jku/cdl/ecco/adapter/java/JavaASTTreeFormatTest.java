package at.jku.cdl.ecco.adapter.java;

import at.jku.cdl.ecco.adapter.java.artifactData.ASTNodeType;
import at.jku.cdl.ecco.adapter.java.artifactData.JavaASTData;
import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Repositories committed before synchronized statements, blocks and arrow switch cases were kept,
 * or before an enum's constants were an ordered group, hold differently shaped trees - a new commit would add the same statements a second time under
 * their new parents. Committing into them is refused; checking out of them still works.
 */
public class JavaASTTreeFormatTest {

    private static void makeOld(Node node, int format) {
        if (node.getArtifact() != null && node.getArtifact().getData() instanceof JavaASTData data
                && data.getType() == ASTNodeType.PACKAGEDECLARATION)
            data.setTreeFormat(format);
        for (Node child : node.getChildren())
            makeOld(child, format);
    }

    /** Format 0: before synchronized statements, blocks and arrow cases; format 1: before the enum constants' ordered group. */
    @Test
    @Timeout(120)
    public void commitsIntoRepositoriesOfAnOlderFormatAreRefused() throws Exception {
        assertRefused(0, "synchronized");
        assertRefused(1, "enum's constants");
    }

    private static void assertRefused(int format, String reason) throws Exception {
        String source = "public class Main {\n    public static void main(String[] args) {\n        System.out.println(\"hi\");\n    }\n}\n";
        Path work = Files.createTempDirectory("java-ast-tree-format");
        Path content = Files.createDirectories(work.resolve("content"));
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(work.resolve(".ecco"));
            service.init();
            service.setBaseDir(content);
            Files.writeString(content.resolve("Main.java"), source);
            service.commit("first", "A");
            // what the previous reader stored
            for (Association association : service.getRepository().getAssociations())
                makeOld(association.getRootNode(), format);

            EccoException refused = assertThrows(EccoException.class, () -> service.commit("again", "A"));
            String message = refused.getMessage() + " " + (refused.getCause() == null ? "" : refused.getCause().getMessage());
            assertTrue(message.contains(reason) && message.contains("re-create the repository"), message);

            Path checkout = Files.createDirectories(work.resolve("checkout"));
            service.setBaseDir(checkout);
            service.checkout("A");
            assertEquals(source, Files.readString(checkout.resolve("Main.java")));
        }
    }
}
