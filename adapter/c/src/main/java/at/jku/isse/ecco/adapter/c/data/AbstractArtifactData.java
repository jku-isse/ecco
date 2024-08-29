package at.jku.isse.ecco.adapter.c.data;

import at.jku.isse.ecco.artifact.ArtifactData;

import java.util.Objects;

public class AbstractArtifactData implements ArtifactData {

    private String id;

    public AbstractArtifactData(String name) {
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
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        AbstractArtifactData other = (AbstractArtifactData) obj;
        if (id == null) {
            return other.id == null;
        } else return id.equals(other.id);
    }
}
