package at.jku.isse.ecco.adapter.golang.data;

import at.jku.isse.ecco.artifact.ArtifactData;

import java.util.Objects;

public class TokenArtifactData implements ArtifactData {
    private final String token;
    private final int tokenIndex;

    private final int row;

    private final int column;

    public TokenArtifactData(String token, int tokenId, int row, int column) {
        this.token = token;
        this.tokenIndex = tokenId;
        this.row = row;
        this.column = column;
    }

    public String getToken() {
        return this.token;
    }

    public int getRow() {
        return row;
    }

    public int getColumn() {
        return column;
    }

    public int getTokenIndex() {
        return tokenIndex;
    }

    @Override
    public String toString() {
        return "TokenArtifactData{" +
                "token='" + token + '\'' +
                ", tokenId=" + tokenIndex +
                ", row=" + row +
                ", column=" + column +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TokenArtifactData that = (TokenArtifactData) o;
        return getTokenIndex() == that.getTokenIndex() && getRow() == that.getRow() && getColumn() == that.getColumn() && Objects.equals(getToken(), that.getToken());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getToken(), getTokenIndex(), getRow(), getColumn());
    }
}
