package at.jku.isse.ecco.adapter.lilypond.data.token;

import at.jku.isse.ecco.adapter.lilypond.parce.ParceToken;
import at.jku.isse.ecco.artifact.ArtifactData;

import java.util.Objects;

public class DefaultTokenArtifactData implements ArtifactData {

    private static final long serialVersionUID = 6126587744534296251L;
    private final int pos;
    private final String token;
    private final String action;
    private final String meaning;

    public DefaultTokenArtifactData(ParceToken token)
    {
        this.pos = token.getPos();
        this.token = token.getText();
        this.action = token.getAction();
        this.meaning = token.getMeaning();
    }

    /**
     * Returns starting position of token in file.
     * @return Position of token in file
     */
    public int getPos() { return pos; }

    /**
     * Returns the token text.
     * @return Text of token
     */
    public String getText() {
        return this.token;
    }

    /**
     * Returns action of Parce token.
     * @return Action of token
     */
    public String getAction() {
        return this.action;
    }

    @Override
    public String toString() {
        return "Token '" + token + "', Action '" + action + "'";
    }

    /**
     * What makes two tokens the same: their text, unless the parser knows what it means however it
     * is spelled (a note's absolute pitch and duration, see ParceToken#getMeaning) or a subclass
     * knows that two spellings mean the same thing (see {@link LineBreakArtifactData}). Two variants that differ only
     * there must not differ to ECCO, or the difference is traced to a feature.
     * @return The token's identity
     */
    protected String identity() {
        return this.meaning != null ? this.meaning : this.token;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DefaultTokenArtifactData that = (DefaultTokenArtifactData) o;
        return identity().equals(that.identity())
                && action.equals(that.action);
    }

    @Override
    public int hashCode() {
        return Objects.hash(identity(), action);
    }
}
