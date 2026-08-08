package at.jku.isse.ecco.adapter.lilypond;

import at.jku.isse.ecco.adapter.lilypond.parce.ParceToken;
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
		LilypondNode<ParceToken> string = new LilypondNode<>("LilyPond.string", null);
		string.setLevel(0);

		LilypondNode<ParceToken> result = LilyEccoTransformer.transform(string);

		assertSame(string, result);
		assertNull(result.getNext());
	}

	@Test
	void transformStringMergesConsecutiveLiteralStringPieces() {
		LilypondNode<ParceToken> string = new LilypondNode<>("LilyPond.string", null);
		string.setLevel(0);
		LilypondNode<ParceToken> part1 = string.append("Literal.String", new ParceToken(0, "hello", "Literal.String"), 1);
		LilypondNode<ParceToken> part2 = part1.append("Literal.String", new ParceToken(5, "world", "Literal.String"), 1);
		part2.append("Other", new ParceToken(10, "x", "Other"), 0);

		LilypondNode<ParceToken> result = LilyEccoTransformer.transform(string);

		assertEquals("LilyPond.string", result.getName());
		assertEquals("helloworld", result.getData().getText());
		assertEquals("Other", result.getNext().getName());
	}

	@Test
	void transformSchemeNumberWithNothingAfterItReturnsItUnchanged() {
		LilypondNode<ParceToken> number = new LilypondNode<>("SchemeLily.number", null);
		number.setLevel(0);

		LilypondNode<ParceToken> result = LilyEccoTransformer.transform(number);

		assertSame(number, result);
		assertNull(result.getNext());
	}

	@Test
	void transformSchemeNumberMergesConsecutiveDigits() {
		LilypondNode<ParceToken> number = new LilypondNode<>("SchemeLily.number", null);
		number.setLevel(0);
		LilypondNode<ParceToken> d1 = number.append("Literal.Number.Int", new ParceToken(0, "1", "Literal.Number.Int"), 1);
		LilypondNode<ParceToken> d2 = d1.append("Literal.Number.Int", new ParceToken(1, "2", "Literal.Number.Int"), 1);
		d2.append("Other", new ParceToken(2, "x", "Other"), 0);

		LilypondNode<ParceToken> result = LilyEccoTransformer.transform(number);

		assertEquals("SchemeLily.number", result.getName());
		assertEquals("12", result.getData().getText());
	}

	@Test
	void transformOfALyricmodeBlockProducesOneMergedLyricNode() {
		LilypondNode<ParceToken> lyricmode = new LilypondNode<>("Keyword.Lyric", new ParceToken(0, "\\lyricmode", "Keyword.Lyric"));
		lyricmode.setLevel(0);
		LilypondNode<ParceToken> lyriclistTok = lyricmode.append("LilyPond.lyriclist", new ParceToken(10, "", "LilyPond.lyriclist"), 0);
		LilypondNode<ParceToken> bracketStart = lyriclistTok.append("Delimiter.Bracket.Start", new ParceToken(11, "", "Delimiter.Bracket.Start"), 1);
		LilypondNode<ParceToken> word1 = bracketStart.append("Text.Lyric.Word", new ParceToken(12, "Ah", "Text.Lyric.Word"), 1);
		word1.append("Text.Lyric.Word", new ParceToken(15, "yes", "Text.Lyric.Word"), 1);

		LilypondNode<ParceToken> result = LilyEccoTransformer.transform(lyricmode);

		assertEquals("Keyword.Lyric", result.getName());
		// the empty-text Delimiter.Bracket.Start token still gets its own trailing space appended
		// by the collection loop (nothing special-cases it), hence the double space
		assertEquals("\\lyricmode  Ah yes ", result.getData().getText());
		assertNull(result.getNext());
	}

	@Test
	void transformLyriclistBailsOutWhenIsLyriclistsOwnGateIsBypassedAndNextIsNull() throws Exception {
		LilypondNode<ParceToken> lyricmode = new LilypondNode<>("Keyword.Lyric", new ParceToken(0, "\\lyricmode", "Keyword.Lyric"));
		lyricmode.setLevel(0);
		// no next at all - isLyriclist() would already reject this, but transformLyriclist() must
		// not NPE if ever invoked directly (see class javadoc)

		LilypondNode<ParceToken> result = invokeTransformLyriclist(lyricmode);

		assertSame(lyricmode, result);
	}

	@Test
	void transformLyriclistBailsOutWhenLyricstosListHasNoFirstItem() throws Exception {
		LilypondNode<ParceToken> lyricmode = new LilypondNode<>("Keyword.Lyric", new ParceToken(0, "\\lyricsto", "Keyword.Lyric"));
		lyricmode.setLevel(0);
		LilypondNode<ParceToken> lyricsto = lyricmode.append("LilyPond.lyricsto", new ParceToken(1, "\\lyricsto", "LilyPond.lyricsto"), 0);
		lyricsto.append("LilyPond.list", new ParceToken(2, "", "LilyPond.list"), 0);
		// LilyPond.list has nothing after it at all - the fixed two-hop assumption
		// (lyricsto -> list -> first item) has nothing to land on for its second hop

		LilypondNode<ParceToken> result = invokeTransformLyriclist(lyricmode);

		assertSame(lyricmode, result);
	}

	@SuppressWarnings("unchecked")
	private static LilypondNode<ParceToken> invokeTransformLyriclist(LilypondNode<ParceToken> lyricmode) throws Exception {
		Method m = LilyEccoTransformer.class.getDeclaredMethod("transformLyriclist", LilypondNode.class);
		m.setAccessible(true);
		return (LilypondNode<ParceToken>) m.invoke(null, lyricmode);
	}
}
