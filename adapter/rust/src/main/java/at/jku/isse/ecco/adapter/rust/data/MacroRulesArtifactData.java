package at.jku.isse.ecco.adapter.rust.data;

import at.jku.isse.ecco.artifact.ArtifactData;
import lombok.Data;

@Data
public class MacroRulesArtifactData implements ArtifactData {
    private final String identifier;
}
