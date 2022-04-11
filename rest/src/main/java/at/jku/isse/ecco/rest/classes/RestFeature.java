package at.jku.isse.ecco.rest.classes;

import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;

import java.util.Collection;
import java.util.LinkedList;

public class RestFeature {
    private final Feature feature;

    public RestFeature(Feature feature) {
        this.feature = feature;
    }

    public String getId() {
        return feature.getId();
    }

    public String getName() {
        return feature.getName();
    }

    public Collection<RestFeatureRevision> getRevisions() {
        Collection<RestFeatureRevision> revisions = new LinkedList<>();
        for (FeatureRevision f : feature.getRevisions()) {
            revisions.add(new RestFeatureRevision(f));
        }
        return revisions;
    }
}
