package at.jku.isse.ecco.adapter.typescript.data;

import at.jku.isse.ecco.artifact.ArtifactData;

import java.util.Objects;

public class SwitchBlockArtifactData extends AbstractArtifactData {

    private static final long serialVersionUID = 79752043809092308L;

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


    /** Its children's order matters (cases, members, declarations) - see ArtifactData#requiresOrderedArtifact. */
    @Override
    public boolean requiresOrderedArtifact() {
        return true;
    }
}
