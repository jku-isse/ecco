package at.jku.isse.ecco.adapter.lilypond.parce;

import java.util.Objects;

/**
 * Represents a wrapper for a Parce-Token (<a href="https://parce.info/tree.html">https://parce.info</a>).
 */
public class ParceToken {
    private int pos;
    private String text;
    private String action;
    private String postWhitespace = "";

    /**
     * Returns position of token in original text.
     * @return Position of token in original text.
     */
    public int getPos() { return pos; }

    /**
     * Returns text of token.
     * @return Text of token.
     */
    public String getText() { return text; }

    public void setText(String s) {
        text = s;
    }

    /**
     * Returns action of the token (e.g. Text.Lyrics).
     * @return Action of token.
     */
    public String getAction() { return action; }

    public String getPostWhitespace() { return postWhitespace; }

    public void setPostWhitespace(String ws) {
        postWhitespace = ws;
    }

    public ParceToken(int pos, String text, String action) {
        this.pos = pos;
        this.text = text;
        this.action = action;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ParceToken that = (ParceToken) o;
        return text.equals(that.text) && action.equals(that.action);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, action);
    }

    @Override
    public String toString() {
        return "LilypondParceToken{" +
                "pos=" + pos +
                ", text='" + text + "'" +
                ", action='" + action + "'}";
    }
}
