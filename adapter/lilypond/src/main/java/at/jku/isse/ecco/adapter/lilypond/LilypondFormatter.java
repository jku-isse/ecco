package at.jku.isse.ecco.adapter.lilypond;

import at.jku.isse.ecco.adapter.lilypond.data.token.DefaultTokenArtifactData;

class LilypondFormatter {

    /**
     * Whether a space goes between [d], written as [text], and [next], written as [nextText]. Two tokens
     * that were written together in the file they were read from (the one starts where the other ends)
     * are written together again - \\=2(, ^^, the pieces of a number parce split; two that were not are
     * never run into one word (\\shp 0 4 was written \\shp 04, a2 2 as a22). Otherwise, by their actions.
     */
    public static boolean appendSpace(DefaultTokenArtifactData d, DefaultTokenArtifactData next, String text, String nextText) {
        if (next != null) {
            if (next.getPos() == d.getPos() + d.getText().length()) {
                return false;
            }
            if (glue(text, nextText)) {
                return true;
            }
        }
        return appendSpace(d, next);
    }

    /** Whether [a] followed directly by [b] would read as one word or number: 0 4 as 04, 4. 4 as 4.4, ^ ^ as ^^. */
    static boolean glue(String a, String b) {
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
        if (ca.equals("Delimiter.Operator.Assignment")) {
            return true;

        } else if (ca.equals(LilypondReader.PARSER_ACTION_LINEBREAK) ||
                ca.equals("Delimiter.Separator") ||
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
