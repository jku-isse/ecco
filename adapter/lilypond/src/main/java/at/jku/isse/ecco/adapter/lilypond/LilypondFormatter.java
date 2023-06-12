package at.jku.isse.ecco.adapter.lilypond;

import at.jku.isse.ecco.adapter.lilypond.data.token.DefaultTokenArtifactData;

class LilypondFormatter {
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
