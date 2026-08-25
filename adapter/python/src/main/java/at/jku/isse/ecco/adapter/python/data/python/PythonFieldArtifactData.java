package at.jku.isse.ecco.adapter.python.data.python;

import at.jku.isse.ecco.artifact.ArtifactData;

import java.util.Objects;

public class PythonFieldArtifactData implements ArtifactData {

    private String parentFieldName;
    private String fieldName;

    public PythonFieldArtifactData(String fieldName) {
        this.fieldName = fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getFieldName() {
        return this.fieldName;
    }

    public void setParentFieldName(String parentFieldName) {
        this.parentFieldName = parentFieldName;
    }

    public String getParentFieldName() {
        return this.parentFieldName;
    }

    @Override
    public String toString() {
        return this.fieldName;
    }

    @Override
    public int hashCode() {
        return Objects.hash(parentFieldName, fieldName);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PythonFieldArtifactData that = (PythonFieldArtifactData) o;
        return Objects.equals(parentFieldName, that.parentFieldName) && Objects.equals(fieldName, that.fieldName);
    }
}