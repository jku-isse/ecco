package at.jku.isse.ecco.adapter.markdown;

import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Markdown adapter promises byte-exact files (see MarkdownFileWriterTest for hand-written ones).
 * Here the documents are generated: ATX and setext headings, paragraphs, nested bullet and ordered
 * lists, block quotes, fenced and indented code with blank lines inside, tables, HTML blocks,
 * thematic breaks and link reference definitions, with runs of blank lines, trailing spaces and
 * tabs, LF, CRLF or CR line ends, with or without a final newline, in UTF-8 or Latin-1. Every one
 * must come back byte for byte, through the adapter alone and through a commit and checkout.
 */
public class MarkdownRoundTripPropertyTest {

    @Test
    @Timeout(300)
    public void generatedDocumentsComeBackExactly(@TempDir Path tmp) throws IOException {
        Random random = new Random(42);
        for (int round = 0; round < 2_000; round++) {
            byte[] source = new Generator(random).document();
            byte[] result = roundTrip(tmp.resolve("round" + round), source);
            if (!Arrays.equals(source, result))
                fail("round " + round + ":\n--- source ---\n" + show(source) + "\n--- written ---\n" + show(result));
        }
    }

    @Test
    @Timeout(300)
    public void generatedDocumentsSurviveACommitAndACheckout(@TempDir Path tmp) throws Exception {
        Random random = new Random(7);
        Path variant = Files.createDirectories(tmp.resolve("variant"));
        Map<String, byte[]> files = new TreeMap<>();
        for (int i = 0; i < 20; i++) {
            String name = "docs/doc" + i + (i % 2 == 0 ? ".md" : ".markdown");
            byte[] source = new Generator(random).document();
            Files.createDirectories(variant.resolve(name).getParent());
            Files.write(variant.resolve(name), source);
            files.put(name, source);
        }
        try (EccoService service = new EccoService()) {
            service.setRepositoryDir(Files.createDirectories(tmp.resolve("repo")).resolve(".ecco"));
            service.init();
            service.setBaseDir(variant);
            service.commit("generated", "A");

            Path out = Files.createDirectories(tmp.resolve("out"));
            service.setBaseDir(out);
            service.checkout("A");
            for (Map.Entry<String, byte[]> file : files.entrySet())
                assertArrayEquals(file.getValue(), Files.readAllBytes(out.resolve(file.getKey())), file.getKey());
        }
    }

    private static byte[] roundTrip(Path dir, byte[] source) throws IOException {
        Files.createDirectories(dir);
        Files.write(dir.resolve("gen.md"), source);
        Set<Node.Op> read = new MarkdownReader(new SerEntityFactory()).read(dir, new Path[]{Path.of("gen.md")});
        assertEquals(1, read.size());
        Path out = Files.createDirectories(dir.resolve("out"));
        Path[] written = new MarkdownFileWriter().write(out, Set.copyOf(read));
        assertEquals(1, written.length);
        return Files.readAllBytes(written[0]);
    }

    private static String show(byte[] bytes) {
        return new String(bytes, StandardCharsets.ISO_8859_1).replace("\r", "\\r");
    }

    /** A random Markdown document, line by line. */
    private static final class Generator {
        private final Random random;
        private final List<String> lines = new ArrayList<>();

        Generator(Random random) {
            this.random = random;
        }

        byte[] document() {
            for (int i = this.random.nextInt(10); i >= 0; i--) {
                this.block("", 0);
                // blocks mostly separated by one blank line, sometimes none or several
                for (int b = new int[]{1, 1, 1, 0, 2, 3}[this.random.nextInt(6)]; b > 0; b--)
                    this.lines.add(this.random.nextInt(6) == 0 ? "   " : "");
            }
            String separator = new String[]{"\n", "\n", "\r\n", "\r"}[this.random.nextInt(4)];
            StringBuilder text = new StringBuilder(String.join(separator, this.lines));
            if (this.random.nextInt(4) != 0)
                text.append(separator);
            return this.random.nextInt(5) == 0
                    ? text.toString().replace("€", "EUR").getBytes(StandardCharsets.ISO_8859_1)
                    : text.toString().getBytes(StandardCharsets.UTF_8);
        }

        private String words() {
            String[] words = {"ECCO", "variant", "*emphasis*", "**strong**", "`code`", "[link](http://x.org)", "café", "größe", "€",
                    "a | b", "\\*", "<span>", "tab\there", "trailing  ", "_under_", "#not-a-heading"};
            StringBuilder text = new StringBuilder();
            for (int i = this.random.nextInt(5); i >= 0; i--)
                text.append(i == 0 ? "" : " ").append(words[this.random.nextInt(words.length)]);
            return text.toString().strip().isEmpty() ? "text" : text.toString();
        }

        private void block(String indent, int depth) {
            switch (this.random.nextInt(depth < 2 ? 14 : 8)) {
                case 0 -> this.lines.add(indent + "#".repeat(1 + this.random.nextInt(6)) + " " + this.words() + (this.random.nextInt(4) == 0 ? " ##" : ""));
                case 1 -> {
                    this.lines.add(indent + this.words());
                    this.lines.add(indent + (this.random.nextBoolean() ? "=====" : "---"));
                }
                case 2, 3 -> {
                    for (int i = this.random.nextInt(3); i >= 0; i--)
                        this.lines.add(indent + this.words() + (this.random.nextInt(5) == 0 ? "  " : ""));
                }
                case 4 -> this.lines.add(indent + new String[]{"***", "---", "___", "- - -"}[this.random.nextInt(4)]);
                case 5 -> this.fencedCode(indent);
                case 6 -> this.lines.add(indent + "[" + this.random.nextInt(9) + "]: http://example.org/" + this.random.nextInt(9) + (this.random.nextBoolean() ? " \"title\"" : ""));
                case 7 -> this.table(indent);
                case 8 -> this.list(indent, depth, false);
                case 9 -> this.list(indent, depth, true);
                case 10 -> this.quote(indent, depth);
                case 11 -> this.indentedCode(indent);
                case 12 -> this.html(indent);
                default -> this.fencedCode(indent);
            }
        }

        private void fencedCode(String indent) {
            String fence = this.random.nextBoolean() ? "```" : "~~~";
            this.lines.add(indent + fence + (this.random.nextBoolean() ? "java" : ""));
            for (int i = this.random.nextInt(4); i >= 0; i--)
                this.lines.add(this.random.nextInt(4) == 0 ? "" : indent + new String[]{"int x = 1;", "    nested();", "# not a heading", "- not a list", "| not | a table |"}[this.random.nextInt(5)]);
            // an unclosed fence runs to the end of its container
            if (this.random.nextInt(8) != 0)
                this.lines.add(indent + fence);
        }

        private void indentedCode(String indent) {
            this.lines.add(indent + "    code line");
            if (this.random.nextBoolean()) {
                this.lines.add("");
                this.lines.add(indent + "    after a blank line");
            }
            this.lines.add(indent + "\tindented with a tab");
        }

        private void table(String indent) {
            this.lines.add(indent + "| A | B |" + (this.random.nextBoolean() ? " C |" : ""));
            boolean three = this.lines.get(this.lines.size() - 1).endsWith("C |");
            this.lines.add(indent + new String[]{"|---|---|", "| :-- | --: |", "|:---:|---|"}[this.random.nextInt(3)] + (three ? "---|" : ""));
            for (int i = this.random.nextInt(3); i >= 0; i--)
                this.lines.add(indent + "| " + this.random.nextInt(99) + " | x \\| y |" + (three ? " z |" : ""));
        }

        private void list(String indent, int depth, boolean ordered) {
            String bullet = new String[]{"-", "*", "+"}[this.random.nextInt(3)];
            boolean loose = this.random.nextInt(4) == 0;
            for (int i = 0; i <= this.random.nextInt(3); i++) {
                String marker = ordered ? (i + 1) + (this.random.nextBoolean() ? "." : ")") : bullet;
                this.lines.add(indent + marker + " " + (this.random.nextInt(5) == 0 ? "[ ] " : "") + this.words());
                String inner = indent + " ".repeat(marker.length() + 1);
                if (this.random.nextInt(3) == 0) {
                    if (this.random.nextBoolean()) this.lines.add("");
                    this.block(inner, depth + 1);
                }
                if (loose)
                    this.lines.add("");
            }
        }

        private void quote(String indent, int depth) {
            for (int i = this.random.nextInt(3); i >= 0; i--)
                this.lines.add(indent + "> " + this.words());
            if (this.random.nextInt(3) == 0)
                this.lines.add(indent + "lazy continuation of the quote");
            if (this.random.nextInt(3) == 0) {
                this.lines.add(indent + ">");
                this.block(indent + "> ", depth + 1);
            }
        }

        private void html(String indent) {
            switch (this.random.nextInt(3)) {
                case 0 -> {
                    this.lines.add(indent + "<div class=\"note\">");
                    this.lines.add(indent + this.words());
                    this.lines.add(indent + "</div>");
                }
                case 1 -> this.lines.add(indent + "<!-- " + this.words() + " -->");
                default -> {
                    this.lines.add(indent + "<details>");
                    this.lines.add(indent + "<summary>more</summary>");
                    this.lines.add("");
                    this.lines.add(indent + this.words());
                    this.lines.add("");
                    this.lines.add(indent + "</details>");
                }
            }
        }
    }
}
