package at.jku.isse.ecco.adapter.rust.data;

import at.jku.isse.ecco.artifact.ArtifactData;
import lombok.Data;

@Data
public class VisibilityArtifactData implements ArtifactData {
    final String visibility = "pub";
}
