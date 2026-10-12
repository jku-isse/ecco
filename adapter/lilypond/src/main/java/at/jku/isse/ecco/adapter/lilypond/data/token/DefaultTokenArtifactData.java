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

    public DefaultTokenArtifactData(LilypondToken token)
    {
        this.pos = token.getPos();
        this.token = token.getText();
        this.action = token.getAction();
        this.meaning = token.getMeaning();
        this.attached = token.isAttached();
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
