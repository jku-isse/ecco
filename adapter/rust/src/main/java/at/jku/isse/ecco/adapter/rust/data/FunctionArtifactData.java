package at.jku.isse.ecco.adapter.rust.data;

import at.jku.isse.ecco.artifact.ArtifactData;
import lombok.Data;

import java.io.BufferedWriter;

@Data
public class FunctionArtifactData implements ArtifactData, RustWritable {

    private final String signature;

    public FunctionArtifactData(String signature) {
        this.signature = signature;
    }

    @Override
    public void write(BufferedWriter bw) throws java.io.IOException {
        bw.write(this.signature);
    }
}
