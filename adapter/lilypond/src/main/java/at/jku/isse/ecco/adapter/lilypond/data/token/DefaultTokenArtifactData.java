package at.jku.isse.ecco.adapter.lilypond.data.token;

import at.jku.isse.ecco.adapter.lilypond.parser.LilypondToken;
import at.jku.isse.ecco.artifact.ArtifactData;

import java.util.Objects;

public class DefaultTokenArtifactData implements ArtifactData {

    private static final long serialVersionUID = 6126587744534296251L;
    private final int pos;
    private final String token;
    private final String action;
    private final String meaning;
    // null in tokens stored before lybar-2 (Java serialization leaves a field it does not find null)
    private final Boolean attached;
    // not part of what makes two tokens the same, null in tokens stored before them - see
    // getSpaceBefore() and follows()
    private final String spaceBefore;
    private final int before;
    private final String written;

    public DefaultTokenArtifactData(LilypondToken token)
    {
        this.pos = token.getPos();
        this.token = token.getText();
        this.action = token.getAction();
        this.meaning = token.getMeaning();
        this.attached = token.isAttached();
        this.spaceBefore = common(token.getSpaceBefore());
        this.before = token.getBefore() == null ? 0 : hash(token.getBefore());
        this.written = token.getText().equals(token.getWritten()) ? null : token.getWritten();
    }

    /** [text]'s hash, never 0 (no token before). */
    private static int hash(String text) {
        int hash = text.hashCode();
        return hash == 0 ? 1 : hash;
    }

    /** One instance of the usual spaces, so serialization writes each once. */
    private static String common(String space) {
        if (space == null) return null;
        if (space.isEmpty()) return "";
        if (space.equals(" ")) return " ";
        return space;
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
     * Returns the token's action: its category, as the parser sends it (see LilypondToken).
     * @return Action of token
     */
    public String getAction() {
        return this.action;
    }

    @Override
    public boolean isWhitespace() {
        return this.token.isBlank();
    }

    @Override
    public String toString() {
        return "Token '" + token + "', Action '" + action + "'";
    }

    /**
     * What makes two tokens the same: their text, unless the parser knows what it means however it
     * is spelled (a note's absolute pitch and duration, see LilypondToken#getMeaning) or a subclass
     * knows that two spellings mean the same thing (see {@link LineBreakArtifactData}). Two variants that differ only
     * there must not differ to ECCO, or the difference is traced to a feature.
     * @return The token's identity
     */
    protected String identity() {
        if (this.meaning == null) return this.token;
        // after @ the pitch in steps, after ~ the duration before it in its file: for the writer
        int end = this.meaning.length();
        for (char mark : new char[] {'@', '~'}) {
            int at = this.meaning.indexOf(mark);
            if (at >= 0) end = Math.min(end, at);
        }
        return this.meaning.substring(0, end);
    }

    /**
     * What the token means however it is spelled - see LilypondToken#getMeaning - or null.
     * @return Meaning of the token, or null
     */
    public String getMeaning() {
        return this.meaning;
    }

    /**
     * Whether the token was read directly after the one before it, where the two would otherwise read
     * as one word or number (see LilypondToken#isAttached), or null for a token stored before lybar-2,
     * which did not record it. Part of what makes two tokens the same: {@code c4} and {@code c 4} are
     * other music.
     * @return Whether the token is attached to the one before it, or null if unknown
     */
    public Boolean getAttached() {
        return this.attached;
    }

    /**
     * The whitespace this token was read with after the token before it, on its line (see
     * LilypondToken#getSpaceBefore), or null for a token stored before it was recorded. Not part of
     * what makes two tokens the same: spacing that is not music is no difference between variants -
     * a token matched in a later commit keeps the spacing of the commit that stored it.
     * @return Whitespace before the token, or null if unknown
     */
    public String getSpaceBefore() {
        return this.spaceBefore;
    }

    /**
     * Whether this token was read right after [previous] (its text, by hash), so that the whitespace
     * between them can be written again as it was read.
     * @param previous The token written before this one
     * @return Whether this token followed a token with the same text when it was read
     */
    public boolean follows(DefaultTokenArtifactData previous) {
        return this.spaceBefore != null && this.before != 0 && hash(previous.getText()) == this.before;
    }

    /**
     * The token as it was written, where that is not its text (see LilypondToken#getWritten), or null.
     * Not part of what makes two tokens the same.
     * @return The token as written, or null
     */
    public String getWritten() {
        return this.written;
    }

    /**
     * Whether this token was read first in its file, with what [getSpaceBefore()] holds before it.
     * @return Whether the token was read first
     */
    public boolean first() {
        return this.spaceBefore != null && this.before == 0;
    }

    private boolean attached() {
        return Boolean.TRUE.equals(this.attached);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DefaultTokenArtifactData that = (DefaultTokenArtifactData) o;
        return identity().equals(that.identity())
                && action.equals(that.action)
                && attached() == that.attached();
    }

    @Override
    public int hashCode() {
        // unchanged for tokens that are not attached
        int hash = Objects.hash(identity(), action);
        return attached() ? 31 * hash + 1 : hash;
    }
}
