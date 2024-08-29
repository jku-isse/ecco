package at.jku.isse.ecco.adapter.c.data;

import at.jku.isse.ecco.artifact.ArtifactData;

import java.util.Objects;

public class LineArtifactData implements ArtifactData {

    private String line;

    public LineArtifactData(String line) {
        this.line = line;
    }

    public String getLine() {
        return this.line;
    }

    @Override
    public String toString() {
        return this.line;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.line);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        LineArtifactData other = (LineArtifactData) obj;
        if (this.line == null) {
            return other.line == null;
        } else return this.line.equals(other.line);
    }
}
