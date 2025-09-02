package at.jku.isse.ecco.adapter.rust.data;

import at.jku.isse.ecco.artifact.ArtifactData;
import lombok.Data;
import lombok.Getter;

@Data
public class AttributeArtifactData implements ArtifactData {
    private final String attribute;

    public AttributeArtifactData(String attribute) {
        this.attribute = attribute;
    }

}
