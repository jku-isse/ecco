package at.jku.isse.ecco.service;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The "fun.txt" history in which a line ended up in the wrong association: one line per feature,
 * committed incrementally (bla; bla, ble; bla, ble, blu), then a fourth commit that skips the middle
 * feature (bla, blu). The association with condition "bla" was left empty and the "bla" line was
 * held by an association whose condition did not hold for "bla, blu" - a checkout of "bla, blu"
 * lost the line without a warning. Every committed variant, and every single feature, must come back.
 */
public class WrongAssociationMembershipTest {

    private static final String BLA = "bla, bla, bla\n";
    private static final String BLE = "ble, ble, ble\n";
    private static final String BLU = "blu, blu, blu\n";

    @Test
    @Timeout(120)
    public void aCommitSkippingAMiddleFeatureKeepsEachLineWithItsFeature(@TempDir Path tmp) throws Exception {
        Map<String, String> commits = new LinkedHashMap<>();
        commits.put("bla", BLA);
        commits.put("bla, ble", BLA + BLE);
        commits.put("bla, ble, blu", BLA + BLE + BLU);
        commits.put("bla, blu", BLA + BLU);

        // a feature checked out alone has its line; the final newline came with "bla"
        Map<String, String> expected = new LinkedHashMap<>(commits);
        expected.put("ble", BLE.strip());
        expected.put("blu", BLU.strip());

        Path repo = tmp.resolve(".ecco");
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repo);
            service.init();
            int i = 0;
            for (Map.Entry<String, String> commit : commits.entrySet()) {
                Path variant = Files.createDirectories(tmp.resolve("v" + i++));
                Files.writeString(variant.resolve("fun.txt"), commit.getValue());
                service.setBaseDir(variant);
                service.commit("c" + i, commit.getKey());
            }
            assertCheckouts(service, tmp.resolve("in-session"), expected);
            assertEachLineHeldByItsFeature(service);
        }
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(repo);
            service.open();
            assertCheckouts(service, tmp.resolve("reopened"), expected);
            assertEachLineHeldByItsFeature(service);
        }
    }

    /** The association holding a feature's line has that feature, and nothing else, as its condition. */
    private static void assertEachLineHeldByItsFeature(EccoService service) {
        Map<String, String> holders = new LinkedHashMap<>();
        for (Association association : service.getRepository().getAssociations()) {
            List<String> lines = new ArrayList<>();
            lines((Node.Op) association.getRootNode(), lines);
            for (String line : lines)
                holders.put(line, association.computeCondition().toLogicString().replaceAll("_[0-9a-f_]+", ""));
        }
        assertEquals(Map.of("bla, bla, bla", "bla", "ble, ble, ble", "ble", "blu, blu, blu", "blu"), holders,
                () -> "associations:\n" + associations(service));
    }

    private static void lines(Node.Op node, List<String> lines) {
        if (node.isUnique() && node.getArtifact() != null && String.valueOf(node.getArtifact().getData()).matches("b.., b.., b.."))
            lines.add(String.valueOf(node.getArtifact().getData()));
        for (Node.Op child : node.getChildren())
            lines(child, lines);
    }

    private static void assertCheckouts(EccoService service, Path root, Map<String, String> expected) throws Exception {
        List<String> wrong = new ArrayList<>();
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            Path out = Files.createDirectories(root.resolve(entry.getKey().replaceAll("[^a-z]", "_")));
            service.setBaseDir(out);
            service.checkout(entry.getKey());
            Path file = out.resolve("fun.txt");
            String actual = Files.exists(file) ? Files.readString(file) : "<no file>";
            if (!actual.equals(entry.getValue()))
                wrong.add(entry.getKey() + ": expected " + show(entry.getValue()) + " got " + show(actual));
        }
        assertEquals(List.of(), wrong, () -> "associations:\n" + associations(service));
    }

    private static String show(String text) {
        return "[" + text.replace("\n", "\\n") + "]";
    }

    /** Each association's condition and the lines its own tree holds. */
    static String associations(EccoService service) {
        StringBuilder text = new StringBuilder();
        for (Association association : service.getRepository().getAssociations()) {
            text.append("  ").append(association.computeCondition().toLogicString()).append('\n');
            dump((Node.Op) association.getRootNode(), "    ", text);
        }
        return text.toString();
    }

    private static void dump(Node.Op node, String indent, StringBuilder text) {
        if (node.getArtifact() != null && node.isUnique())
            text.append(indent).append(node.getArtifact().getData()).append('\n');
        for (Node.Op child : node.getChildren())
            dump(child, indent + "  ", text);
    }
}
