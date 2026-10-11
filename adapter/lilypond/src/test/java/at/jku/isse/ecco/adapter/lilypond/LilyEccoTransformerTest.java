package at.jku.isse.ecco.adapter.lilypond;

import at.jku.isse.ecco.adapter.lilypond.parser.LilypondToken;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Exercises {@link LilyEccoTransformer}, especially the malformed-input hardening added for real
 * (if narrow) NPE risks: a node whose next() unexpectedly returns null, or a {@code \lyricsto}
 * block whose nesting doesn't match {@code transformLyriclist()}'s fixed-hop assumption.
 *
 * <p>{@code transformString}/{@code transformSchemeNumber}'s hardening is reachable through the
 * public {@link LilyEccoTransformer#transform} entry point directly (nothing validates their input
 * shape before they're called). {@code transformLyriclist}'s two added guards are NOT reachable that
 * way - {@code isLyriclist()} already validates the exact same hops before ever calling it, so by
 * the time {@code transform()} would call it, those hops are already guaranteed non-null. Those two
 * cases are exercised directly via reflection instead, as defense-in-depth coverage for what would
 * happen if that assumption (a private, informally-enforced invariant between two methods) ever
 * stops holding.
 */
public class LilyEccoTransformerTest {

	@Test
	void transformOfNullHeadReturnsNullWithoutThrowing() {
		assertNull(LilyEccoTransformer.transform(null));
	}

	@Test
	void transformStringWithNothingAfterItReturnsItUnchanged() {
		LilypondNode<LilypondToken> string = new LilypondNode<>("LilyPond.string", null);
		string.setLevel(0);

		LilypondNode<LilypondToken> result = LilyEccoTransformer.transform(string);

		assertSame(string, result);
		assertNull(result.getNext());
	}

	@Test
	void transformStringMergesConsecutiveLiteralStringPieces() {
		LilypondNode<LilypondToken> string = new LilypondNode<>("LilyPond.string", null);
		string.setLevel(0);
		LilypondNode<LilypondToken> part1 = string.append("Literal.String", new LilypondToken(0, "hello", "Literal.String"), 1);
		LilypondNode<LilypondToken> part2 = part1.append("Literal.String", new LilypondToken(5, "world", "Literal.String"), 1);
		part2.append("Other", new LilypondToken(10, "x", "Other"), 0);

		LilypondNode<LilypondToken> result = LilyEccoTransformer.transform(string);

		assertEquals("LilyPond.string", result.getName());
		assertEquals("helloworld", result.getData().getText());
		assertEquals("Other", result.getNext().getName());
	}

	@Test
	void transformSchemeNumberWithNothingAfterItReturnsItUnchanged() {
		LilypondNode<LilypondToken> number = new LilypondNode<>("SchemeLily.number", null);
		number.setLevel(0);

		LilypondNode<LilypondToken> result = LilyEccoTransformer.transform(number);

		assertSame(number, result);
		assertNull(result.getNext());
	}

	@Test
	void transformSchemeNumberMergesConsecutiveDigits() {
		LilypondNode<LilypondToken> number = new LilypondNode<>("SchemeLily.number", null);
		number.setLevel(0);
		LilypondNode<LilypondToken> d1 = number.append("Literal.Number.Int", new LilypondToken(0, "1", "Literal.Number.Int"), 1);
		LilypondNode<LilypondToken> d2 = d1.append("Literal.Number.Int", new LilypondToken(1, "2", "Literal.Number.Int"), 1);
		d2.append("Other", new LilypondToken(2, "x", "Other"), 0);

		LilypondNode<LilypondToken> result = LilyEccoTransformer.transform(number);

		assertEquals("SchemeLily.number", result.getName());
		assertEquals("12", result.getData().getText());
	}

	@Test
	void transformOfALyricmodeBlockProducesOneMergedLyricNode() {
		LilypondNode<LilypondToken> lyricmode = new LilypondNode<>("Keyword.Lyric", new LilypondToken(0, "\\lyricmode", "Keyword.Lyric"));
		lyricmode.setLevel(0);
		LilypondNode<LilypondToken> lyriclistTok = lyricmode.append("LilyPond.lyriclist", new LilypondToken(10, "", "LilyPond.lyriclist"), 0);
		LilypondNode<LilypondToken> bracketStart = lyriclistTok.append("Delimiter.Bracket.Start", new LilypondToken(11, "", "Delimiter.Bracket.Start"), 1);
		LilypondNode<LilypondToken> word1 = bracketStart.append("Text.Lyric.Word", new LilypondToken(12, "Ah", "Text.Lyric.Word"), 1);
		word1.append("Text.Lyric.Word", new LilypondToken(15, "yes", "Text.Lyric.Word"), 1);

		boolean merge = LilyEccoTransformer.MERGE_LYRICS;
		LilyEccoTransformer.MERGE_LYRICS = true;
		LilypondNode<LilypondToken> result;
		try {
			result = LilyEccoTransformer.transform(lyricmode);
		} finally {
			LilyEccoTransformer.MERGE_LYRICS = merge;
		}

		assertEquals("Keyword.Lyric", result.getName());
		// the empty-text Delimiter.Bracket.Start token still gets its own trailing space appended
		// by the collection loop (nothing special-cases it), hence the double space
		assertEquals("\\lyricmode  Ah yes ", result.getData().getText());
		assertNull(result.getNext());
	}

	@Test
	void withSyllableLyricsALyricmodeBlockKeepsEverySyllable() {
		LilypondNode<LilypondToken> lyricmode = new LilypondNode<>("Keyword.Lyric", new LilypondToken(0, "\\lyricmode", "Keyword.Lyric"));
		lyricmode.setLevel(0);
		LilypondNode<LilypondToken> lyriclistTok = lyricmode.append("LilyPond.lyriclist", new LilypondToken(10, "", "LilyPond.lyriclist"), 0);
		LilypondNode<LilypondToken> bracketStart = lyriclistTok.append("Delimiter.Bracket.Start", new LilypondToken(11, "", "Delimiter.Bracket.Start"), 1);
		LilypondNode<LilypondToken> word1 = bracketStart.append("Text.Lyric.Word", new LilypondToken(12, "Ah", "Text.Lyric.Word"), 1);
		word1.append("Text.Lyric.Word", new LilypondToken(15, "yes", "Text.Lyric.Word"), 1);

		boolean merge = LilyEccoTransformer.MERGE_LYRICS;
		LilyEccoTransformer.MERGE_LYRICS = false;
		try {
			LilypondNode<LilypondToken> result = LilyEccoTransformer.transform(lyricmode);
			assertEquals("\\lyricmode", result.getData().getText());
			assertEquals("yes", result.getNext().getNext().getNext().getNext().getData().getText());
		} finally {
			LilyEccoTransformer.MERGE_LYRICS = merge;
		}
	}

	@Test
	void transformLyriclistBailsOutWhenIsLyriclistsOwnGateIsBypassedAndNextIsNull() throws Exception {
		LilypondNode<LilypondToken> lyricmode = new LilypondNode<>("Keyword.Lyric", new LilypondToken(0, "\\lyricmode", "Keyword.Lyric"));
		lyricmode.setLevel(0);
		// no next at all - isLyriclist() would already reject this, but transformLyriclist() must
		// not NPE if ever invoked directly (see class javadoc)

		LilypondNode<LilypondToken> result = invokeTransformLyriclist(lyricmode);

		assertSame(lyricmode, result);
	}

	@Test
	void transformLyriclistBailsOutWhenLyricstosListHasNoFirstItem() throws Exception {
		LilypondNode<LilypondToken> lyricmode = new LilypondNode<>("Keyword.Lyric", new LilypondToken(0, "\\lyricsto", "Keyword.Lyric"));
		lyricmode.setLevel(0);
		LilypondNode<LilypondToken> lyricsto = lyricmode.append("LilyPond.lyricsto", new LilypondToken(1, "\\lyricsto", "LilyPond.lyricsto"), 0);
		lyricsto.append("LilyPond.list", new LilypondToken(2, "", "LilyPond.list"), 0);
		// LilyPond.list has nothing after it at all - the fixed two-hop assumption
		// (lyricsto -> list -> first item) has nothing to land on for its second hop

		LilypondNode<LilypondToken> result = invokeTransformLyriclist(lyricmode);

		assertSame(lyricmode, result);
	}

	@SuppressWarnings("unchecked")
	private static LilypondNode<LilypondToken> invokeTransformLyriclist(LilypondNode<LilypondToken> lyricmode) throws Exception {
		Method m = LilyEccoTransformer.class.getDeclaredMethod("transformLyriclist", LilypondNode.class);
		m.setAccessible(true);
		return (LilypondNode<LilypondToken>) m.invoke(null, lyricmode);
	}
}
