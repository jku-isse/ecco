package at.jku.isse.ecco.adapter.c.view;

import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight, regex-based re-lexer for the text {@link CCodeViewer} shows - each rendered line's
 * text is already a full line from a {@code LineArtifactData}, so it is re-tokenized here with a
 * small regex scanner rather than colored from a retained per-token lexer output. Mirrors the Java
 * adapter's highlighter, with a C keyword set and preprocessor-directive highlighting instead.
 */
final class CSyntaxHighlighter {

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
	private static final Style PREPROCESSOR_STYLE = Style.of(Color.rgb(0xc0, 0, 0xc0));
	private static final Style TYPE_STYLE = Style.of(Color.rgb(0, 0x80, 0x80));

	private static final Set<String> KEYWORDS = Set.of(
			"auto", "break", "case", "char", "const", "continue", "default", "do", "double",
			"else", "enum", "extern", "float", "for", "goto", "if", "inline", "int", "long",
			"register", "restrict", "return", "short", "signed", "sizeof", "static", "struct",
			"switch", "typedef", "union", "unsigned", "void", "volatile", "while",
			"_Bool", "_Complex", "_Imaginary"
	);

	private static final Set<String> TYPE_NAMES = Set.of(
			"size_t", "ssize_t", "ptrdiff_t", "wchar_t", "FILE", "NULL",
			"int8_t", "int16_t", "int32_t", "int64_t",
			"uint8_t", "uint16_t", "uint32_t", "uint64_t"
	);

	// MULTILINE (in addition to DOTALL) matters once tokenizeLines() below feeds this pattern a
	// whole multi-line artifact's text at once: without it, PREPROCESSOR's '^' anchor would only
	// match the very start of that whole text, not the start of each physical line within it.
	private static final Pattern TOKEN_PATTERN = Pattern.compile(
			"(?<COMMENT>//[^\\n]*|/\\*.*?\\*/)" +
					"|(?<PREPROCESSOR>^\\s*#\\s*\\w+)" +
					"|(?<STRING>\"(?:\\\\.|[^\"\\\\])*\")" +
					"|(?<CHAR>'(?:\\\\.|[^'\\\\])*')" +
					"|(?<NUMBER>\\b0[xX][0-9a-fA-F_]+[uUlL]*\\b|\\b\\d[\\d_]*\\.?[\\d_]*(?:[eE][+-]?\\d+)?[fFuUlL]*\\b)" +
					"|(?<IDENT>[A-Za-z_][A-Za-z0-9_]*)",
			Pattern.DOTALL | Pattern.MULTILINE
	);

	private CSyntaxHighlighter() {
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
			} else if (m.group("PREPROCESSOR") != null) {
				style = PREPROCESSOR_STYLE;
			} else if (m.group("STRING") != null || m.group("CHAR") != null) {
				style = STRING_STYLE;
			} else if (m.group("NUMBER") != null) {
				style = NUMBER_STYLE;
			} else if (m.group("IDENT") != null) {
				if (KEYWORDS.contains(value)) {
					style = KEYWORD_STYLE;
				} else if (TYPE_NAMES.contains(value) || value.endsWith("_t")) {
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

	/**
	 * Like {@link #tokenize}, but tokenizes the given (possibly multi-line) text as a whole - so a
	 * block comment spanning several physical lines is recognized as one token, since the regex
	 * above compiles with {@link Pattern#DOTALL} - then distributes the resulting tokens across one
	 * list per physical line. The returned list always has exactly as many elements as
	 * {@code text.split("\r\n|\r|\n", -1)} would, split at the same points, so a caller that renders
	 * one row per physical line can pair each row with its own pre-tokenized styling instead of
	 * re-tokenizing that row's text in isolation (which would lose track of a comment that opened on
	 * an earlier row).
	 */
	static List<List<Token>> tokenizeLines(String text) {
		List<List<Token>> rows = new ArrayList<>();
		List<Token> current = new ArrayList<>();
		for (Token token : tokenize(text)) {
			String[] parts = token.text().split("\r\n|\r|\n", -1);
			for (int i = 0; i < parts.length; i++) {
				if (!parts[i].isEmpty()) {
					current.add(new Token(parts[i], token.style()));
				}
				if (i < parts.length - 1) {
					rows.add(current);
					current = new ArrayList<>();
				}
			}
		}
		rows.add(current);
		return rows;
	}
}
