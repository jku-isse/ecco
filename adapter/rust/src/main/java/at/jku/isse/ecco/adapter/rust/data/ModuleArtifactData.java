package at.jku.isse.ecco.adapter.rust.data;

import at.jku.isse.ecco.artifact.ArtifactData;

import java.io.BufferedWriter;
import java.util.Objects;

public class ModuleArtifactData implements ArtifactData, RustWritable {
    private final String content;

    public ModuleArtifactData(String content) {
        this.content = content;
    }

    @Override
    public void write(BufferedWriter bw) throws java.io.IOException {
        bw.write(this.content);
        bw.newLine();
    }

    public String getContent() {
        return this.content;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ModuleArtifactData that = (ModuleArtifactData) o;
        return Objects.equals(content, that.content);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(content);
    }

    @Override
    public String toString() {
        return "ModuleArtifactData(content=" + this.getContent() + ")";
    }
}
