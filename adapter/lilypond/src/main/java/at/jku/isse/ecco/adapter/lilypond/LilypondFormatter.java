package at.jku.isse.ecco.adapter.lilypond;

import at.jku.isse.ecco.adapter.lilypond.data.token.DefaultTokenArtifactData;

class LilypondFormatter {
    public static boolean appendSpace(DefaultTokenArtifactData d, DefaultTokenArtifactData next) {
        if (d.getAction().equals(LilypondReader.PARSER_ACTION_LINEBREAK) ||
            d.getAction().equals("Delimiter.Separator") ||
            d.getAction().startsWith("Delimiter.ModeChange.") ||
            d.getAction().equals("Comment")) {
            return false;
        }

        if (next != null) {
            if (next.getAction().equals(LilypondReader.PARSER_ACTION_LINEBREAK) ||
                next.getAction().equals("Delimiter.Separator")) {
                return false;
            }

            if (next.getAction().startsWith("Pitch.") ||
                    next.getAction().startsWith("Number.Duration.") ||
                    next.getAction().startsWith("Name.Builtin.Dynamic") ||
                    next.getAction().startsWith("Name.Script.Articulation") ||
                    next.getAction().startsWith("Name.Symbol.Spanner") ||
                    next.getAction().startsWith("Delimiter.Separator")) {
                return false;
            }

            if (d.getAction().startsWith("Text.Music.") &&
                    next.getAction().startsWith("Literal.Number.Duration")) {
                return false;
            }
        }

        return true;
    }
}