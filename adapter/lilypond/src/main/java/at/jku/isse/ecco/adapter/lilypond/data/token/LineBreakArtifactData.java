package at.jku.isse.ecco.adapter.lilypond.data.token;

import at.jku.isse.ecco.adapter.lilypond.parce.ParceToken;

import java.util.Objects;

public class LineBreakArtifactData extends DefaultTokenArtifactData {

    public LineBreakArtifactData(ParceToken token) {
        super(token);
    }

    @Override
    public String toString() {
        return "LineBreakArtifactData{" +
                super.toString() + "}";
    }
}
