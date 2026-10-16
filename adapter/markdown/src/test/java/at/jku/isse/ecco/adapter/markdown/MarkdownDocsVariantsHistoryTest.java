package at.jku.isse.ecco.adapter.markdown;

import at.jku.isse.ecco.adapter.markdown.data.SectionArtifactData;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The twelve variants of examples/markdown_docs_variants committed in order into one repository:
 * after each commit, every variant committed so far checks out byte for byte. V11 is the first
 * commit that drops features of the one before; it once left an association holding one blank
 * line near "### Docker" with a TROUBLESHOOTING disjunct in its condition, so checking out V11
 * gave that blank line twice.
 */
public class MarkdownDocsVariantsHistoryTest {

    @TempDir
    Path tmp;

    @Test
    @Timeout(600)
    public void everyCommittedVariantComesBackAfterEachCommit() throws Exception {
        Path examples = examples();
        List<Path> variants;
        try (var list = Files.list(examples)) {
            variants = list.filter(p -> p.getFileName().toString().matches("V\\d+")).sorted().toList();
        }
        assertEquals(12, variants.size());

        List<String> wrong = new ArrayList<>();
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(this.tmp.resolve(".ecco"));
            service.init();
            for (int n = 0; n < variants.size(); n++) {
                Path variant = variants.get(n);
                service.setBaseDir(variant);
                service.commit(variant.getFileName().toString(), configuration(variant));
                for (int i = 0; i <= n; i++) {
                    Path committed = variants.get(i);
                    Path out = Files.createDirectories(this.tmp.resolve("after" + (n + 1)).resolve(committed.getFileName().toString()));
                    service.setBaseDir(out);
                    service.checkout(configuration(committed));
                    String expected = Files.readString(committed.resolve("docs.md"));
                    Path file = out.resolve("docs.md");
                    String actual = Files.exists(file) ? Files.readString(file) : "<no file>";
                    if (!actual.equals(expected))
                        wrong.add("after " + variants.get(n).getFileName() + ", " + committed.getFileName() + ": " + firstDifference(expected, actual));
                }
                if (!wrong.isEmpty())
                    break;
            }
            assertEquals(List.of(), wrong, () -> "associations:\n" + associations(service));
            int sections = 0;
            for (Association association : service.getRepository().getAssociations())
                sections += sections((Node.Op) association.getRootNode());
            assertTrue(sections > 0, "the files were read by the Markdown adapter, not the text adapter");
        }
    }

    private static int sections(Node.Op node) {
        int sections = node.getArtifact() != null && node.getArtifact().getData() instanceof SectionArtifactData ? 1 : 0;
        for (Node.Op child : node.getChildren())
            sections += sections(child);
        return sections;
    }

    private static Path examples() {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            Path examples = dir.resolve("examples").resolve("markdown_docs_variants");
            if (Files.isDirectory(examples))
                return examples;
        }
        throw new IllegalStateException("examples/markdown_docs_variants not found");
    }

    private static String configuration(Path variant) throws Exception {
        return Files.readString(variant.resolve(".config")).strip();
    }

    private static String firstDifference(String expected, String actual) {
        String[] e = expected.split("\n", -1);
        String[] a = actual.split("\n", -1);
        for (int i = 0; i < Math.max(e.length, a.length); i++) {
            String el = i < e.length ? e[i] : "<end>";
            String al = i < a.length ? a[i] : "<end>";
            if (!el.equals(al))
                return "line " + (i + 1) + " expected [" + el + "] got [" + al + "] (" + e.length + " vs " + a.length + " lines)";
        }
        return "same lines, different bytes";
    }

    /** Each association's condition and the artifacts its own tree holds as unique nodes. */
    private static String associations(EccoService service) {
        StringBuilder text = new StringBuilder();
        for (Association association : service.getRepository().getAssociations()) {
            text.append("  ").append(association.computeCondition().toLogicString()).append('\n');
            dump((Node.Op) association.getRootNode(), "    ", text);
        }
        return text.toString();
    }

    private static void dump(Node.Op node, String indent, StringBuilder text) {
        if (node.getArtifact() != null && node.isUnique())
            text.append(indent).append(String.valueOf(node.getArtifact().getData()).replace("\n", "\\n")).append('\n');
        for (Node.Op child : node.getChildren())
            dump(child, indent + "  ", text);
    }
}
