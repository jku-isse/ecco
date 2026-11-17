package at.jku.cdl.ecco.adapter.java;

import at.jku.isse.ecco.service.EccoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Initializer blocks and field initializers run in the order they are written, so their order is
 * meaning: they were unordered children of the type's node, like its methods, and were written after
 * all fields - a block before a field reading what it computed, or a block one variant inserted
 * before another, ran in another order. They are in the ordered group with the fields now.
 */
public class JavaASTInitializerOrderTest {

    private static final Path FILE = Path.of("p", "C.java");

    /** x is 1, the block doubles it, y reads it: 2. Written fields first, y was 1. */
    private static final String ALONE = "package p;\n\npublic class C {\n"
            + "    static int x = 1;\n\n"
            + "    static {\n        x = x * 2;\n    }\n\n"
            + "    static int y = x;\n}\n";

    /** Adds a block before the doubling one: 22. Written after it, 12. */
    private static final String INSERTED = "package p;\n\npublic class C {\n"
            + "    static int x = 1;\n\n"
            + "    static {\n        x = x + 10;\n    }\n\n"
            + "    static {\n        x = x * 2;\n    }\n\n"
            + "    static int y = x;\n}\n";

    @Test
    @Timeout(120)
    public void aBlockStaysBetweenTheFieldsItWasBetween() throws Exception {
        assertEquals(order(ALONE), order(checkouts(List.of(ALONE)).getFirst()));
    }

    @Test
    @Timeout(120)
    public void aBlockOneVariantInsertedKeepsItsPlace() throws Exception {
        List<String> checkouts = checkouts(List.of(ALONE, INSERTED));
        assertEquals(order(ALONE), order(checkouts.get(0)), "A");
        assertEquals(order(INSERTED), order(checkouts.get(1)), "B");
    }

    /** The fields and blocks of [source], in the order written - what decides the values. */
    private static List<String> order(String source) {
        List<String> order = new ArrayList<>();
        for (String line : source.split("\n")) {
            String t = line.trim();
            if (t.startsWith("static int") || t.startsWith("x = "))
                order.add(t);
        }
        return order;
    }

    /** Commits each of [variants] under a feature of its own (A, B, ...), then checks each out. */
    private static List<String> checkouts(List<String> variants) throws Exception {
        Path work = Files.createTempDirectory("java-ast-initializers");
        Path content = Files.createDirectories(work.resolve("content"));
        List<String> out = new ArrayList<>();
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(work.resolve(".ecco"));
            service.init();
            service.setBaseDir(content);
            Files.createDirectories(content.resolve(FILE).getParent());
            for (int i = 0; i < variants.size(); i++) {
                Files.writeString(content.resolve(FILE), variants.get(i));
                service.commit("v" + i, String.valueOf((char) ('A' + i)));
            }
            for (int i = 0; i < variants.size(); i++) {
                String feature = String.valueOf((char) ('A' + i));
                Path checkout = Files.createDirectories(work.resolve("checkout-" + feature));
                service.setBaseDir(checkout);
                service.checkout(feature);
                out.add(Files.readString(checkout.resolve(FILE)));
            }
        }
        return out;
    }
}
