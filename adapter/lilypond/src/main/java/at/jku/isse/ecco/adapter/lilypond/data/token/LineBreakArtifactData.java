package at.jku.isse.ecco.adapter.lilypond.data.token;

import at.jku.isse.ecco.adapter.lilypond.parce.ParceToken;

public class LineBreakArtifactData extends DefaultTokenArtifactData {

    private static final long serialVersionUID = -4566813844259713982L;

    public LineBreakArtifactData(ParceToken token) {
        super(token);
    }

    /**
     * Every line break is the same: its text also holds the next line's indentation and any blank
     * lines, so re-indenting a variant made all of its line breaks new tokens, and traced them to
     * whatever feature that variant was committed with. The writer still writes the text this
     * break was first committed with.
     */
    @Override
    protected String identity() {
        return "\n";
    }

    @Override
    public String toString() {
        return "LineBreakArtifactData{" +
                super.toString() + "}";
    }
}
