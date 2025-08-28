package at.jku.isse.ecco.adapter.rust.data;

import at.jku.isse.ecco.artifact.ArtifactData;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.Objects;

@ToString
@EqualsAndHashCode
@Getter
public class LineArtifactData implements ArtifactData {

    private final String line;

    public LineArtifactData(String line) {
        this.line = line;
    }


}
