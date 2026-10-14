package at.jku.isse.ecco.adapter.markdown;

import at.jku.isse.ecco.adapter.markdown.data.SectionArtifactData;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two variants of a Markdown file that differ in one aspect only, each committed under its own
 * feature into one repository: each checkout gives back its own variant, byte for byte. The aspects
 * are the ones a block's artifact data does or does not hold (MarkdownArtifactDataEqualityTest): if
 * two different blocks were taken for the same one, one variant's content would replace the other's.
 */
public class MarkdownRepositoryVariantsTest {

	private static final Path FILE = Path.of("docs", "README.md");

	@TempDir
	Path tmp;

	/** Commits each variant under its own feature (A, B, C, ...), in order, then checks each one out. */
	private void assertEachVariantComesBack(byte[]... variants) throws Exception {
		Path content = Files.createDirectories(this.tmp.resolve("content"));
		try (EccoService service = new EccoService()) {
			service.setRepositoryDir(this.tmp.resolve(".ecco"));
			service.init();
			service.setBaseDir(content);
			Files.createDirectories(content.resolve(FILE).getParent());
			for (int i = 0; i < variants.length; i++) {
				Files.write(content.resolve(FILE), variants[i]);
				service.commit("variant " + i, feature(i));
			}
			boolean markdown = false;
			for (Association association : service.getRepository().getAssociations())
				markdown |= hasSection(association.getRootNode());
			for (int i = 0; i < variants.length; i++) {
				Path checkout = Files.createDirectories(this.tmp.resolve("checkout-" + feature(i)));
				service.setBaseDir(checkout);
				service.checkout(feature(i));
				String expected = feature(i);
				byte[] actual = Files.readAllBytes(checkout.resolve(FILE));
				assertArrayEquals(variants[i], actual, () -> "checkout " + expected + " gave" + show(actual) + "\nvariants:" + show(variants));
			}
			assertTrue(markdown || noHeading(variants), "the file was read by the Markdown adapter, not the text adapter");
		}
	}

	private static String feature(int i) {
		return String.valueOf((char) ('A' + i));
	}

	private static boolean hasSection(Node node) {
		if (node.getArtifact() != null && node.getArtifact().getData() instanceof SectionArtifactData)
			return true;
		for (Node child : node.getChildren())
			if (hasSection(child))
				return true;
		return false;
	}

	private static boolean noHeading(byte[]... variants) {
		for (byte[] variant : variants)
			if (new String(variant, StandardCharsets.UTF_8).contains("# "))
				return false;
		return true;
	}

	private static String show(byte[]... variants) {
		StringBuilder text = new StringBuilder();
		for (byte[] variant : variants)
			text.append("\n--- ").append(new String(variant, StandardCharsets.UTF_8).replace("\r", "\\r"));
		return text.toString();
	}

	private static byte[] utf8(String text) {
		return text.getBytes(StandardCharsets.UTF_8);
	}

	@ParameterizedTest(name = "{0}")
	@CsvSource(delimiter = '|', quoteCharacter = '"', textBlock = """
			info string            | "# T\\n\\n```java\\nx();\\n```\\n"              | "# T\\n\\n```kotlin\\nx();\\n```\\n"
			info string or none    | "# T\\n\\n```java\\nx();\\n```\\n"              | "# T\\n\\n```\\nx();\\n```\\n"
			fence character        | "# T\\n\\n```\\nx();\\n```\\n"                  | "# T\\n\\n~~~\\nx();\\n~~~\\n"
			fence length           | "# T\\n\\n```\\nx();\\n```\\n"                  | "# T\\n\\n````\\nx();\\n````\\n"
			fenced or indented     | "# T\\n\\n```\\nx();\\n```\\n"                  | "# T\\n\\n    x();\\n"
			code content           | "# T\\n\\n```\\nx();\\n```\\n"                  | "# T\\n\\n```\\ny();\\n```\\n"
			list start             | "# T\\n\\n1. a\\n2. b\\n"                      | "# T\\n\\n3. a\\n4. b\\n"
			list delimiter         | "# T\\n\\n1. a\\n2. b\\n"                      | "# T\\n\\n1) a\\n2) b\\n"
			bullet marker          | "# T\\n\\n- a\\n- b\\n"                        | "# T\\n\\n* a\\n* b\\n"
			bullet or ordered      | "# T\\n\\n- a\\n- b\\n"                        | "# T\\n\\n1. a\\n2. b\\n"
			tight or loose         | "# T\\n\\n- a\\n- b\\n"                        | "# T\\n\\n- a\\n\\n- b\\n"
			nested list            | "# T\\n\\n- a\\n  - b\\n"                      | "# T\\n\\n- a\\n- b\\n"
			heading level          | "# T\\n\\n## S\\n\\ntext\\n"                   | "# T\\n\\n### S\\n\\ntext\\n"
			heading text           | "# T\\n\\n## S\\n\\ntext\\n"                   | "# T\\n\\n## R\\n\\ntext\\n"
			setext level           | "Title\\n=====\\n\\ntext\\n"                  | "Title\\n-----\\n\\ntext\\n"
			setext or ATX          | "Title\\n=====\\n\\ntext\\n"                  | "# Title\\n\\ntext\\n"
			multi-line setext      | "Foo\\nbar\\n===\\n\\ntext\\n"                | "Foo\\nbaz\\n===\\n\\ntext\\n"
			closing hashes         | "# T\\n\\n## S\\n"                            | "# T\\n\\n## S ##\\n"
			thematic break         | "# T\\n\\na\\n\\n***\\n\\nb\\n"                | "# T\\n\\na\\n\\n___\\n\\nb\\n"
			table alignment        | "# T\\n\\n| a | b |\\n|---|---|\\n| 1 | 2 |\\n" | "# T\\n\\n| a | b |\\n|:--|--:|\\n| 1 | 2 |\\n"
			table cell             | "# T\\n\\n| a | b |\\n|---|---|\\n| 1 | 2 |\\n" | "# T\\n\\n| a | b |\\n|---|---|\\n| 1 | 3 |\\n"
			table header           | "# T\\n\\n| a | b |\\n|---|---|\\n| 1 | 2 |\\n" | "# T\\n\\n| a | c |\\n|---|---|\\n| 1 | 2 |\\n"
			hard line break        | "# T\\n\\na  \\nb\\n"                         | "# T\\n\\na\\nb\\n"
			trailing tab           | "# T\\n\\na\\t\\n"                            | "# T\\n\\na\\n"
			lazy continuation      | "# T\\n\\n> a\\nb\\n"                         | "# T\\n\\n> a\\n> b\\n"
			quote or paragraph     | "# T\\n\\n> a\\n"                             | "# T\\n\\na\\n"
			html block             | "# T\\n\\n<div>\\nx\\n</div>\\n"               | "# T\\n\\n<div>\\ny\\n</div>\\n"
			final newline          | "# T\\n\\ntext\\n"                            | "# T\\n\\ntext"
			blank lines            | "# T\\n\\ntext\\n"                            | "# T\\n\\n\\n\\ntext\\n"
			front matter           | "---\\ntitle: a\\n---\\n\\n# T\\n"             | "---\\ntitle: b\\n---\\n\\n# T\\n"
			""")
	@Timeout(120)
	public void twoVariantsDifferingInOneAspectEachComeBack(String aspect, String a, String b) throws Exception {
		this.assertEachVariantComesBack(utf8(unescape(a)), utf8(unescape(b)));
	}

	private static String unescape(String text) {
		return text.replace("\\n", "\n").replace("\\t", "\t");
	}

	/** Three variants: the third shares parts with each of the other two. */
	@Test
	@Timeout(120)
	public void threeVariantsOfAListEachComeBack() throws Exception {
		this.assertEachVariantComesBack(
				utf8("# T\n\n1. a\n2. b\n\n```java\nx();\n```\n"),
				utf8("# T\n\n3. a\n4. b\n\n```kotlin\nx();\n```\n"),
				utf8("# T\n\n1. a\n2. b\n\n```kotlin\nx();\n```\n"));
	}

	/** The same heading twice in one file, and a variant with only one of them. */
	@Test
	@Timeout(120)
	public void repeatedHeadingsEachComeBack() throws Exception {
		this.assertEachVariantComesBack(
				utf8("# T\n\n## Example\n\none\n\n## Example\n\ntwo\n"),
				utf8("# T\n\n## Example\n\ntwo\n"));
	}

	/** Sections swapped: the order of the second variant conflicts with the first one's. */
	@Test
	@Timeout(120)
	public void swappedSectionsEachComeBack() throws Exception {
		this.assertEachVariantComesBack(
				utf8("# T\n\n## X\n\nx\n\n## Y\n\ny\n"),
				utf8("# T\n\n## Y\n\ny\n\n## X\n\nx\n"));
	}

	/**
	 * Variants differing in line ending only. The line separator is recorded on the file's plugin
	 * artifact, which all variants of the file share, so every variant gets the newest commit's
	 * (a limitation documented in TextFileFormat): whitespace only, the lines are right.
	 */
	@Test
	@Disabled("bug: variants of a file share one line separator, the newest commit's - an LF variant is checked out with CRLF")
	@Timeout(120)
	public void lineEndingVariantsEachComeBack() throws Exception {
		this.assertEachVariantComesBack(utf8("# T\n\ntext\n"), utf8("# T\r\n\r\ntext\r\n"));
	}

	@Test
	@Timeout(120)
	public void lineEndingVariantsAllGetTheNewestCommitsLineEnding() throws Exception {
		byte[] crlf = utf8("# T\r\n\r\ntext\r\n");
		Path content = Files.createDirectories(this.tmp.resolve("content"));
		try (EccoService service = new EccoService()) {
			service.setRepositoryDir(this.tmp.resolve(".ecco"));
			service.init();
			service.setBaseDir(content);
			Files.createDirectories(content.resolve(FILE).getParent());
			Files.write(content.resolve(FILE), utf8("# T\n\ntext\n"));
			service.commit("lf", "A");
			Files.write(content.resolve(FILE), crlf);
			service.commit("crlf", "B");
			Path checkout = Files.createDirectories(this.tmp.resolve("checkout-A"));
			service.setBaseDir(checkout);
			service.checkout("A");
			assertArrayEquals(crlf, Files.readAllBytes(checkout.resolve(FILE)));
		}
	}

	/**
	 * Variants differing in charset only: as with line endings, the newest commit's charset is used
	 * for every variant. Here the characters are the same, but a UTF-8 variant is written in
	 * Latin-1, so a reader expecting UTF-8 gets an invalid byte instead of "é".
	 */
	@Test
	@Disabled("bug: variants of a file share one charset, the newest commit's - a UTF-8 variant is checked out in Latin-1")
	@Timeout(120)
	public void charsetVariantsEachComeBack() throws Exception {
		this.assertEachVariantComesBack(utf8("# T\n\ncafé\n"), "# T\n\ncafé\n".getBytes(StandardCharsets.ISO_8859_1));
	}

	@Test
	@Timeout(120)
	public void charsetVariantsAllGetTheNewestCommitsCharset() throws Exception {
		byte[] latin1 = "# T\n\ncafé\n".getBytes(StandardCharsets.ISO_8859_1);
		Path content = Files.createDirectories(this.tmp.resolve("content"));
		try (EccoService service = new EccoService()) {
			service.setRepositoryDir(this.tmp.resolve(".ecco"));
			service.init();
			service.setBaseDir(content);
			Files.createDirectories(content.resolve(FILE).getParent());
			Files.write(content.resolve(FILE), utf8("# T\n\ncafé\n"));
			service.commit("utf-8", "A");
			Files.write(content.resolve(FILE), latin1);
			service.commit("latin-1", "B");
			Path checkout = Files.createDirectories(this.tmp.resolve("checkout-A"));
			service.setBaseDir(checkout);
			service.checkout("A");
			assertArrayEquals(latin1, Files.readAllBytes(checkout.resolve(FILE)));
		}
	}

	/**
	 * Random documents built from many kinds of block; the second variant changes, inserts or removes
	 * one block, or one line in one block. Each comes back.
	 */
	@Test
	@Timeout(300)
	public void randomVariantPairsEachComeBack() throws Exception {
		String[] blocks = {
				"# H1", "## H2", "### H3", "## H2 ##", "Setext\n===", "Setext\n---",
				"para one", "para two\nsecond line", "hard  \nbreak", "trailing\t",
				"```java\nx();\n\ny();\n```", "```kotlin\nx();\n```", "~~~\nx();\n~~~", "    indented\n    code",
				"- a\n- b", "* a\n* b", "- a\n\n- b", "- a\n  - nested\n- c", "1. a\n2. b", "3. a\n4. b", "1) a\n2) b",
				"> quote\nlazy", "> q\n>\n> - item", "<div>\nhtml\n</div>", "<!-- c -->",
				"| a | b |\n|---|---|\n| 1 | 2 |", "| a | b |\n|:--|--:|\n| 1 | 2 |",
				"***", "---", "___", "[r]: http://x.org", "- [ ] task\n- [x] done",
		};
		java.util.Random random = new java.util.Random(3);
		for (int round = 0; round < 150; round++) {
			java.util.List<String> a = new java.util.ArrayList<>();
			for (int i = 2 + random.nextInt(7); i > 0; i--)
				a.add(blocks[random.nextInt(blocks.length)]);
			java.util.List<String> b = new java.util.ArrayList<>(a);
			int at = random.nextInt(b.size());
			switch (random.nextInt(4)) {
				case 0 -> b.set(at, blocks[random.nextInt(blocks.length)]);
				case 1 -> b.add(at, blocks[random.nextInt(blocks.length)]);
				case 2 -> b.remove(at);
				default -> b.set(at, b.get(at).replaceFirst("[a-z]", "Z"));
			}
			String separator = random.nextInt(4) == 0 ? "\n\n\n" : "\n\n";
			String end = random.nextInt(4) == 0 ? "" : "\n";
			Path dir = this.tmp.resolve("round" + round);
			Files.createDirectories(dir);
			Path previous = this.tmp;
			this.tmp = dir;
			try {
				this.assertEachVariantComesBack(utf8(String.join(separator, a) + end), utf8(String.join(separator, b) + end));
			} finally {
				this.tmp = previous;
			}
		}
	}

	/** Paragraphs are all equal artifacts: a variant inserting one before the others. */
	@Test
	@Timeout(120)
	public void anInsertedParagraphKeepsItsPlace() throws Exception {
		this.assertEachVariantComesBack(
				utf8("# T\n\nfirst\n\nsecond\n"),
				utf8("# T\n\nnew\n\nfirst\n\nsecond\n"));
	}
}
