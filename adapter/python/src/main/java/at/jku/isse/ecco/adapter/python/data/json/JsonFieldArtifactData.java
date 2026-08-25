package at.jku.isse.ecco.adapter.python.data.json;

import at.jku.isse.ecco.artifact.ArtifactData;

import java.util.Objects;

public class JsonFieldArtifactData implements ArtifactData {

    private String fieldName;

    public JsonFieldArtifactData(String fieldName) {
        this.fieldName = fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getFieldName() {
        return this.fieldName;
    }

    @Override
    public String toString() {
        return this.fieldName;
    }

    @Override
    public int hashCode() {
        return Objects.hash(fieldName);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JsonFieldArtifactData that = (JsonFieldArtifactData) o;
        return Objects.equals(fieldName, that.fieldName);
    }
}