package at.jku.isse.ecco.adapter.typescript;

import at.jku.isse.ecco.service.EccoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.typescript.data.SwitchBlockArtifactData;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.tree.Node;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The reader created switch statements, enums and variable statements as unordered nodes, so the
 * order of their clauses, members and declarations was not tracked: composing a variant from
 * several commits put a switch's `default:` before its `case Circle:` (examples/typescript_variants,
 * shape2 step 4), without an order warning.
 */
public class TypeScriptOrderTest {

    private static String checkout(String[][] commits, String configuration) throws Exception {
        Path work = Files.createTempDirectory("typescript-order");
        Path content = Files.createDirectories(work.resolve("content"));
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(work.resolve(".ecco"));
            service.init();
            service.setBaseDir(content);
            for (String[] commit : commits) {
                Files.writeString(content.resolve("main.ts"), commit[1]);
                service.commit(commit[1], commit[0]);
            }
            Path out = Files.createDirectories(work.resolve("out"));
            service.setBaseDir(out);
            service.checkout(configuration);
            return Files.readString(out.resolve("main.ts"));
        }
    }

    @Test
    @Timeout(180)
    public void switchCasesKeepTheirOrder() throws Exception {
        String base = "function f(s: any) {\n    switch (s) {\n        default:\n            return 0;\n    }\n}\n";
        String rect = "function f(s: any) {\n    switch (s) {\n        case Rect:\n            return 1;\n        default:\n            return 0;\n    }\n}\n";
        String circle = "function f(s: any) {\n    switch (s) {\n        case Circle:\n            return 2;\n        default:\n            return 0;\n    }\n}\n";
        assertEquals(circle, checkout(new String[][]{{"BASE", base}, {"BASE, RECT", rect}, {"BASE, CIRCLE", circle}}, "BASE, CIRCLE"));
    }

    @Test
    @Timeout(180)
    public void enumMembersKeepTheirOrder() throws Exception {
        String base = "enum E {\n    A,\n    Z\n}\n";
        String withB = "enum E {\n    A,\n    B,\n    Z\n}\n";
        String withC = "enum E {\n    A,\n    C,\n    Z\n}\n";
        assertEquals(withC, checkout(new String[][]{{"BASE", base}, {"BASE, WB", withB}, {"BASE, WC", withC}}, "BASE, WC"));
    }

    private static void makeSwitchesUnordered(Node node) {
        if (node.getArtifact() != null && node.getArtifact().getData() instanceof SwitchBlockArtifactData)
            ((at.jku.isse.ecco.artifact.Artifact.Op<?>) node.getArtifact()).setOrdered(false);
        for (Node child : node.getChildren())
            makeSwitchesUnordered(child);
    }

    /**
     * A repository written before switches were ordered holds unordered switch artifacts; committing
     * to it with this reader made checkouts lose the switch - so it is refused, with an explanation.
     */
    @Test
    @Timeout(180)
    public void aRepositoryWithUnorderedSwitchesIsNotCommittedTo() throws Exception {
        String source = "function f(s: any) {\n    switch (s) {\n        case 1:\n            return 1;\n        default:\n            return 0;\n    }\n}\n";
        Path work = Files.createTempDirectory("typescript-order-old");
        Path content = Files.createDirectories(work.resolve("content"));
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(work.resolve(".ecco"));
            service.init();
            service.setBaseDir(content);
            Files.writeString(content.resolve("main.ts"), source);
            service.commit("first", "A");
            // what the previous reader stored
            for (Association association : service.getRepository().getAssociations())
                makeSwitchesUnordered(association.getRootNode());

            Files.writeString(content.resolve("main.ts"), source.replace("return 1;", "return 2;"));
            EccoException refused = assertThrows(EccoException.class, () -> service.commit("second", "A, B"));
            assertTrue(String.valueOf(refused.getMessage()).contains("re-create the repository")
                    || (refused.getCause() != null && String.valueOf(refused.getCause().getMessage()).contains("re-create the repository")), refused.toString());

            Path out = Files.createDirectories(work.resolve("out"));
            service.setBaseDir(out);
            service.checkout("A");
            assertTrue(Files.readString(out.resolve("main.ts")).contains("switch (s)"), "checkouts still work");
        }
    }
}
