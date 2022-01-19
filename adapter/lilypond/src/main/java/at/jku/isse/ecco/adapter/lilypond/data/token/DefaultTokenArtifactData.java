package at.jku.isse.ecco.adapter.lilypond.data.token;

import at.jku.isse.ecco.adapter.lilypond.parce.ParceToken;
import at.jku.isse.ecco.artifact.ArtifactData;

import java.util.Objects;

public class DefaultTokenArtifactData implements ArtifactData {
    private final int pos;
    private final String token;
    private final String action;

    public DefaultTokenArtifactData(ParceToken token)
    {
        this.pos = token.getPos();
        this.token = token.getText();
        this.action = token.getAction();
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DefaultTokenArtifactData that = (DefaultTokenArtifactData) o;
        return token.equals(that.token)
                && action.equals(that.action);
    }

    @Override
    public int hashCode() {
        return Objects.hash(token, action);
    }
}
