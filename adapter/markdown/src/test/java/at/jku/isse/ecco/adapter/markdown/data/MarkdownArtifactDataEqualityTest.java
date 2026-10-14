package at.jku.isse.ecco.adapter.markdown.data;

import at.jku.isse.ecco.artifact.ArtifactData;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ECCO matches artifacts across variants by the equality of their data: two blocks that differ but
 * compare equal let one variant's content replace the other's, two equal blocks that compare unequal
 * are duplicated. The writer only ever prints the {@link LineArtifactData} leaves (MarkdownFileWriter),
 * so what a block looks like is always in its lines; the fields of a block only decide which blocks
 * of two variants are taken for the same one.
 */
public class MarkdownArtifactDataEqualityTest {

	/** Reflexive, symmetric, consistent with hashCode, unequal to null and to other types, and the same after serialization (repositories store artifacts serialized). */
	private static void assertContract(Supplier<ArtifactData> make) throws Exception {
		ArtifactData a = make.get();
		ArtifactData b = make.get();
		assertEquals(a, a);
		assertEquals(a, b);
		assertEquals(b, a);
		assertEquals(a.hashCode(), b.hashCode());
		assertNotEquals(null, a);
		assertTrue(!a.equals(null));
		assertTrue(!a.equals("not artifact data"));
		ArtifactData copy = roundTrip(a);
		assertEquals(a, copy);
		assertEquals(a.hashCode(), copy.hashCode());
		assertEquals(a.toString(), copy.toString());
	}

	private static void assertDiffer(ArtifactData a, ArtifactData b) {
		assertNotEquals(a, b);
		assertNotEquals(b, a);
	}

	private static ArtifactData roundTrip(ArtifactData data) throws IOException, ClassNotFoundException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
			out.writeObject(data);
		}
		try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
			return (ArtifactData) in.readObject();
		}
	}

	@Test
	public void everyTypeKeepsTheContract() throws Exception {
		List<Supplier<ArtifactData>> all = List.of(
				ParagraphArtifactData::new, BlockQuoteArtifactData::new, ListItemArtifactData::new, TableArtifactData::new,
				() -> new LineArtifactData("text"), () -> new LineArtifactData(null),
				() -> new CodeBlockArtifactData(true, "java"), () -> new CodeBlockArtifactData(false, null),
				() -> new ThematicBreakArtifactData("***"), () -> new ThematicBreakArtifactData(null),
				() -> new HtmlBlockArtifactData("<div>"), () -> new HtmlBlockArtifactData(null),
				() -> new BulletListArtifactData("-"), () -> new BulletListArtifactData(null),
				() -> new OrderedListArtifactData(1, "."), () -> new OrderedListArtifactData(null, null),
				() -> new SectionArtifactData(2, "## Title"), () -> new SectionArtifactData(1, null),
				() -> new TableRowArtifactData(true), () -> new TableRowArtifactData(false));
		for (Supplier<ArtifactData> make : all)
			assertContract(make);
	}

	/** Marker types: equal to any other of their own type, never to another type - also not to another marker type. */
	@Test
	public void markerTypesAreEqualOnlyToTheirOwnType() {
		List<ArtifactData> markers = List.of(new ParagraphArtifactData(), new BlockQuoteArtifactData(), new ListItemArtifactData(), new TableArtifactData());
		for (ArtifactData a : markers)
			for (ArtifactData b : markers)
				assertEquals(a.getClass() == b.getClass(), a.equals(b), a + " / " + b);
		// a different type with the same "content" (none) is still different
		assertDiffer(new ParagraphArtifactData(), new LineArtifactData(""));
	}

	@Test
	public void aLineIsItsTextIncludingWhitespace() {
		assertDiffer(new LineArtifactData("a"), new LineArtifactData("b"));
		// a hard line break (two trailing spaces) is meaning, a trailing tab or space at least bytes
		assertDiffer(new LineArtifactData("a"), new LineArtifactData("a  "));
		assertDiffer(new LineArtifactData("a"), new LineArtifactData("a\t"));
		assertDiffer(new LineArtifactData("  a"), new LineArtifactData("a"));
		assertDiffer(new LineArtifactData(""), new LineArtifactData(null));
		assertDiffer(new LineArtifactData("a"), new SectionArtifactData(0, "a"));
	}

	@Test
	public void aCodeBlockIsWhetherItIsFencedAndItsInfoString() {
		assertDiffer(new CodeBlockArtifactData(true, "java"), new CodeBlockArtifactData(true, "kotlin"));
		assertDiffer(new CodeBlockArtifactData(true, "java"), new CodeBlockArtifactData(true, null));
		assertDiffer(new CodeBlockArtifactData(true, ""), new CodeBlockArtifactData(true, null));
		assertDiffer(new CodeBlockArtifactData(true, null), new CodeBlockArtifactData(false, null));
		assertDiffer(new CodeBlockArtifactData(true, "java"), new CodeBlockArtifactData(false, "java"));
		// the fence character (``` or ~~~) and its length are not fields: they are in the block's
		// fence lines, which tell two such blocks apart (MarkdownRepositoryVariantsTest)
	}

	@Test
	public void anOrderedListIsItsStartNumberAndDelimiter() {
		assertDiffer(new OrderedListArtifactData(1, "."), new OrderedListArtifactData(3, "."));
		assertDiffer(new OrderedListArtifactData(1, "."), new OrderedListArtifactData(1, ")"));
		assertDiffer(new OrderedListArtifactData(1, "."), new OrderedListArtifactData(null, "."));
		assertDiffer(new OrderedListArtifactData(1, "."), new OrderedListArtifactData(1, null));
		// Integer compared by value, not identity (outside the Integer cache)
		assertEquals(new OrderedListArtifactData(1000, "."), new OrderedListArtifactData(Integer.valueOf(1000), "."));
		assertEquals(new OrderedListArtifactData(1000, ".").hashCode(), new OrderedListArtifactData(Integer.valueOf(1000), ".").hashCode());
	}

	@Test
	public void aBulletListIsItsMarker() {
		assertDiffer(new BulletListArtifactData("-"), new BulletListArtifactData("*"));
		assertDiffer(new BulletListArtifactData("-"), new BulletListArtifactData("+"));
		assertDiffer(new BulletListArtifactData("-"), new BulletListArtifactData(null));
		assertDiffer(new BulletListArtifactData("-"), new OrderedListArtifactData(null, "-"));
	}

	@Test
	public void aSectionIsItsLevelAndHeadingLine() {
		assertDiffer(new SectionArtifactData(2, "## A"), new SectionArtifactData(3, "## A"));
		assertDiffer(new SectionArtifactData(2, "## A"), new SectionArtifactData(2, "## B"));
		assertDiffer(new SectionArtifactData(2, "## A"), new SectionArtifactData(2, "## A "));
		assertDiffer(new SectionArtifactData(2, "## A"), new SectionArtifactData(2, null));
		// a setext heading's line is its (first) text line: the level alone tells "===" from "---"
		assertDiffer(new SectionArtifactData(1, "Title"), new SectionArtifactData(2, "Title"));
	}

	@Test
	public void aThematicBreakIsItsLiteral() {
		assertDiffer(new ThematicBreakArtifactData("***"), new ThematicBreakArtifactData("---"));
		assertDiffer(new ThematicBreakArtifactData("***"), new ThematicBreakArtifactData("___"));
		assertDiffer(new ThematicBreakArtifactData("***"), new ThematicBreakArtifactData("* * *"));
		assertDiffer(new ThematicBreakArtifactData("***"), new ThematicBreakArtifactData(null));
		assertEquals("(thematic break)", new ThematicBreakArtifactData(null).toString());
	}

	@Test
	public void anHtmlBlockIsItsLiteral() {
		assertDiffer(new HtmlBlockArtifactData("<div>\nx\n</div>"), new HtmlBlockArtifactData("<div>\ny\n</div>"));
		assertDiffer(new HtmlBlockArtifactData("<div>"), new HtmlBlockArtifactData(null));
		assertEquals("(html block)", new HtmlBlockArtifactData(null).toString());
	}

	@Test
	public void aTableRowIsWhetherItIsAHeaderRow() {
		assertDiffer(new TableRowArtifactData(true), new TableRowArtifactData(false));
		assertEquals("(table header row)", new TableRowArtifactData(true).toString());
		assertEquals("(table row)", new TableRowArtifactData(false).toString());
		// the cells and the alignment row are lines: a table row is a marker like a paragraph
		assertEquals(new TableArtifactData(), new TableArtifactData());
	}

	@Test
	public void toStringsNameTheirBlock() {
		assertEquals("```java", new CodeBlockArtifactData(true, "java").toString());
		assertEquals("```", new CodeBlockArtifactData(true, "").toString());
		assertEquals("(indented)", new CodeBlockArtifactData(false, null).toString());
		assertEquals("(ordered list, start=3))", new OrderedListArtifactData(3, ")").toString());
		assertEquals("(bullet list, marker=*)", new BulletListArtifactData("*").toString());
		assertEquals("## A", new SectionArtifactData(2, "## A").toString());
		assertEquals("(paragraph)", new ParagraphArtifactData().toString());
		assertEquals("(block quote)", new BlockQuoteArtifactData().toString());
		assertEquals("(list item)", new ListItemArtifactData().toString());
		assertEquals("(table)", new TableArtifactData().toString());
		assertEquals("a", new LineArtifactData("a").toString());
		assertEquals(true, new CodeBlockArtifactData(true, "x").isFenced());
		assertEquals("x", new CodeBlockArtifactData(true, "x").getInfo());
		assertEquals(3, new OrderedListArtifactData(3, ")").getStartNumber());
		assertEquals(")", new OrderedListArtifactData(3, ")").getDelimiter());
		assertEquals(2, new SectionArtifactData(2, "## A").getLevel());
		assertEquals("## A", new SectionArtifactData(2, "## A").getHeadingLine());
		assertEquals("*", new BulletListArtifactData("*").getMarker());
		assertEquals("___", new ThematicBreakArtifactData("___").getLiteral());
		assertEquals("<p>", new HtmlBlockArtifactData("<p>").getLiteral());
	}
}
