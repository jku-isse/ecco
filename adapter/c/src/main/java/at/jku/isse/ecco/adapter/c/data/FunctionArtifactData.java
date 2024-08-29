package at.jku.isse.ecco.adapter.c.data;

import at.jku.isse.ecco.artifact.ArtifactData;

import java.util.Objects;

public class FunctionArtifactData  implements ArtifactData {

    private String signature;

    public FunctionArtifactData(String signature){
        this.signature = signature;
    }

    public String getSignature(){
        return this.signature;
    }

    @Override
    public String toString() {
        return this.signature;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.signature);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        FunctionArtifactData other = (FunctionArtifactData) obj;
        if (this.signature == null) {
            return other.signature == null;
        } else return this.signature.equals(other.signature);
    }
}
