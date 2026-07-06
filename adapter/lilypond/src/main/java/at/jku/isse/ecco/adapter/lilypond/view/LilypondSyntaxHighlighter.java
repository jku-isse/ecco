package at.jku.isse.ecco.adapter.lilypond.view;

import javafx.scene.paint.Color;

import java.util.List;
import java.util.Map;

/**
 * Maps parce/lilypond lexer token "actions" (e.g. "Text.Music.Pitch", "Keyword.Lyric",
 * "Name.Builtin", "Name.Function.Markup") to a text style for syntax highlighting in
 * {@link CodeViewer}. Parce assigns each token a dotted, Pygments-like hierarchical category (see
 * {@code LilypondParser_1.py} and {@code parce.lang.lilypond}).
 * <p>
 * The style groups and colors below are carried over from python-ly's {@code ly.colorize}
 * module (the {@code default_mapping()}/{@code default_scheme}, historically Frescobaldi's own
 * default LilyPond highlighting scheme, before it switched to parce), re-targeted at parce's
 * action categories instead of python-ly's own {@code ly.lex.lilypond} token classes so the
 * existing parce-based parsing pipeline doesn't need to change.
 * <p>
 * A token's action is first matched against {@link #RULES} by longest matching prefix (e.g.
 * "Name.Builtin.Dynamic" matches the "Name.Builtin.Dynamic" rule rather than the more general
 * "Name.Builtin" one). If no rule matches, the action's root category (the part before the first
 * '.') is looked up in {@link #ROOT_FALLBACKS}, so categories not explicitly listed still get a
 * sensible style instead of silently rendering as plain text.
 */
final class LilypondSyntaxHighlighter {

	record Style(Color color, boolean bold, boolean italic) {
		private static Style of(Color color) {
			return new Style(color, false, false);
		}

		private static Style bold(Color color) {
			return new Style(color, true, false);
		}

		private static Style boldPlain() {
			return new Style(null, true, false);
		}

		private static Style italic(Color color) {
			return new Style(color, false, true);
		}
	}

	private static final Style DEFAULT_STYLE = new Style(null, false, false);

	// colors from ly.colorize.default_scheme
	private static final Color FUNCTION_COLOR = Color.rgb(0, 0, 0xc0);   // built-in commands
	private static final Color VARIABLE_COLOR = Color.rgb(0, 0, 0xff);  // user commands/properties
	private static final Color VALUE_COLOR = Color.rgb(0x80, 0x80, 0);    // plain numeric/other literals
	private static final Color STRING_COLOR = Color.rgb(0xc0, 0, 0);
	private static final Color ESCAPE_COLOR = Color.rgb(0, 0x80, 0x80);
	private static final Color COMMENT_COLOR = Color.GRAY;
	private static final Color ERROR_COLOR = Color.RED;
	private static final Color DURATION_COLOR = Color.rgb(0, 0x80, 0x80);
	private static final Color MARKUP_COLOR = Color.rgb(0, 0x80, 0);
	private static final Color LYRIC_COLOR = Color.rgb(0, 0x60, 0);
	private static final Color GROB_COLOR = Color.rgb(0xc0, 0, 0xc0);
	private static final Color ACCENT_COLOR = Color.rgb(0xff, 0x80, 0); // articulation/dynamic/fingering

	// most specific rules; checked first, by longest matching prefix of the token's action
	private static final List<Map.Entry<String, Style>> RULES = List.of(
			Map.entry("Comment", Style.italic(COMMENT_COLOR)),
			Map.entry("Keyword.Markup", Style.of(MARKUP_COLOR)),
			Map.entry("Keyword.Lyric", Style.of(LYRIC_COLOR)),
			Map.entry("Keyword", Style.boldPlain()),
			Map.entry("Literal.String", Style.of(STRING_COLOR)),
			Map.entry("Literal.Number.Duration", Style.of(DURATION_COLOR)),
			Map.entry("Literal.Number", Style.of(VALUE_COLOR)),
			Map.entry("Literal.Character", Style.bold(ACCENT_COLOR)),
			// commands like \override, \tempo, \clef, \relative, \autoBeamOff, and markup
			// functions like \bold, \italic
			Map.entry("Name.Builtin.Dynamic", Style.bold(ACCENT_COLOR)),
			Map.entry("Name.Builtin.Context", Style.boldPlain()),
			Map.entry("Name.Builtin", Style.bold(FUNCTION_COLOR)),
			Map.entry("Name.Function", Style.bold(FUNCTION_COLOR)),
			// grob/property names, e.g. Hairpin.to-barline
			Map.entry("Name.Object.Grob", Style.of(GROB_COLOR)),
			Map.entry("Name.Attribute", Style.of(VARIABLE_COLOR)),
			// the "global" in "global = { ... }" is unstyled; a reference like "\global" is not
			Map.entry("Name.Variable.Definition", DEFAULT_STYLE),
			Map.entry("Name.Variable", Style.of(VARIABLE_COLOR)),
			Map.entry("Name.Constant.Context", Style.boldPlain()),
			Map.entry("Name.Type", Style.of(VARIABLE_COLOR)),
			Map.entry("Name.Script.Articulation", Style.bold(ACCENT_COLOR)),
			Map.entry("Name.Symbol.Spanner", Style.boldPlain()),
			Map.entry("Text.Lyric.LyricText", Style.of(LYRIC_COLOR)),
			Map.entry("Delimiter", Style.boldPlain())
	);

	// fallback by root category (text before the first '.'), for actions not covered above
	private static final Map<String, Style> ROOT_FALLBACKS = Map.of(
			"Comment", Style.italic(COMMENT_COLOR),
			"Keyword", Style.boldPlain(),
			"Literal", Style.of(VALUE_COLOR),
			"Name", Style.of(VARIABLE_COLOR),
			"Delimiter", Style.boldPlain()
	);

	private LilypondSyntaxHighlighter() {
	}

	static Style styleFor(String action) {
		if (action == null) {
			return DEFAULT_STYLE;
		}

		Style style = RULES.stream()
				.filter(rule -> action.startsWith(rule.getKey()))
				.max(Map.Entry.comparingByKey((a, b) -> Integer.compare(a.length(), b.length())))
				.map(Map.Entry::getValue)
				.orElse(null);
		if (style != null) {
			return style;
		}

		String root = action.contains(".") ? action.substring(0, action.indexOf('.')) : action;
		return ROOT_FALLBACKS.getOrDefault(root, DEFAULT_STYLE);
	}
}
