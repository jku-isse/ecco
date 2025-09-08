package at.jku.isse.ecco.adapter.rust.data;

import at.jku.isse.ecco.artifact.ArtifactData;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.io.BufferedWriter;
import java.io.IOException;

@ToString
@EqualsAndHashCode
@Getter
public class LineArtifactData implements ArtifactData, RustWritable {

    private final String line;

    public LineArtifactData(String line) {
        this.line = line;
    }

    public void write(BufferedWriter bw) throws IOException {
        bw.write(this.line);
        bw.newLine();
    }


}
