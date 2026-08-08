package at.jku.isse.ecco.adapter.markdown;

import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.adapter.markdown.data.BlockQuoteArtifactData;
import at.jku.isse.ecco.adapter.markdown.data.BulletListArtifactData;
import at.jku.isse.ecco.adapter.markdown.data.CodeBlockArtifactData;
import at.jku.isse.ecco.adapter.markdown.data.LineArtifactData;
import at.jku.isse.ecco.adapter.markdown.data.ListItemArtifactData;
import at.jku.isse.ecco.adapter.markdown.data.OrderedListArtifactData;
import at.jku.isse.ecco.adapter.markdown.data.ParagraphArtifactData;
import at.jku.isse.ecco.adapter.markdown.data.SectionArtifactData;
import at.jku.isse.ecco.adapter.markdown.data.TableArtifactData;
import at.jku.isse.ecco.adapter.markdown.data.TableRowArtifactData;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MarkdownReaderTest {

	private final MarkdownReader reader = new MarkdownReader(new SerEntityFactory());

	private Node.Op readSingleFile(String content) throws IOException {
		Path baseDir = Files.createTempDirectory("markdown-reader");
		Path file = baseDir.resolve("a.md");
		Files.writeString(file, content);

		Set<Node.Op> result = this.reader.read(baseDir, new Path[]{Path.of("a.md")});
		assertEquals(1, result.size());
		Node.Op fileNode = result.iterator().next();
		assertInstanceOf(PluginArtifactData.class, fileNode.getArtifact().getData());
		assertEquals(MarkdownPlugin.class.getName(), ((PluginArtifactData) fileNode.getArtifact().getData()).getPluginId());
		return fileNode;
	}

	/** All LineArtifactData leaf texts, in document order - the same content a byte-exact round trip must reproduce. */
	private static List<String> collectLines(Node node) {
		List<String> lines = new ArrayList<>();
		collectLines(node, lines);
		return lines;
	}

	private static void collectLines(Node node, List<String> lines) {
		for (Node child : node.getChildren()) {
			if (child.getArtifact().getData() instanceof LineArtifactData lineArtifactData) {
				lines.add(lineArtifactData.getLine());
			} else {
				collectLines(child, lines);
			}
		}
	}

	@Test
	public void readOfAnEmptyFileProducesAPluginNodeWithNoChildren() throws IOException {
		Node.Op fileNode = this.readSingleFile("");
		assertEquals(0, fileNode.getChildren().size());
	}

	@Test
	public void readOfAFlatParagraphOnlyDocumentProducesOneParagraphWrappingItsLines() throws IOException {
		Node.Op fileNode = this.readSingleFile("Some paragraph\ntext that spans two lines.\n");

		assertEquals(1, fileNode.getChildren().size());
		Node.Op paragraphNode = (Node.Op) fileNode.getChildren().get(0);
		assertInstanceOf(ParagraphArtifactData.class, paragraphNode.getArtifact().getData());
		assertEquals(List.of("Some paragraph", "text that spans two lines."), collectLines(paragraphNode));
	}

	@Test
	public void readOfNestedHeadingsProducesNestedSections() throws IOException {
		Node.Op fileNode = this.readSingleFile("""
				# Title

				intro text

				## Sub

				sub text

				### SubSub

				subsub text
				""");

		assertEquals(1, fileNode.getChildren().size());
		Node.Op h1 = (Node.Op) fileNode.getChildren().get(0);
		SectionArtifactData h1Data = (SectionArtifactData) h1.getArtifact().getData();
		assertEquals(1, h1Data.getLevel());
		assertEquals("# Title", h1Data.getHeadingLine());
		// H1's own children, in document order: its own heading line, the blank line after it, the
		// intro paragraph, the blank line before the next heading, then the H2 section (nested, not a
		// sibling of H1) - every line in the source has to live somewhere for a byte-exact round trip.
		assertEquals(5, h1.getChildren().size());
		assertEquals("# Title", lineOf(h1, 0));
		assertEquals("", lineOf(h1, 1));
		assertInstanceOf(ParagraphArtifactData.class, ((Node.Op) h1.getChildren().get(2)).getArtifact().getData());
		assertEquals("", lineOf(h1, 3));

		Node.Op h2 = (Node.Op) h1.getChildren().get(4);
		SectionArtifactData h2Data = (SectionArtifactData) h2.getArtifact().getData();
		assertEquals(2, h2Data.getLevel());
		assertEquals("## Sub", h2Data.getHeadingLine());
		assertEquals(5, h2.getChildren().size());

		Node.Op h3 = (Node.Op) h2.getChildren().get(4);
		SectionArtifactData h3Data = (SectionArtifactData) h3.getArtifact().getData();
		assertEquals(3, h3Data.getLevel());
		assertEquals("### SubSub", h3Data.getHeadingLine());
		assertEquals(List.of("### SubSub", "", "subsub text"), collectLines(h3));
	}

	private static String lineOf(Node.Op parent, int index) {
		return ((LineArtifactData) ((Node.Op) parent.getChildren().get(index)).getArtifact().getData()).getLine();
	}

	@Test
	public void readOfSiblingHeadingsClosesThePreviousSectionAtTheSameLevel() throws IOException {
		Node.Op fileNode = this.readSingleFile("# One\n\ntext one\n\n# Two\n\ntext two\n");

		// [section "# One", the blank line between the two sections (a file-level gap, not inside
		// either section), section "# Two"]
		assertEquals(3, fileNode.getChildren().size());
		assertEquals("# One", ((SectionArtifactData) ((Node.Op) fileNode.getChildren().get(0)).getArtifact().getData()).getHeadingLine());
		assertEquals("", lineOf(fileNode, 1));
		assertEquals("# Two", ((SectionArtifactData) ((Node.Op) fileNode.getChildren().get(2)).getArtifact().getData()).getHeadingLine());
	}

	@Test
	public void readOfAFencedCodeBlockDoesNotMisreadAHashCommentLineAsAHeading() throws IOException {
		Node.Op fileNode = this.readSingleFile("""
				# Real heading

				```bash
				#!/bin/bash
				# not a heading
				echo hi
				```
				""");

		// exactly one section (the real heading) - the fenced code block, including its "#" lines,
		// must not have produced any extra SectionArtifactData
		assertEquals(1, fileNode.getChildren().size());
		Node.Op section = (Node.Op) fileNode.getChildren().get(0);
		// heading's own line, the blank line after it, then the code block
		assertEquals(3, section.getChildren().size());
		Node.Op codeBlock = (Node.Op) section.getChildren().get(2);
		CodeBlockArtifactData codeBlockData = (CodeBlockArtifactData) codeBlock.getArtifact().getData();
		assertTrue(codeBlockData.isFenced());
		assertEquals("bash", codeBlockData.getInfo());
		// fence delimiter lines are included verbatim, same as every other line
		assertEquals(List.of("```bash", "#!/bin/bash", "# not a heading", "echo hi", "```"), collectLines(codeBlock));
	}

	@Test
	public void readOfMixedNestedListsProducesNestedListItems() throws IOException {
		Node.Op fileNode = this.readSingleFile("""
				- outer 1
				  1. inner a
				  2. inner b
				- outer 2
				""");

		assertEquals(1, fileNode.getChildren().size());
		Node.Op outerList = (Node.Op) fileNode.getChildren().get(0);
		assertInstanceOf(BulletListArtifactData.class, outerList.getArtifact().getData());
		assertEquals(2, outerList.getChildren().size());

		Node.Op outerItem1 = (Node.Op) outerList.getChildren().get(0);
		assertInstanceOf(ListItemArtifactData.class, outerItem1.getArtifact().getData());
		// outer item 1's own children: its paragraph, then the nested ordered list
		assertEquals(2, outerItem1.getChildren().size());
		Node.Op innerList = (Node.Op) outerItem1.getChildren().get(1);
		assertInstanceOf(OrderedListArtifactData.class, innerList.getArtifact().getData());
		assertEquals(2, innerList.getChildren().size());
	}

	@Test
	public void readOfAListItemContainingACodeBlockPreservesTheGapLineBetweenThem() throws IOException {
		Node.Op fileNode = this.readSingleFile("""
				- outer 1
				- outer 2

				  ```
				  code in list item
				  ```
				""");

		Node.Op outerList = (Node.Op) fileNode.getChildren().get(0);
		Node.Op item2 = (Node.Op) outerList.getChildren().get(1);
		// paragraph (its raw line, list marker included verbatim), gap blank line, code block (raw
		// lines, original indentation included verbatim) - the blank line between them, and the
		// indentation, both have to round-trip too.
		assertEquals(List.of("- outer 2", "", "  ```", "  code in list item", "  ```"), collectLines(item2));
	}

	@Test
	public void readOfABlockQuoteContainingAListProducesNestedStructure() throws IOException {
		Node.Op fileNode = this.readSingleFile("""
				> - quoted item one
				> - quoted item two
				""");

		Node.Op blockQuote = (Node.Op) fileNode.getChildren().get(0);
		assertInstanceOf(BlockQuoteArtifactData.class, blockQuote.getArtifact().getData());
		Node.Op list = (Node.Op) blockQuote.getChildren().get(0);
		assertInstanceOf(BulletListArtifactData.class, list.getArtifact().getData());
		assertEquals(2, list.getChildren().size());
	}

	@Test
	public void readOfAGfmTableProducesHeaderAndBodyRows() throws IOException {
		Node.Op fileNode = this.readSingleFile("""
				| a | b |
				|---|---|
				| 1 | 2 |
				| 3 | 4 |
				""");

		Node.Op table = (Node.Op) fileNode.getChildren().get(0);
		assertInstanceOf(TableArtifactData.class, table.getArtifact().getData());
		// header row, the "|---|---|" alignment line (pure syntax - CommonMark's table parser doesn't
		// emit a node for it at all, so it round-trips as a plain gap-fill line directly under the
		// table), then the two body rows
		assertEquals(4, table.getChildren().size());

		Node.Op headerRow = (Node.Op) table.getChildren().get(0);
		assertTrue(((TableRowArtifactData) headerRow.getArtifact().getData()).isHeader());
		assertEquals(List.of("| a | b |"), collectLines(headerRow));

		assertEquals("|---|---|", lineOf(table, 1));

		Node.Op bodyRow1 = (Node.Op) table.getChildren().get(2);
		assertTrue(!((TableRowArtifactData) bodyRow1.getArtifact().getData()).isHeader());
		assertEquals(List.of("| 1 | 2 |"), collectLines(bodyRow1));
	}

	@Test
	public void readOfMultipleFilesProducesOneNodePerFile() throws IOException {
		Path baseDir = Files.createTempDirectory("markdown-reader-multi");
		Files.writeString(baseDir.resolve("a.md"), "# A\n");
		Files.writeString(baseDir.resolve("b.md"), "# B\n");

		Set<Node.Op> result = this.reader.read(baseDir, new Path[]{Path.of("a.md"), Path.of("b.md")});

		assertEquals(2, result.size());
	}

	@Test
	public void getPluginIdIsTheMarkdownPluginClassName() {
		assertEquals(MarkdownPlugin.class.getName(), this.reader.getPluginId());
	}

	@Test
	public void getPrioritizedPatternsClaimsMdAtAHigherPriorityThanTheTextAdapter() {
		var patterns = this.reader.getPrioritizedPatterns();
		assertTrue(patterns.keySet().stream().anyMatch(priority -> priority > 1));
		assertTrue(patterns.values().stream().flatMap(java.util.Arrays::stream).anyMatch("**.md"::equals));
	}

}
