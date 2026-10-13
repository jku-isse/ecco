package at.jku.isse.ecco.adapter.lilypond;

import at.jku.isse.ecco.adapter.lilypond.data.token.DefaultTokenArtifactData;

public class LilypondFormatter {

    /**
     * What goes between [d], written as [text], and [next], written as [nextText]: the whitespace [next]
     * was read with, where it was read right after a token with [d]'s text - so a file checked out as it
     * was committed comes back as it was written - and otherwise a space or nothing, as
     * {@link #appendSpace(DefaultTokenArtifactData, DefaultTokenArtifactData, String, String)} decides.
     * Where a space is music ({@link #glue}), whether [next] is attached decides either way.
     */
    public static String separator(DefaultTokenArtifactData d, DefaultTokenArtifactData next, String text, String nextText) {
        if (next.follows(d)) {
            String space = next.getSpaceBefore();
            if (!glue(text, nextText) || space.isEmpty() == Boolean.TRUE.equals(next.getAttached())) {
                return space;
            }
        }
        return appendSpace(d, next, text, nextText) ? " " : "";
    }

    /** What goes before [first], the first token written: the whitespace it was read with, if it was read first. */
    public static String leading(DefaultTokenArtifactData first) {
        return first.first() ? first.getSpaceBefore() : "";
    }

    /**
     * Whether a space goes between [d], written as [text], and [next], written as [nextText]. Where the
     * two would read as one word or number without it, the space is music: [next] is written right
     * after [d] if it was read right after the token before it ({@code c4}, {@code ^^}), else apart
     * ({@code a2 2}, not {@code a22}; {@code \\p e}, not {@code \\pe}). Otherwise, by their actions.
     *
     * <p>Tokens stored before lybar-2 do not know whether they were attached; for them the positions they
     * were read at decide (written together if the one started where the other ended). A token matched in
     * a later commit keeps the position of the commit that stored it, so in a checkout combining commits
     * that guesses - the reason lybar-2 records it.
     */
    public static boolean appendSpace(DefaultTokenArtifactData d, DefaultTokenArtifactData next, String text, String nextText) {
        if (next != null) {
            if (next.getAttached() == null) {
                if (next.getPos() == d.getPos() + d.getText().length()) {
                    return false;
                }
                if (glue(text, nextText)) {
                    return true;
                }
            } else if (glue(text, nextText)) {
                return !next.getAttached();
            }
        }
        return appendSpace(d, next);
    }

    /** Whether [a] followed directly by [b] would read as one word or number: 0 4 as 04, 4. 4 as 4.4, ^ ^ as ^^. */
    public static boolean glue(String a, String b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
            return false;
        }
        char x = a.charAt(a.length() - 1), y = b.charAt(0);
        return Character.isLetterOrDigit(x) && Character.isLetterOrDigit(y)
                || x == '.' && Character.isDigit(y)
                || Character.isDigit(x) && y == '.'
                // two direction or script signs: ^ ^ as ^^ is a marcato, - - as -- a tenuto
                || "-^_".indexOf(x) >= 0 && "-^_".indexOf(y) >= 0;
    }

    @SuppressWarnings("RedundantIfStatement")
    public static boolean appendSpace(DefaultTokenArtifactData d, DefaultTokenArtifactData next) {
        String ca = d.getAction();
        // the id of a slur or phrasing slur goes right after \= (\=2( - apart, lymodel loses the slur)
        if ("\\=".equals(d.getText())) {
            return false;
        }
        if (ca.equals("Delimiter.Operator.Assignment")) {
            return true;

        } else if (ca.equals(LilypondReader.PARSER_ACTION_LINEBREAK) ||
                ca.equals("Delimiter.Separator") ||
                // c4:32, c:m7, c/e
                ca.equals("Delimiter.Separator.Chord") ||
                ca.startsWith("Delimiter.ModeChange.") ||
                ca.startsWith("Delimiter.Operator.") ||
                ca.startsWith("Delimiter.Scheme.") ||
                ca.equals("Comment")) {
            return false;
        }

        if (next != null) {
            String na = next.getAction();
            if (ca.equals("Literal.String") && na.equals("Literal.String")) {
                return false;
            } else if (ca.startsWith("Literal.Number") && na.startsWith("Literal.Number")) {
                return false;
            }
            if (na.equals(LilypondReader.PARSER_ACTION_LINEBREAK) ||
                na.equals("Delimiter.Separator")) {
                return false;
            }

            if (na.startsWith("Pitch.") ||
                    na.startsWith("Number.Duration.") ||
                    na.startsWith("Name.Builtin.Dynamic") ||
                    na.startsWith("Name.Script.Articulation") &&
                            !ca.startsWith("Literal.") ||
                    na.startsWith("Name.Symbol.Spanner") ||
                    na.startsWith("Delimiter.Separator") ||
                    na.equals("Text.Music.Pitch.Octave") ||
                    na.equals("Delimiter.Direction")) {
                return false;
            }

            if ((ca.startsWith("Text.Music.") ||
                    ca.startsWith("Literal.Number.Duration")) &&
                    na.startsWith("Literal.Number.Duration")) {
                return false;
            }

            if ((ca.equals("Delimiter.Direction")) &&
                    na.startsWith("Literal.Character.Script")) {
                return false;
            }
        }

        return true;
    }
}
