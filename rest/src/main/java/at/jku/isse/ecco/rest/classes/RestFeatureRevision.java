package at.jku.isse.ecco.rest.classes;

public class RestFeatureRevision {
    private final String id;
    private final String description;
    private final String featureRevisionString;


    public RestFeatureRevision(final String id, final String description, final String featureRevisionString) {
        this.id = id;
        this.description = description;
        this.featureRevisionString = featureRevisionString;
    }

    public String getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public String getFeatureRevisionString() {
        return featureRevisionString;
    }
}
