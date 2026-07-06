package at.jku.isse.ecco.adapter.java.view;

import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight, regex-based re-lexer for the Java-looking text {@link JavaCodeViewer} reconstructs
 * from an artifact tree. Unlike lilypond's parce-based highlighter, the Java adapter's tree does
 * not retain per-token lexer output (each artifact already stores a summarized text fragment such
 * as a whole statement or field declaration), so there is no token stream to color directly -
 * instead each rendered line's text is re-tokenized here with a small regex scanner.
 */
final class JavaSyntaxHighlighter {

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

	record Token(String text, Style style) {
	}

	private static final Style PLAIN = new Style(null, false, false);
	private static final Style KEYWORD_STYLE = Style.bold(Color.rgb(0, 0, 0xc0));
	private static final Style STRING_STYLE = Style.of(Color.rgb(0xc0, 0, 0));
	private static final Style NUMBER_STYLE = Style.of(Color.rgb(0x80, 0x80, 0));
	private static final Style COMMENT_STYLE = Style.italic(Color.GRAY);
	private static final Style ANNOTATION_STYLE = Style.of(Color.rgb(0xc0, 0, 0xc0));
	private static final Style TYPE_STYLE = Style.of(Color.rgb(0, 0x80, 0x80));

	private static final Set<String> KEYWORDS = Set.of(
			"abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
			"continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
			"for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
			"new", "package", "private", "protected", "public", "return", "short", "static", "strictfp",
			"super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void",
			"volatile", "while", "var", "record", "yield", "sealed", "permits",
			"true", "false", "null"
	);

	private static final Pattern TOKEN_PATTERN = Pattern.compile(
			"(?<COMMENT>//[^\\n]*|/\\*.*?\\*/)" +
					"|(?<STRING>\"(?:\\\\.|[^\"\\\\])*\")" +
					"|(?<CHAR>'(?:\\\\.|[^'\\\\])*')" +
					"|(?<NUMBER>\\b0[xX][0-9a-fA-F_]+[lL]?\\b|\\b\\d[\\d_]*\\.?[\\d_]*(?:[eE][+-]?\\d+)?[fFdDlL]?\\b)" +
					"|(?<ANNOTATION>@[A-Za-z_][A-Za-z0-9_]*)" +
					"|(?<IDENT>[A-Za-z_$][A-Za-z0-9_$]*)",
			Pattern.DOTALL
	);

	private JavaSyntaxHighlighter() {
	}

	static List<Token> tokenize(String text) {
		List<Token> tokens = new ArrayList<>();
		Matcher m = TOKEN_PATTERN.matcher(text);
		int last = 0;
		while (m.find()) {
			if (m.start() > last) {
				tokens.add(new Token(text.substring(last, m.start()), PLAIN));
			}

			String value = m.group();
			Style style;
			if (m.group("COMMENT") != null) {
				style = COMMENT_STYLE;
			} else if (m.group("STRING") != null || m.group("CHAR") != null) {
				style = STRING_STYLE;
			} else if (m.group("NUMBER") != null) {
				style = NUMBER_STYLE;
			} else if (m.group("ANNOTATION") != null) {
				style = ANNOTATION_STYLE;
			} else if (m.group("IDENT") != null) {
				if (KEYWORDS.contains(value)) {
					style = KEYWORD_STYLE;
				} else if (Character.isUpperCase(value.charAt(0))) {
					// heuristic: capitalized identifiers are usually type names
					style = TYPE_STYLE;
				} else {
					style = PLAIN;
				}
			} else {
				style = PLAIN;
			}

			tokens.add(new Token(value, style));
			last = m.end();
		}
		if (last < text.length()) {
			tokens.add(new Token(text.substring(last), PLAIN));
		}
		return tokens;
	}
}
