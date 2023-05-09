package at.jku.isse.ecco.adapter.cpp.data;

import at.jku.isse.ecco.artifact.ArtifactData;

import java.util.Objects;

public class SwitchBlockArtifactData implements ArtifactData {

    private String switchblock;

    public SwitchBlockArtifactData(String switchblock) {
        this.switchblock = switchblock;
    }

    public void setSwitchBlock(String switchblock) {
        this.switchblock = switchblock;
    }

    public String getSwitchBlock() {
        return this.switchblock;
    }

    @Override
    public String toString() {
        return this.switchblock;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.switchblock);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        SwitchBlockArtifactData other = (SwitchBlockArtifactData) obj;
        if (switchblock == null) {
            if (other.switchblock != null)
                return false;
        } else if (!switchblock.equals(other.switchblock))
            return false;
        return true;
    }

}
