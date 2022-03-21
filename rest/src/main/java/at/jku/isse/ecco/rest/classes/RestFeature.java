package at.jku.isse.ecco.rest.classes;

import java.util.List;

public class RestFeature {
    private final String id;
    private final String name;
    private final List<RestFeatureRevision> revision;


    public RestFeature(final String id, final String name, final List<RestFeatureRevision> revision) {
        this.id = id;
        this.name = name;
        this.revision = revision;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public List<RestFeatureRevision> getRevision() {
        return revision;
    }
}
