package at.jku.isse.ecco.adapter.ds.artifacts;

import at.jku.isse.designspace.variant.dto.ReferenceValueDto;
import at.jku.isse.ecco.artifact.ArtifactData;
import lombok.Getter;

import java.util.Objects;

@Getter
public class ReferenceValueArtifactData implements ValueArtifactData {
    private final Long referencedId;

    public ReferenceValueArtifactData(ReferenceValueDto referenceValueDto) {
        this.referencedId = referenceValueDto.getValue();
    }

    @Override
    public String toString() {
        return "<ReferenceValueArtifactData{ " +
                "referencedID = " + this.referencedId +
                "}>";
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.referencedId);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ReferenceValueArtifactData other = (ReferenceValueArtifactData) obj;
        return Objects.equals(this.referencedId, other.referencedId);
    }
}
