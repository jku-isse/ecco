package at.jku.isse.ecco.adapter.typescript.data;

import java.util.Objects;

public class EnumArtifactData extends AbstractArtifactData {

    private final String enumName;


    public EnumArtifactData(String block) {
        this.enumName = block;
    }


    @Override
    public String toString() {
        return this.enumName;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.enumName);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        EnumArtifactData other = (EnumArtifactData) obj;
        if (enumName == null) {
            return other.enumName == null;
        } else return enumName.equals(other.enumName);
    }

}
