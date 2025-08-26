package at.jku.isse.ecco.adapter.ds.artifacts;

import at.jku.isse.designspace.core.model.Cardinality;
import at.jku.isse.designspace.variant.dto.PropertyDto;
import at.jku.isse.ecco.artifact.ArtifactData;
import lombok.Getter;

import java.util.Objects;

@Getter
public class PropertyArtifactData implements ArtifactData {

    private final Cardinality cardinality;
    private final String name;
    
    public PropertyArtifactData(PropertyDto propertyDto){
        this.name = propertyDto.getName();
        this.cardinality = propertyDto.getCardinality();
    }

    @Override
    public String toString() {
        return "<Property{ " +
                "name = " + this.name +
                "cardinality = " + this.cardinality +
                "}>";
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.name, this.cardinality);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        PropertyArtifactData other = (PropertyArtifactData) obj;
        return (Objects.equals(this.name, other.name) && Objects.equals(this.cardinality, other.cardinality));
    }
    
}
