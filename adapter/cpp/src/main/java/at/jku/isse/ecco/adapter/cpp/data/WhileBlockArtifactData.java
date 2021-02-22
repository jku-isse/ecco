package at.jku.isse.ecco.adapter.cpp.data;

import at.jku.isse.ecco.artifact.ArtifactData;

import java.util.Objects;

public class WhileBlockArtifactData implements ArtifactData {

    private String whileblock;

    public WhileBlockArtifactData(String whileblock) {
        this.whileblock = whileblock;
    }

    public void setWhileBlock(String whileblock) {
        this.whileblock = whileblock;
    }

    public String getWhileBlock() {
        return this.whileblock;
    }

    @Override
    public String toString() {
        return this.whileblock;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.whileblock);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        WhileBlockArtifactData other = (WhileBlockArtifactData) obj;
        if (whileblock == null) {
            if (other.whileblock != null)
                return false;
        } else if (!whileblock.equals(other.whileblock))
            return false;
        return true;
    }

}
