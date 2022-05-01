package at.jku.isse.ecco.adapter.lilypond;

import at.jku.isse.ecco.adapter.lilypond.data.token.DefaultTokenArtifactData;

class LilypondFormatter {
    @SuppressWarnings("RedundantIfStatement")
    public static boolean appendSpace(DefaultTokenArtifactData d, DefaultTokenArtifactData next) {
        if (d.getAction().equals("Delimiter.Operator.Assignment")) {
            return true;

        } else if (d.getAction().equals(LilypondReader.PARSER_ACTION_LINEBREAK) ||
                d.getAction().equals("Delimiter.Separator") ||
                d.getAction().startsWith("Delimiter.ModeChange.") ||
                d.getAction().startsWith("Delimiter.Operator.") ||
                d.getAction().startsWith("Delimiter.Scheme.") ||
                d.getAction().equals("Comment")) {
            return false;
        }

        if (next != null) {
            if (d.getAction().equals("Literal.String") && next.getAction().equals("Literal.String")) {
                return false;
            } else if (d.getAction().startsWith("Literal.Number") && next.getAction().startsWith("Literal.Number")) {
                return false;
            }
            if (next.getAction().equals(LilypondReader.PARSER_ACTION_LINEBREAK) ||
                next.getAction().equals("Delimiter.Separator")) {
                return false;
            }

            if (next.getAction().startsWith("Pitch.") ||
                    next.getAction().startsWith("Number.Duration.") ||
                    next.getAction().startsWith("Name.Builtin.Dynamic") ||
                    next.getAction().startsWith("Name.Script.Articulation") &&
                            !d.getAction().startsWith("Literal.") ||
                    next.getAction().startsWith("Name.Symbol.Spanner") ||
                    next.getAction().startsWith("Delimiter.Separator") ||
                    next.getAction().equals("Text.Music.Pitch.Octave") ||
                    next.getAction().equals("Delimiter.Direction")) {
                return false;
            }

            if ((d.getAction().startsWith("Text.Music.") ||
                    d.getAction().startsWith("Literal.Number.Duration")) &&
                    next.getAction().startsWith("Literal.Number.Duration")) {
                return false;
            }

            if ((d.getAction().equals("Delimiter.Direction")) &&
                    next.getAction().startsWith("Literal.Character.Script")) {
                return false;
            }
        }

        return true;
    }
}
