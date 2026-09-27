package at.jku.isse.ecco.adapter.lilypond.data.token;

import at.jku.isse.ecco.adapter.lilypond.parce.ParceToken;

public class LineBreakArtifactData extends DefaultTokenArtifactData {

    private static final long serialVersionUID = -4566813844259713982L;

    public LineBreakArtifactData(ParceToken token) {
        super(token);
    }

    @Override
    public String toString() {
        return "LineBreakArtifactData{" +
                super.toString() + "}";
    }
}
