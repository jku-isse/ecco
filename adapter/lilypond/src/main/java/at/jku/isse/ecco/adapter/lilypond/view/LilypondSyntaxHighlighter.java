package at.jku.isse.ecco.adapter.lilypond.view;

import javafx.scene.paint.Color;

import java.util.List;
import java.util.Map;

/**
 * Maps parce/lilypond lexer token "actions" (e.g. "Text.Music.Pitch", "Keyword.Lyric",
 * "Literal.String") to a text style for syntax highlighting in {@link CodeViewer}. Parce assigns
 * each token a dotted, Pygments-like hierarchical category (see {@code LilypondParser_1.py}); a
 * token's action is matched against the rules below by longest matching prefix, e.g.
 * "Name.Builtin.Dynamic" matches the "Name.Builtin.Dynamic" rule rather than the more general
 * "Name.Builtin" or "Name" ones.
 */
final class LilypondSyntaxHighlighter {

	record Style(Color color, boolean bold, boolean italic) {
		private static Style of(Color color) {
			return new Style(color, false, false);
		}

		private static Style bold(Color color) {
			return new Style(color, true, false);
		}

		private static Style italic(Color color) {
			return new Style(color, false, true);
		}
	}

	private static final Style DEFAULT_STYLE = Style.of(Color.BLACK);

	private static final List<Map.Entry<String, Style>> RULES = List.of(
			Map.entry("Comment", Style.italic(Color.GRAY)),
			Map.entry("Keyword", Style.bold(Color.rgb(0, 0, 200))),
			Map.entry("Literal.String", Style.of(Color.rgb(163, 21, 21))),
			Map.entry("Literal.Number", Style.of(Color.rgb(9, 134, 88))),
			Map.entry("Name.Builtin.Dynamic", Style.bold(Color.rgb(121, 94, 38))),
			Map.entry("Name.Builtin", Style.of(Color.rgb(121, 94, 38))),
			Map.entry("Name.Variable", Style.of(Color.rgb(38, 94, 121))),
			Map.entry("Name.Constant", Style.of(Color.rgb(38, 94, 121)))
	);

	private LilypondSyntaxHighlighter() {
	}

	static Style styleFor(String action) {
		if (action == null) {
			return DEFAULT_STYLE;
		}
		return RULES.stream()
				.filter(rule -> action.startsWith(rule.getKey()))
				.max(Map.Entry.comparingByKey((a, b) -> Integer.compare(a.length(), b.length())))
				.map(Map.Entry::getValue)
				.orElse(DEFAULT_STYLE);
	}
}
