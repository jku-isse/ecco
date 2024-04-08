package at.jku.isse.ecco.adapter.typescript.data;

import at.jku.isse.ecco.artifact.ArtifactData;

import java.util.Objects;

public class DoBlockArtifactData extends AbstractArtifactData {

    private String doblock;

    public DoBlockArtifactData(String doblock) {
        this.doblock = doblock;
    }

    public void setDoBlock(String doblock) {
        this.doblock = doblock;
    }

    public String getDoBlock() {
        return this.doblock;
    }

    @Override
    public String toString() {
        return this.doblock;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.doblock);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        DoBlockArtifactData other = (DoBlockArtifactData) obj;
        if (doblock == null) {
            if (other.doblock != null)
                return false;
        } else if (!doblock.equals(other.doblock))
            return false;
        return true;
    }

}
