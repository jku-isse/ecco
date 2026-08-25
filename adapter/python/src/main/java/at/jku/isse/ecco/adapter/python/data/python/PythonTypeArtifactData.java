package at.jku.isse.ecco.adapter.python.data.python;

import at.jku.isse.ecco.artifact.ArtifactData;

import java.util.Arrays;

public class PythonTypeArtifactData implements ArtifactData {

    private byte[] bytes;

    public void setBytes(byte[] bytes) {
        this.bytes = bytes;
    }

    public byte[] getBytes() {
        return this.bytes;
    }

    @Override
    public String toString() {
        return Arrays.toString(this.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PythonTypeArtifactData that = (PythonTypeArtifactData) o;
        return Arrays.equals(bytes, that.bytes);
    }
}