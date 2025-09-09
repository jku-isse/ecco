package at.jku.isse.ecco.adapter.rust.data;

import at.jku.isse.ecco.artifact.ArtifactData;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Objects;

public class LineArtifactData implements ArtifactData, RustWritable {

    private final String line;

    public LineArtifactData(String line) {
        this.line = line;
    }

    public void write(BufferedWriter bw) throws IOException {
        bw.write(this.line);
        bw.newLine();
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof LineArtifactData)) return false;
        final LineArtifactData other = (LineArtifactData) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$line = this.getLine();
        final Object other$line = other.getLine();
        if (this$line == null ? other$line != null : !this$line.equals(other$line)) return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof LineArtifactData;
    }

    public int hashCode() {
        return Objects.hash(this.line);
    }

    public String getLine() {
        return this.line;
    }

    public String toString() {
        return this.getLine();
    }
}
