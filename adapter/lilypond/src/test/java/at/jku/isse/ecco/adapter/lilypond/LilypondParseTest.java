package at.jku.isse.ecco.adapter.lilypond;

import at.jku.isse.ecco.adapter.lilypond.data.token.DefaultTokenArtifactData;
import at.jku.isse.ecco.adapter.lilypond.parser.py4j.LilypondPython;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * What the tree-sitter parse makes of what tree-sitter's grammar reads flat or not at all: markup and
 * drum notes after their command (the { } is no child of \markup or \drums there), a chord's or
 * tremolo's {@code :}, Scheme keywords and characters, a file that is empty or lacks its last brace.
 */
@Timeout(120)
public class LilypondParseTest {

	private static boolean readable() {
		try {
			LilypondPython.resolve();
			return true;
		} catch (at.jku.isse.ecco.EccoException e) {
			return false;
		}
	}

	/** [text] read as a file and written back. */
	private static String roundTrip(String text) throws Exception {
		assumeTrue(readable(), "needs a python with tree-sitter, py4j and lymodel");
		Path base = Files.createTempDirectory("lilypond-round-trip");
		Files.writeString(base.resolve("a.ly"), text);
		Set<Node.Op> nodes = new LilypondReader(new SerEntityFactory()).read(base, new Path[]{Path.of("a.ly")});
		return new LilypondStringWriter().write(new java.util.HashSet<>(nodes))[0];
	}

	/** Spacing that is not music comes back as written: Scheme, braces, scripts, tabs, CRLF, blank lines, an assignment's =. */
	@org.junit.jupiter.params.ParameterizedTest(name = "musical tokens: {0}")
	@org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
	public void aFileIsWrittenBackAsItWasRead(boolean musical) throws Exception {
		String previous = System.getProperty("ecco.lilypond.musicalTokens");
		System.setProperty("ecco.lilypond.musicalTokens", String.valueOf(musical));
		try {
			aFileIsWrittenBackAsItWasRead();
		} finally {
			if (previous == null) System.clearProperty("ecco.lilypond.musicalTokens");
			else System.setProperty("ecco.lilypond.musicalTokens", previous);
		}
	}

	private static void aFileIsWrittenBackAsItWasRead() throws Exception {
		for (String text : new String[] {
				"\\markup \\override #'(baseline-skip . 2) { a b }\n",
				"#(define-public hide-empty-staves #f)\n",
				"tr = #(define-music-function (m)\n   (ly:music?)\n   #{ \\transpose f f $m #})\n",
				"{ \\tuplet 3/2 {gis4) gis8~ }| e8.^>^\\sfzf cis16 }\n",
				"\t\\layout { }\n",
				"{\r\n\tc4  d\t e |   \r\n  \r\n\tf1 }\r\n",
				"one= { c4 }\ntwo  =  { d4 }\nthree =\n{ e4 }\n",
				"{ c4 d }   \n\n\n",
				"{ \\override Hairpin.to-barline = ##f  c4 }\n",
				"\\new Voice = \"s\" { c4 d }\n\\new Lyrics \\lyricsto \"s\" {  Hel -- lo\n    world }\n",
		}) {
			assertEquals(text, roundTrip(text), text);
		}
	}

	/** The tokens of [text] read as a file, in order. */
	private static List<DefaultTokenArtifactData> tokens(String text) throws Exception {
		assumeTrue(readable(), "needs a python with tree-sitter, py4j and lymodel");
		Path base = Files.createTempDirectory("lilypond-parse");
		Files.writeString(base.resolve("a.ly"), text);
		Set<Node.Op> nodes = new LilypondReader(new SerEntityFactory()).read(base, new Path[]{Path.of("a.ly")});
		List<DefaultTokenArtifactData> tokens = new ArrayList<>();
		Deque<Node> stack = new ArrayDeque<>(nodes);
		while (!stack.isEmpty()) {
			Node node = stack.pop();
			if (node.getArtifact() != null && node.getArtifact().getData() instanceof DefaultTokenArtifactData data)
				tokens.add(data);
			List<? extends Node> children = node.getChildren();
			for (int i = children.size() - 1; i >= 0; i--)
				stack.push(children.get(i));
		}
		return tokens;
	}

	private static String action(List<DefaultTokenArtifactData> tokens, String text) {
		return tokens.stream().filter(t -> t.getText().equals(text)).findFirst()
				.orElseThrow(() -> new AssertionError("no token " + text + " in " + texts(tokens))).getAction();
	}

	private static List<String> texts(List<DefaultTokenArtifactData> tokens) {
		return tokens.stream().map(t -> t.getText() + " " + t.getAction()).toList();
	}

	@Test
	void markupIsReadByWords_notAsNotesSlursOrVariables() throws Exception {
		// a variable there made the next note lose the pitch it is written relative to
		List<DefaultTokenArtifactData> tokens = tokens("\\relative c' { c4^\\markup \\bold { hello (etc.) } d4 }\n"
				+ "\\markup \\column { \\line { a b } }\n");
		assertEquals("Text", action(tokens, "hello"));
		assertEquals("Text", action(tokens, "(etc.)"));
		assertEquals("Text", action(tokens, "a"));
		assertEquals("Name.Function.Markup", action(tokens, "\\line"));
		assertTrue(tokens.stream().noneMatch(t -> t.getAction().startsWith("Name.Variable") || t.getAction().contains("Slur")),
				texts(tokens).toString());
	}

	@Test
	void notesAfterDrumsAreDrums() throws Exception {
		List<DefaultTokenArtifactData> tokens = tokens("{ \\drums { bd4 sn } }\ndr = \\drummode { hh8 }\n");
		assertEquals("Text.Music.Pitch.Drum", action(tokens, "bd"));
		assertEquals("Text.Music.Pitch.Drum", action(tokens, "sn"));
		assertEquals("Text.Music.Pitch.Drum", action(tokens, "hh"));
	}

	@Test
	void theColonOfATremoloOrChordJoinsWhatFollows() throws Exception {
		List<DefaultTokenArtifactData> tokens = tokens("{ c4:32 }\n\\chordmode { c:m7 }\n");
		assertEquals("Delimiter.Separator.Chord", action(tokens, ":"));
		assertEquals("Literal.Number", action(tokens, "32"));
		assertEquals("Text", action(tokens, "m"), "a chord's step, not a variable");
		// written c4:32, not c4: 32
		int colon = tokens.indexOf(tokens.stream().filter(t -> t.getText().equals(":")).findFirst().orElseThrow());
		assertFalse(LilypondFormatter.appendSpace(tokens.get(colon), tokens.get(colon + 1), ":", "32"));
	}

	@Test
	void schemeKeywordsAndCharactersAreOneWord() throws Exception {
		List<DefaultTokenArtifactData> tokens = tokens("#(define x #:kw)\n#(display #\\x)\n#(list #(1 2))\n");
		assertEquals("Literal.Symbol.Keyword", action(tokens, "#:kw"));
		assertEquals("Literal.Symbol.Character", action(tokens, "#\\x"));
		assertEquals("Delimiter.OpenParen", action(tokens, "#("));
		assertTrue(tokens.stream().noneMatch(t -> t.getAction().equals("Text")), texts(tokens).toString());
	}

	@Test
	void nothingIsReadAsAnEmptyToken() throws Exception {
		assertTrue(tokens("").isEmpty());
		// tree-sitter supposes the missing } - an empty token
		assertTrue(tokens("{ c4 d\n").stream().noneMatch(t -> t.getText().isEmpty()));
	}

	@Test
	void positionsAreJavas() throws Exception {
		String text = "\\markup { \"🎵\" } { c4 }\n";
		List<DefaultTokenArtifactData> tokens = tokens(text);
		assertEquals(text.indexOf("c4"), tokens.stream().filter(t -> t.getText().equals("c")).findFirst().orElseThrow().getPos());
	}
}
