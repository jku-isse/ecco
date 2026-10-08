package at.jku.isse.ecco.adapter.lilypond;

import at.jku.isse.ecco.adapter.lilypond.data.token.DefaultTokenArtifactData;
import at.jku.isse.ecco.adapter.lilypond.parce.ParceToken;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LilypondRelativizerTest {

	private static DefaultTokenArtifactData token(String text, String action, String meaning) {
		ParceToken t = new ParceToken(0, text, action);
		t.setMeaning(meaning);
		return new DefaultTokenArtifactData(t);
	}

	private static List<String> write(Object... textActionMeaning) {
		List<DefaultTokenArtifactData> tokens = new ArrayList<>();
		for (int i = 0; i < textActionMeaning.length; i += 3) {
			tokens.add(token((String) textActionMeaning[i], (String) textActionMeaning[i + 1], (String) textActionMeaning[i + 2]));
		}
		return LilypondRelativizer.texts(tokens);
	}

	private static final String P = "Text.Music.Pitch";

	@Test
	void aNoteIsSpelledAgainstTheNoteWrittenBeforeIt() {
		// fis2 gis8 cis8 fis8: the last fis was stored as spelled after a gis, and is now written
		// after a cis
		assertEquals(List.of("\\relative", "c'", "{", "fis2", "gis8", "cis8", "fis,8", "}"),
				write("\\relative", "Name.Builtin", null, "c'", P, "\\relative@7", "{", "Delimiter.Bracket.Start", null,
						"fis2", P, "fis'2@10~", "gis8", P, "gis'8@11~2", "cis8", P, "cis''8@14~8", "fis8", P, "fis'8@10~8",
						"}", "Delimiter.Bracket.End", null));
	}

	@Test
	void aRoundTripIsUnchanged() {
		assertEquals(List.of("\\relative", "c'", "{", "r4", "fis2", "gis8", "<c e>4", "}"),
				write("\\relative", "Name.Builtin", null, "c'", P, "\\relative@7", "{", "Delimiter.Bracket.Start", null,
						"r4", "Text.Music.Rest", "r4~", "fis2", P, "fis'2@10~4", "gis8", P, "gis'8@11~2",
						"<", "Delimiter.Chord.Start", null, "c", P, "c''@14", "e", P, "e''@16", ">4", "Delimiter.Chord.End", ">4~8",
						"}", "Delimiter.Bracket.End", null).stream().reduce(new ArrayList<String>(), (l, x) -> { l.add(x); return l; }, (a, b) -> a)
						.stream().collect(java.util.stream.Collectors.collectingAndThen(java.util.stream.Collectors.toList(), l -> {
							// chord tokens joined for the comparison
							List<String> j = new ArrayList<>(l.subList(0, 6)); j.add(l.get(6) + l.get(7) + " " + l.get(8) + l.get(9)); j.add(l.get(10)); return j; })));
	}

	@Test
	void anImplicitDurationIsWrittenWhenTheNoteBeforeItHasAnother() {
		// stored as `fis` after a quarter; now written after an eighth
		assertEquals(List.of("\\relative", "c'", "{", "fis2", "gis8", "fis4", "}"),
				write("\\relative", "Name.Builtin", null, "c'", P, "\\relative@7", "{", "Delimiter.Bracket.Start", null,
						"fis2", P, "fis'2@10~", "gis8", P, "gis'8@11~2", "fis", P, "fis'4@10~4", "}", "Delimiter.Bracket.End", null));
	}

	@Test
	void withoutMeaningsNothingChanges() {
		assertEquals(List.of("\\relative", "c'", "{", "gis8", "cis8", "fis8", "}"),
				write("\\relative", "Name.Builtin", null, "c'", P, null, "{", "Delimiter.Bracket.Start", null,
						"gis8", P, null, "cis8", P, null, "fis8", P, null, "}", "Delimiter.Bracket.End", null));
	}
}
