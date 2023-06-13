package at.jku.isse.ecco.cli.command.features;

import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;

import java.util.Collection;

public class TestFeature implements Feature {
    private final String name;

    public TestFeature(String name) {
        this.name = name;
    }

    @Override
    public Collection<? extends FeatureRevision> getRevisions() {
        return null;
    }

    @Override
    public FeatureRevision addRevision(String id) {
        return null;
    }

    @Override
    public FeatureRevision getRevision(String id) {
        return null;
    }

    @Override
    public FeatureRevision getOrphanedRevision(String id) {
        return null;
    }

    @Override
    public FeatureRevision getLatestRevision() {
        return null;
    }

    @Override
    public Feature feature(String name) {
        return null;
    }

    @Override
    public String getId() {
        return null;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public void setName(String name) {

    }

    @Override
    public String getDescription() {
        return null;
    }

    @Override
    public void setDescription(String description) {

    }

    @Override
    public String toString() {
        return this.name;
    }
}
