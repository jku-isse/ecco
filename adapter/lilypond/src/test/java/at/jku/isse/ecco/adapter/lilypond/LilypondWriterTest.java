package at.jku.isse.ecco.adapter.lilypond;

import static org.junit.jupiter.api.Assertions.assertFalse;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.adapter.lilypond.data.ContextArtifactDataFactory;
import at.jku.isse.ecco.adapter.lilypond.data.token.DefaultTokenArtifactData;
import at.jku.isse.ecco.adapter.lilypond.parser.LilypondToken;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises {@link LilypondWriter}: the {@code collectTokenNodes()} traversal it shares with
 * {@link LilypondStringWriter}, and end-to-end file writing including failure handling. */
public class LilypondWriterTest {

	private final SerEntityFactory ef = new SerEntityFactory();
	private final LilypondWriter writer = new LilypondWriter();

	private Node.Op token(String text) {
		return ef.createOrderedNode(ef.createArtifact(new DefaultTokenArtifactData(new LilypondToken(0, text, "Token"))));
	}

	@Test
	void collectTokenNodesVisitsTokensInDocumentOrderIncludingATokenWithItsOwnChild() {
		Node.Op outer = ef.createOrderedNode(ef.createArtifact(ContextArtifactDataFactory.getContextArtifactData("outer")));
		Node.Op a = token("a");
		Node.Op b = token("b");
		Node.Op bNested = token("b-nested");
		Node.Op c = token("c");

		outer.addChild(a);
		outer.addChild(b);
		b.addChild(bNested); // a token node with its own child - the trickiest case: it must
		                      // still be visited itself AND recursed into
		outer.addChild(c);

		List<Node> collected = new ArrayList<>();
		LilypondWriter.collectTokenNodes(outer, collected);

		assertEquals(List.of(a, b, bNested, c), collected);
	}

	@Test
	void collectTokenNodesSkipsContextNodesButRecursesThroughThem() {
		Node.Op outer = ef.createOrderedNode(ef.createArtifact(ContextArtifactDataFactory.getContextArtifactData("outer")));
		Node.Op inner = ef.createOrderedNode(ef.createArtifact(ContextArtifactDataFactory.getContextArtifactData("inner")));
		Node.Op a = token("a");
		outer.addChild(inner);
		inner.addChild(a);

		List<Node> collected = new ArrayList<>();
		LilypondWriter.collectTokenNodes(outer, collected);

		assertEquals(List.of(a), collected);
	}

	@Test
	void writeJoinsTokensWithSpacesByDefault(@TempDir Path tempDir) throws IOException {
		Node.Op fileNode = ef.createOrderedNode(ef.createArtifact(new PluginArtifactData(writer.getPluginId(), Path.of("song.ly"))));
		fileNode.addChild(token("a"));
		fileNode.addChild(token("b"));
		fileNode.addChild(token("c"));

		Set<Node> input = new HashSet<>();
		input.add(fileNode);
		Path[] written = writer.write(tempDir, input);

		assertEquals(1, written.length);
		assertEquals("a b c", Files.readString(written[0]));
	}

	@Test
	void writeOfAFileWithNoTokensProducesAnEmptyFile(@TempDir Path tempDir) throws IOException {
		Node.Op fileNode = ef.createOrderedNode(ef.createArtifact(new PluginArtifactData(writer.getPluginId(), Path.of("empty.ly"))));

		Set<Node> input = new HashSet<>();
		input.add(fileNode);
		Path[] written = writer.write(tempDir, input);

		assertEquals("", Files.readString(written[0]));
	}

	@Test
	void writeRejectsANodeWithoutPluginArtifactData() {
		Node.Op notAFileNode = token("a");
		Set<Node> input = new HashSet<>();
		input.add(notAFileNode);

		assertThrows(EccoException.class, () -> writer.write(Path.of("."), input));
	}

	@Test
	void writeThrowsEccoExceptionInsteadOfSilentlySwallowingAnIOFailure(@TempDir Path tempDir) {
		// point the output path at a directory that doesn't exist and is never created, so the
		// write itself fails with a real IOException
		Node.Op fileNode = ef.createOrderedNode(ef.createArtifact(
				new PluginArtifactData(writer.getPluginId(), Path.of("no-such-dir/song.ly"))));
		fileNode.addChild(token("a"));

		Set<Node> input = new HashSet<>();
		input.add(fileNode);

		EccoException thrown = assertThrows(EccoException.class, () -> writer.write(tempDir, input));
		assertTrue(thrown.getCause() instanceof IOException);
	}

	private static DefaultTokenArtifactData at(int pos, String text, String action) {
		return new DefaultTokenArtifactData(new LilypondToken(pos, text, action));
	}

	/** Read right after the token before it (LilypondToken#isAttached), as lybar-2 records it. */
	private static DefaultTokenArtifactData attached(int pos, String text, String action) {
		LilypondToken token = new LilypondToken(pos, text, action);
		token.setAttached(true);
		return new DefaultTokenArtifactData(token);
	}

	/** As stored before lybar-2, which did not record whether a token was attached. */
	private static DefaultTokenArtifactData stored(int pos, String text, String action) throws ReflectiveOperationException {
		DefaultTokenArtifactData data = at(pos, text, action);
		java.lang.reflect.Field field = DefaultTokenArtifactData.class.getDeclaredField("attached");
		field.setAccessible(true);
		field.set(data, null);
		return data;
	}

	@Test
	void whereASpaceIsMusicItIsWrittenAsRead_whereverTheTokensWereRead() {
		// written apart: never run into one word or number - \shp 0 4 as \shp 04, a2 2 as a22, ^ ^ as ^^
		// (a marcato), \p e as \pe; the positions they were read at do not count (a token matched in a
		// later commit keeps the position of the commit that stored it)
		assertTrue(LilypondFormatter.appendSpace(at(5, "0", "Literal.Number"), at(6, "4", "Literal.Number"), "0", "4"));
		assertTrue(LilypondFormatter.appendSpace(at(0, "a2", "Text.Music.Pitch"), at(2, "2", "Literal.Number.Duration"), "a2", "2"));
		assertTrue(LilypondFormatter.appendSpace(at(0, "^", "Delimiter.Direction"), at(1, "^", "Delimiter.Direction"), "^", "^"));
		assertTrue(LilypondFormatter.appendSpace(at(5, "\\p", "Name.Builtin.Dynamic"), at(7, "e", "Text.Music.Pitch"), "\\p", "e"));
		// written together: together again, wherever the token before is from
		assertFalse(LilypondFormatter.appendSpace(at(9, "e", "Text.Music.Pitch"), attached(2, "2", "Literal.Number.Duration"), "e", "2"));
		assertFalse(LilypondFormatter.appendSpace(at(0, "^", "Delimiter.Direction"), attached(7, "^", "Literal.Character.Script"), "^", "^"));
		// where no space can join two tokens into one, by the actions: none before a bar check, none after \= (\=2( is a slur with id 2)
		assertFalse(LilypondFormatter.appendSpace(at(0, "c4", "Text.Music.Pitch"), at(3, "|", "Delimiter.Separator"), "c4", "|"));
		assertFalse(LilypondFormatter.appendSpace(at(0, "\\=", "Name.Builtin"), at(2, "2", "Literal.Number"), "\\=", "2"));
	}

	@Test
	void tokensStoredBeforeLybar2AreWrittenTogetherWhenTheyWereReadTogether() throws ReflectiveOperationException {
		// \\=2( and the pieces of 0.1 as parce split them
		assertFalse(LilypondFormatter.appendSpace(stored(0, "\\=", "Name.Builtin"), stored(2, "2", "Literal.Number"), "\\=", "2"));
		assertFalse(LilypondFormatter.appendSpace(stored(0, "0", "Literal.Number.Decimal"), stored(1, ".", "Literal.Number.Dot"), "0", "."));
		assertTrue(LilypondFormatter.appendSpace(stored(5, "0", "Literal.Number"), stored(7, "4", "Literal.Number"), "0", "4"));
	}

	@Test
	void anAttachedTokenIsOtherMusicThanTheSameTokenApart() {
		// c4 and c 4 (c, then a note of duration 4 repeating it)
		assertNotEquals(at(1, "4", "Literal.Number.Duration"), attached(1, "4", "Literal.Number.Duration"));
		assertEquals(attached(1, "4", "Literal.Number.Duration"), attached(9, "4", "Literal.Number.Duration"));
		assertEquals(attached(1, "4", "Literal.Number.Duration").hashCode(), attached(9, "4", "Literal.Number.Duration").hashCode());
	}

	/** Read [spaceBefore] after a token with the text [before]. */
	private static DefaultTokenArtifactData read(String before, String spaceBefore, String text, String action) {
		LilypondToken token = new LilypondToken(0, text, action);
		token.setBefore(before);
		token.setSpaceBefore(spaceBefore);
		return new DefaultTokenArtifactData(token);
	}

	@Test
	void afterTheTokenItWasReadAfter_aTokenIsWrittenWithTheWhitespaceItWasReadWith() {
		DefaultTokenArtifactData c4 = at(0, "c4", "Text.Music.Pitch");
		assertEquals("  ", LilypondFormatter.separator(c4, read("c4", "  ", "|", "Delimiter.Separator"), "c4", "|"));
		assertEquals("", LilypondFormatter.separator(at(0, "{", "Delimiter.Bracket"), read("{", "", "gis4", "Text.Music.Pitch"), "{", "gis4"));
		assertEquals("\t", LilypondFormatter.separator(c4, read("c4", "\t", "d4", "Text.Music.Pitch"), "c4", "d4"));
		// the same text is enough: a token matched in another commit, or respelled, reads the same
		assertEquals(" ", LilypondFormatter.separator(at(7, "c4", "Text.Music.Pitch"), read("c4", " ", "\\p", "Name.Builtin.Dynamic"), "c4", "\\p"));
	}

	@Test
	void afterAnotherToken_orStoredWithoutWhitespace_theWritersRulesDecide() {
		// read after d4, written after c4 (a checkout combining commits): no space before a bar check, by the rules
		assertEquals("", LilypondFormatter.separator(at(0, "c4", "Text.Music.Pitch"), read("d4", "  ", "|", "Delimiter.Separator"), "c4", "|"));
		// read first in its file
		assertEquals(" ", LilypondFormatter.separator(at(0, "c4", "Text.Music.Pitch"), read(null, "", "d4", "Text.Music.Pitch"), "c4", "d4"));
		// stored before the whitespace was recorded
		assertEquals(" ", LilypondFormatter.separator(at(0, "c4", "Text.Music.Pitch"), at(3, "d4", "Text.Music.Pitch"), "c4", "d4"));
	}

	@Test
	void whereASpaceIsMusic_theWhitespaceReadWithIsNeverWrittenAgainstIt() {
		// 4 read right after "a" (a4), written after "a" spelled "a2" (respelled): the space stays music - a2 4, not a24
		LilypondToken four = new LilypondToken(1, "4", "Literal.Number.Duration");
		four.setBefore("a");
		four.setSpaceBefore("");
		DefaultTokenArtifactData data = new DefaultTokenArtifactData(four);
		assertEquals(" ", LilypondFormatter.separator(at(0, "a", "Text.Music.Pitch"), data, "a2", "4"));
	}

	@Test
	void theFirstTokenReadIsWrittenWithTheWhitespaceBeforeIt() {
		assertEquals("\t", LilypondFormatter.leading(read(null, "\t", "\\layout", "Name.Builtin")));
		assertEquals("", LilypondFormatter.leading(read("c4", "\t", "d4", "Text.Music.Pitch")));
		assertEquals("", LilypondFormatter.leading(at(0, "d4", "Text.Music.Pitch")));
	}
}
