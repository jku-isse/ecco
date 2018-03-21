package at.jku.isse.ecco.storage.json.impl.entities;

import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.storage.mem.artifact.MemArtifact;

public class JsonArtifact<T extends ArtifactData> extends MemArtifact<T> {
    public JsonArtifact() {

    }

    public JsonArtifact(T data) {
        super(data);
    }

    public JsonArtifact(T data, boolean ordered) {
        super(data, ordered);
    }
}
