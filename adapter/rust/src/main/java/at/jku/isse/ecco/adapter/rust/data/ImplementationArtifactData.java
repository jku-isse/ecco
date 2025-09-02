package at.jku.isse.ecco.adapter.rust.data;

import at.jku.isse.ecco.artifact.ArtifactData;
import lombok.Getter;

@Getter
public class ImplementationArtifactData implements ArtifactData {
    private final String signature;

    public ImplementationArtifactData(String signature) {
        this.signature = signature;
    }

}
