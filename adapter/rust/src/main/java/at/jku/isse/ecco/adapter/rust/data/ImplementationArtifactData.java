package at.jku.isse.ecco.adapter.rust.data;

import at.jku.isse.ecco.artifact.ArtifactData;
import lombok.Data;
import lombok.Getter;

import java.io.BufferedWriter;

@Data
public class ImplementationArtifactData implements ArtifactData, RustWritable {
    private final String signature;

    public void write(BufferedWriter bw) throws java.io.IOException {
        bw.write(this.signature);
        bw.newLine();
    }

}
