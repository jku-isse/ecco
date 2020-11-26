package at.jku.isse.ecco.adapter.cpp.data;

import at.jku.isse.ecco.artifact.ArtifactData;

import java.util.Objects;

public class ForBlockArtifactData implements ArtifactData {

    private String forblock;

    public ForBlockArtifactData(String forblock) {
        this.forblock = forblock;
    }

    public void setForBlock(String forblock) {
        this.forblock = forblock;
    }

    public String getForBlock() {
        return this.forblock;
    }

    @Override
    public String toString() {
        return this.forblock;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.forblock);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ForBlockArtifactData other = (ForBlockArtifactData) obj;
        if (forblock == null) {
            if (other.forblock != null)
                return false;
        } else if (!forblock.equals(other.forblock))
            return false;
        return true;
    }

}
