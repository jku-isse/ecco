package at.jku.isse.ecco.rest.models;

import at.jku.isse.ecco.feature.FeatureRevision;

public class RestFeatureRevision {
    private final FeatureRevision featureRevision;

    public RestFeatureRevision(FeatureRevision featureRevision) {
        this.featureRevision = featureRevision;
    }

    public String getId() {
        return featureRevision.getId();
    }

    public String getDescription() {
        return featureRevision.getDescription();
    }

    public String getFeatureRevisionString() {
        return featureRevision.getFeatureRevisionString();
    }

    public String getFeatureName() { return featureRevision.getFeature().getName(); }

    public String getFeatureRevisionIndex() { return featureRevision.getFeatureRevisionString().split("\\.")[1]; }
}
