package at.jku.isse.ecco.storage.json.impl.entities;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.storage.mem.dao.MemEntityFactory;

public class JsonPluginEntityFactory extends MemEntityFactory {
    @Override
    public <T extends ArtifactData> Artifact.Op<T> createArtifact(T data) {
        return new JsonArtifact<>(data);
    }
}
