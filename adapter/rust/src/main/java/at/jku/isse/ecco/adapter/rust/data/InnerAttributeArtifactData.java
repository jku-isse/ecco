package at.jku.isse.ecco.adapter.rust.data;

import at.jku.isse.ecco.artifact.ArtifactData;
import lombok.Data;

import java.io.BufferedWriter;

@Data
public class InnerAttributeArtifactData implements ArtifactData, RustWritable {

    private final String attribute;

    @Override
    public void write(BufferedWriter bw) throws java.io.IOException {
        bw.write(this.attribute);
        bw.newLine();
    }
}
