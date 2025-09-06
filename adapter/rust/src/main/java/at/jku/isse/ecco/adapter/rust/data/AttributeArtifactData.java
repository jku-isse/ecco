package at.jku.isse.ecco.adapter.rust.data;

import at.jku.isse.ecco.artifact.ArtifactData;
import lombok.Data;

import java.io.BufferedWriter;
import java.io.IOException;

@Data
public class AttributeArtifactData implements ArtifactData, RustWritable {
    private final String attribute;

    public AttributeArtifactData(String attribute) {
        this.attribute = attribute;
    }

    /**
     * @param bw
     * @throws IOException
     */
    @Override
    public void write(BufferedWriter bw) throws IOException {
        bw.write(attribute);
        bw.newLine();
    }
}
