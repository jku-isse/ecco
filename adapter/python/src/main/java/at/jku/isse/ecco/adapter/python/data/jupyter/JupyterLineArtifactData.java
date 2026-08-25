package at.jku.isse.ecco.adapter.python.data.jupyter;

import at.jku.isse.ecco.artifact.ArtifactData;

import java.util.Objects;

public class JupyterLineArtifactData implements ArtifactData {

    private String line;
    public JupyterLineArtifactData(String line) {
        this.line = line;
    }

    public void setLine(String line) {
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
        return Objects.hash(line);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JupyterLineArtifactData that = (JupyterLineArtifactData) o;
        return Objects.equals(line, that.line);
    }
}