package at.jku.isse.ecco.adapter.python.data.json;

import at.jku.isse.ecco.artifact.ArtifactData;

import java.util.Objects;

public class JsonObjectArtifactData implements ArtifactData {

    @Override
    public String toString() {
        return "JsonObjectArtifactData";
    }

    @Override
    public int hashCode() {
        return Objects.hash("JsonObjectArtifactData");
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        return getClass() == obj.getClass();
    }
}