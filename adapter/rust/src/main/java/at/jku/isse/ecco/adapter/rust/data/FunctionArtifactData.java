package at.jku.isse.ecco.adapter.rust.data;

import at.jku.isse.ecco.artifact.ArtifactData;
import lombok.Data;

@Data
public class FunctionArtifactData implements ArtifactData {

    private final String signature;

    public FunctionArtifactData(String signature) {
        this.signature = signature;
    }
}
