package at.jku.isse.ecco.adapter.python.data.json.value;

import at.jku.isse.ecco.artifact.ArtifactData;

import java.util.Objects;

public abstract class JsonValueArtifactData<T> implements ArtifactData {

    private T value;
    public JsonValueArtifactData(T value) {
        this.value = value;
    }

    public void setValue(T value) {
        this.value = value;
    }

    public T getValue() {
        return this.value;
    }

    @Override
    public String toString() {
        return String.valueOf(this.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JsonValueArtifactData<?> that = (JsonValueArtifactData<?>) o;
        return Objects.equals(value, that.value);
    }
}