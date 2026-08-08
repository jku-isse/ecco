package at.jku.isse.ecco.adapter.markdown.view;

import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.adapter.markdown.MarkdownReader;
import at.jku.isse.ecco.adapter.markdown.view.MarkdownViewer.LineKind;
import at.jku.isse.ecco.adapter.markdown.view.MarkdownViewer.MarkdownLineRow;
import at.jku.isse.ecco.adapter.markdown.view.MarkdownViewer.RenderContext;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Exercises {@link MarkdownViewer#collectLines}'s classification directly (no JavaFX
 * {@code Control}/{@code Stage} involved at all - {@code collectLines} and its {@code RenderContext}
 * dispatch are plain-Java, deliberately static/package-visible for exactly this purpose) against real
 * parsed Markdown, to verify each source line ends up tagged with the {@link LineKind}/list-depth/
 * blockquote state a human would expect from the document's own structure.
 */
public class MarkdownViewerTest {

	private final MarkdownReader reader = new MarkdownReader(new SerEntityFactory());

	private Node readSingleFile(String content) throws IOException {
		Path baseDir = Files.createTempDirectory("markdown-viewer");
		Path file = baseDir.resolve("a.md");
		Files.writeString(file, content);

		Set<Node.Op> result = this.reader.read(baseDir, new Path[]{Path.of("a.md")});
		assertEquals(1, result.size());
		Node.Op fileNode = result.iterator().next();
		assertInstanceOf(PluginArtifactData.class, fileNode.getArtifact().getData());
		return fileNode;
	}

	private static List<MarkdownLineRow> collect(Node fileNode) {
		List<MarkdownLineRow> rows = new ArrayList<>();
		MarkdownViewer.collectLines(fileNode, RenderContext.ROOT, Collections.emptyMap(), rows);
		return rows;
	}

	private static MarkdownLineRow rowWithText(List<MarkdownLineRow> rows, String text) {
		return rows.stream().filter(r -> text.equals(r.getText())).findFirst()
				.orElseThrow(() -> new AssertionError("no row with text " + text + " among " + rows.size() + " rows"));
	}

	@Test
	void headingLineIsClassifiedAsHeadingItsOwnContentIsNot() throws IOException {
		Node fileNode = readSingleFile("""
				# Title

				Some paragraph text.
				""");
		List<MarkdownLineRow> rows = collect(fileNode);

		assertEquals(LineKind.HEADING, kindOf(rows, "# Title"));
		assertEquals(LineKind.PLAIN, kindOf(rows, "Some paragraph text."));
	}

	@Test
	void nestedHeadingGetsItsOwnHeadingStylingNotItsParentSection() throws IOException {
		Node fileNode = readSingleFile("""
				# H1

				## H2

				content
				""");
		List<MarkdownLineRow> rows = collect(fileNode);

		assertEquals(LineKind.HEADING, kindOf(rows, "# H1"));
		assertEquals(LineKind.HEADING, kindOf(rows, "## H2"));
		assertEquals(LineKind.PLAIN, kindOf(rows, "content"));
	}

	@Test
	void fencedCodeBlockLinesAreClassifiedAsCode() throws IOException {
		Node fileNode = readSingleFile("""
				# Title

				```bash
				echo hi
				```
				""");
		List<MarkdownLineRow> rows = collect(fileNode);

		assertEquals(LineKind.CODE, kindOf(rows, "```bash"));
		assertEquals(LineKind.CODE, kindOf(rows, "echo hi"));
		assertEquals(LineKind.CODE, kindOf(rows, "```"));
	}

	@Test
	void blockquoteLinesAreMarkedInBlockquoteRegardlessOfKind() throws IOException {
		Node fileNode = readSingleFile("""
				> **Note:** a callout.
				""");
		List<MarkdownLineRow> rows = collect(fileNode);

		MarkdownLineRow row = rowWithText(rows, "> **Note:** a callout.");
		assertEquals(LineKind.PLAIN, row.getContext().kind);
		assertEquals(true, row.getContext().inBlockquote);
	}

	@Test
	void listItemsGetIncreasingListDepthWhenNested() throws IOException {
		Node fileNode = readSingleFile("""
				- outer 1
				  - inner 1
				""");
		List<MarkdownLineRow> rows = collect(fileNode);

		assertEquals(1, rowWithText(rows, "- outer 1").getContext().listDepth);
		assertEquals(2, rowWithText(rows, "  - inner 1").getContext().listDepth);
	}

	@Test
	void tableHeaderRowIsDistinguishedFromBodyRows() throws IOException {
		Node fileNode = readSingleFile("""
				| A | B |
				|---|---|
				| 1 | 2 |
				""");
		List<MarkdownLineRow> rows = collect(fileNode);

		assertEquals(LineKind.TABLE_HEADER_ROW, kindOf(rows, "| A | B |"));
		assertEquals(LineKind.TABLE_ROW, kindOf(rows, "| 1 | 2 |"));
	}

	@Test
	void thematicBreakIsClassifiedDistinctly() throws IOException {
		Node fileNode = readSingleFile("""
				above

				---

				below
				""");
		List<MarkdownLineRow> rows = collect(fileNode);

		assertEquals(LineKind.THEMATIC_BREAK, kindOf(rows, "---"));
		assertEquals(LineKind.PLAIN, kindOf(rows, "above"));
		assertEquals(LineKind.PLAIN, kindOf(rows, "below"));
	}

	private static LineKind kindOf(List<MarkdownLineRow> rows, String text) {
		return rowWithText(rows, text).getContext().kind;
	}

}
