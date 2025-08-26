package at.jku.isse.ecco.adapter.ds.artifacts;

import at.jku.isse.designspace.variant.dto.InstanceDto;
import at.jku.isse.ecco.artifact.ArtifactData;
import lombok.Getter;

import java.util.Objects;

@Getter
public class InstanceArtifactData implements ArtifactData {

    private final String name;
    private final Long id;

    public InstanceArtifactData(InstanceDto instanceDto) {
        this.name = instanceDto.getName();
        this.id = instanceDto.getId();
    }

    @Override
    public String toString() {
        return "<InstanceArtifaceData{ " +
                "name = " + this.name +
                ", íd = " + this.id + "}>";
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.name, this.id);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        InstanceArtifactData other = (InstanceArtifactData) obj;
        return (Objects.equals(this.name, other.name) && Objects.equals(this.id, other.id));
    }

}
