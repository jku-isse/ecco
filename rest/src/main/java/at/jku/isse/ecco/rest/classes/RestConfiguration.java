package at.jku.isse.ecco.rest.classes;

import at.jku.isse.ecco.core.Variant;
import at.jku.isse.ecco.feature.Configuration;
import at.jku.isse.ecco.feature.FeatureRevision;

import java.util.Collection;
import java.util.LinkedList;

public class RestConfiguration {

    private final Configuration configuration;

    public RestConfiguration(Configuration configuration) {
        this.configuration = configuration;
    }

    public Collection<RestFeatureRevision> getFeatureRevisions() {
        Collection<RestFeatureRevision> revisions = new LinkedList<>();
        for (FeatureRevision f : configuration.getFeatureRevisions()) {
            revisions.add(new RestFeatureRevision(f));
        }
        return revisions;
    }

    public String getConfigurationString(){return configuration.getConfigurationString(); }
}
