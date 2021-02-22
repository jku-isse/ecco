package at.jku.isse.ecco.adapter.cpp.data;

import at.jku.isse.ecco.artifact.ArtifactData;

import java.util.Objects;

public class IfBlockArtifactData implements ArtifactData {

    private String ifblock;

    public IfBlockArtifactData(String ifblock) {
        this.ifblock = ifblock;
    }

    public void setIfBlock(String ifblock) {
        this.ifblock = ifblock;
    }

    public String getIfBlock() {
        return this.ifblock;
    }

    @Override
    public String toString() {
        return this.ifblock;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.ifblock);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        IfBlockArtifactData other = (IfBlockArtifactData) obj;
        if (ifblock == null) {
            if (other.ifblock != null)
                return false;
        } else if (!ifblock.equals(other.ifblock))
            return false;
        return true;
    }

}
