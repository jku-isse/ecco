package at.jku.isse.ecco.adapter.typescript.data;

import at.jku.isse.ecco.artifact.ArtifactData;

import java.util.Objects;

public class VariableAssignmentData extends AbstractArtifactData {
    private String id;

    public VariableAssignmentData(String name) {
        this.id = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String name) {
        this.id = name;
    }

    @Override
    public String toString() {
        return this.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        VariableAssignmentData other = (VariableAssignmentData) obj;
        if (id == null) {
            return other.id == null;
        } else return id.equals(other.id);
    }
}
