package at.jku.isse.ecco.adapter.rust.data;

import at.jku.isse.ecco.artifact.ArtifactData;

import java.util.Objects;

public class ItemArtifactData implements ArtifactData  {

    @Override
    public String toString() {
        return "ItemArtifactData:";
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o != null && getClass() == o.getClass();
    }

    @Override
    public int hashCode() {
        return Objects.hash("");
    }
}
