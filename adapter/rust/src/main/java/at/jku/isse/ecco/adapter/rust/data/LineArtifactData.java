package at.jku.isse.ecco.adapter.rust.data;

import at.jku.isse.ecco.artifact.ArtifactData;
import lombok.Getter;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Objects;

@Getter
public class LineArtifactData implements ArtifactData, RustWritable {

    private final String line;

    public LineArtifactData(String line) {
        this.line = line;
    }

    public void write(BufferedWriter bw) throws IOException {
        bw.write(this.line);
        bw.newLine();
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
