package at.jku.isse.ecco.adapter.rust.data;

import at.jku.isse.ecco.artifact.ArtifactData;
import lombok.Data;
import java.util.Objects;

@Data
public class ConstantArtifactData implements ArtifactData {
    @Override
    public boolean equals(Object o) {
        return this == o || o != null && getClass() == o.getClass();
    }

    @Override
    public int hashCode() {
        return Objects.hash("");
    }

    @Override
    public String toString() {
        return "ConstantArtifactData";
    }

}
