package at.jku.isse.ecco.adapter.rust.data;

import at.jku.isse.ecco.artifact.ArtifactData;
import lombok.*;

@Data
public class TraitArtifactData implements ArtifactData {
    private final String trait;
}
