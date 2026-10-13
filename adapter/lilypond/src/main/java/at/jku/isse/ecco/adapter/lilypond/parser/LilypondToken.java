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
    private String spaceBefore;
    private String before;
    private String written;

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

    /**
     * The whitespace written between this token and the one before it, on this token's line (a line
     * break's own text holds the rest), or null if not known - for the writer, which writes it again
     * where the token follows the same one (see LilypondFormatter#separator).
     * @return Whitespace before the token, or null
     */
    public String getSpaceBefore() { return spaceBefore; }

    public void setSpaceBefore(String spaceBefore) { this.spaceBefore = spaceBefore; }

    /**
     * The text of the token read before this one, or null for the first.
     * @return Text of the token before, or null
     */
    public String getBefore() { return before; }

    public void setBefore(String before) { this.before = before; }

    /**
     * How the token was written, where its text is spelled otherwise ({@code name=} for the text
     * {@code name =} of a variable definition), or null.
     * @return The token as written, or null
     */
    public String getWritten() { return written; }

    public void setWritten(String written) { this.written = written; }

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
