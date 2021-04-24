package at.jku.isse.ecco.adapter.cpp.data;

import at.jku.isse.ecco.artifact.ArtifactData;

import java.util.Objects;

public class ProblemBlockArtifactData implements ArtifactData {

    private String problemblock;

    public ProblemBlockArtifactData(String problemblock) {
        this.problemblock = problemblock;
    }

    public void setProblemBlock(String problemblock) {
        this.problemblock = problemblock;
    }

    public String getProblemBlock() {
        return this.problemblock;
    }

    @Override
    public String toString() {
        return this.problemblock;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.problemblock);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ProblemBlockArtifactData other = (ProblemBlockArtifactData) obj;
        if (problemblock == null) {
            if (other.problemblock != null)
                return false;
        } else if (!problemblock.equals(other.problemblock))
            return false;
        return true;
    }

}
