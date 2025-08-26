package at.jku.isse.ecco.adapter.ds.artifacts;

import at.jku.isse.designspace.variant.dto.InstanceTypeDto;
import at.jku.isse.ecco.artifact.ArtifactData;

import java.util.Objects;
import lombok.Getter;

@Getter
public class InstanceTypeArtifactData implements ArtifactData {

    private final String name;
    private final Long id;

    public InstanceTypeArtifactData(InstanceTypeDto instanceTypeDto) {
        this.name = instanceTypeDto.getName();
        this.id = instanceTypeDto.getId();
    }

    @Override
    public String toString() {
        return "<InstanceTypeArtifactData{ " +
                "name = " + this.name +
                ", id = " + this.id +
                "}>";
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
        InstanceTypeArtifactData other = (InstanceTypeArtifactData) obj;
        return (Objects.equals(this.name, other.name) && Objects.equals(this.id, other.id));
    }
}
