package at.jku.isse.ecco.adapter.lilypond.parser;

import java.util.Objects;

/**
 * A token read from a LilyPond file (LilypondParser_2.py, lymodel's tree-sitter parse): its text, where it
 * was read, and its action - a dotted category named as parce named them (Text.Music.Pitch, Keyword.Lyric).
 */
public class LilypondToken {
    private final int pos;
    private String text;
    private final String action;
    private Object transformationData;
    private String meaning;
    private boolean attached;

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

    public Object getTransformationData() { return transformationData; }

    /**
     * What the token means however it is spelled - a note's absolute pitch and written-out
     * duration, from lybar (see LilypondParser_1.py) - or null to go by its text.
     * @return Meaning of token, or null
     */
    public String getMeaning() { return meaning; }

    public void setMeaning(String meaning) { this.meaning = meaning; }

    /**
     * Whether the token was read directly after the one before it, where the two would read as one
     * word or number apart from that ({@code c4}, not {@code c 4}; see LilypondFormatter#glue) - so
     * whether a space between them changes the music.
     * @return Whether the token is attached to the one before it
     */
    public boolean isAttached() { return attached; }

    public void setAttached(boolean attached) { this.attached = attached; }

    public LilypondToken(int pos, String text, String action) {
        this.pos = pos;
        this.text = text;
        this.action = action;
    }

    public LilypondToken(int pos, String text, String action, Object transformationData) {
        this(pos, text, action);
        this.transformationData = transformationData;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LilypondToken that = (LilypondToken) o;
        return text.equals(that.text) && action.equals(that.action);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, action);
    }

    @Override
    public String toString() {
        return  "LilypondToken{" +
                "pos=" + pos +
                ", action='" + action + "'" +
                ", text='" + text + "'}";
    }
}
