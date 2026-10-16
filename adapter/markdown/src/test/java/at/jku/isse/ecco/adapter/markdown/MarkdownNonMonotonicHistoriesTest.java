package at.jku.isse.ecco.adapter.markdown;

import at.jku.isse.ecco.service.EccoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Random histories of one Markdown document whose features each add a block somewhere in it, the
 * blocks separated by blank lines - many equal blank lines, at different places. The commits do not
 * build on each other: each selects features at random, so later ones drop features earlier ones had.
 * Every committed variant checks out byte for byte after the last commit. A blank line matched to an
 * equal one at another place, during a commit dropping features, once gave its association a
 * condition it had no business with, and a checkout the blank line twice.
 */
public class MarkdownNonMonotonicHistoriesTest {

    private static final String[] FEATURES = {"F1", "F2", "F3", "F4", "F5", "F6"};

    /** The document: per section, its core blocks and the blocks features add, in document order. */
    private static final String[][] SECTIONS = {
            {"# Title", "Intro text.", "F1:> A note\n>\n> over two paragraphs."},
            {"## Install", "F2:### Windows\n\n```\nwin install\n```", "Base install.", "F3:### Docker\n\n```\ndocker run x\n```"},
            {"## Usage", "F4:- one\n- two\n\n- three", "Usage text.", "F5:```\nline\n\nline after blank\n```"},
            {"## More", "F6:| a | b |\n|---|---|\n| 1 | 2 |", "F3:Docker notes.", "Last words."},
    };

    @TempDir
    Path tmp;

    @Test
    @Timeout(600)
    public void everyCommittedVariantComesBack() throws Exception {
        List<String> wrong = new ArrayList<>();
        for (int seed = 0; seed < 40 && wrong.isEmpty(); seed++)
            wrong.addAll(history(Files.createDirectories(this.tmp.resolve("seed" + seed)), seed));
        assertEquals(List.of(), wrong);
    }

    private static List<String> history(Path work, int seed) throws Exception {
        Random random = new Random(seed);
        List<Set<String>> configurations = new ArrayList<>();
        while (configurations.size() < 6) {
            Set<String> features = new LinkedHashSet<>();
            features.add("CORE");
            for (String feature : FEATURES)
                if (random.nextInt(3) > 0 == (configurations.size() % 2 == 0))
                    features.add(feature);
            if (!configurations.contains(features))
                configurations.add(features);
        }
        List<String> wrong = new ArrayList<>();
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(work.resolve(".ecco"));
            service.init();
            for (int i = 0; i < configurations.size(); i++) {
                Path variant = Files.createDirectories(work.resolve("v" + i));
                Files.writeString(variant.resolve("docs.md"), document(configurations.get(i)));
                service.setBaseDir(variant);
                service.commit("c" + i, String.join(", ", configurations.get(i)));
            }
            for (int i = 0; i < configurations.size(); i++) {
                Path out = Files.createDirectories(work.resolve("out" + i));
                service.setBaseDir(out);
                service.checkout(String.join(", ", configurations.get(i)));
                String expected = document(configurations.get(i));
                Path file = out.resolve("docs.md");
                String actual = Files.exists(file) ? Files.readString(file) : "<no file>";
                if (!actual.equals(expected))
                    wrong.add("seed " + seed + ", history " + configurations + ", variant " + configurations.get(i)
                            + ":\n--- expected\n" + expected + "--- got\n" + actual);
            }
        }
        return wrong;
    }

    private static String document(Set<String> features) {
        List<String> blocks = new ArrayList<>();
        for (String[] section : SECTIONS)
            for (String block : section) {
                int colon = block.indexOf(':');
                boolean optional = block.startsWith("F") && colon == 2;
                if (!optional)
                    blocks.add(block);
                else if (features.contains(block.substring(0, colon)))
                    blocks.add(block.substring(colon + 1));
            }
        return String.join("\n\n", blocks) + "\n";
    }
}
