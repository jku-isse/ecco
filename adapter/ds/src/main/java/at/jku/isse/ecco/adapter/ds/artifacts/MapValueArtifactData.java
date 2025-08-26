package at.jku.isse.ecco.adapter.ds.artifacts;

import lombok.Getter;
import java.util.Objects;

/**
 * Wrapper Class to add key for MAP-Properties.
 */
@Getter
public class MapValueArtifactData implements ValueArtifactData {

    private final String key;
    private final ValueArtifactData valueArtifactData;

    public MapValueArtifactData(String key, ValueArtifactData valueArtifactData) {
        this.key = key;
        this.valueArtifactData = valueArtifactData;
    }

    @Override
    public String toString() {
        return "<MapValueArtifactData{ " +
                "key = " + this.key +
                ", value = " + this.valueArtifactData.toString() +
                "}>";
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.key, this.valueArtifactData.hashCode());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        MapValueArtifactData other = (MapValueArtifactData) obj;
        return (Objects.equals(this.key, other.key) && Objects.equals(this.valueArtifactData, other.valueArtifactData));
    }
}
