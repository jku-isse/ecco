package at.jku.isse.ecco.adapter.cpp.data;

import at.jku.isse.ecco.artifact.ArtifactData;

import java.util.Objects;

public class CaseBlockArtifactData implements ArtifactData {

    private static final long serialVersionUID = -7333597943527789824L;

    private String caseblock;

    private Boolean sameline;

    public CaseBlockArtifactData(String caseblock) {
        this.caseblock = caseblock;
    }

    public void setCaseblock(String caseblock) {
        this.caseblock = caseblock;
    }

    public String getCaseblock() {
        return this.caseblock;
    }

    public Boolean getSameline() {
        return sameline;
    }

    public void setSameline(Boolean sameline) {
        this.sameline = sameline;
    }

    @Override
    public String toString() {
        return this.caseblock;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.caseblock);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        CaseBlockArtifactData other = (CaseBlockArtifactData) obj;
        if (caseblock == null) {
            if (other.caseblock != null)
                return false;
        } else if (!caseblock.equals(other.caseblock))
            return false;
        return true;
    }

}
