package at.jku.isse.ecco.adapter.typescript.data;

import java.util.Objects;

public class ArrowFunctionArtifactData extends AbstractArtifactData {

    private final String nameAndParams;

    public ArrowFunctionArtifactData(String block) {
        this.nameAndParams = block;
    }

    @Override
    public String toString() {
        return this.nameAndParams;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.nameAndParams);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ArrowFunctionArtifactData other = (ArrowFunctionArtifactData) obj;
        if (nameAndParams == null) {
            return other.nameAndParams == null;
        } else return nameAndParams.equals(other.nameAndParams);
    }

}
