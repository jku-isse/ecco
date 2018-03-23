package at.jku.isse.ecco.storage.json.impl.entities;

import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.storage.mem.feature.MemFeatureRevision;

import static java.util.Objects.requireNonNull;

public class JsonFeatureRevision implements FeatureRevision {

    private Feature feature;
    private String id;
    private String description;

    public JsonFeatureRevision(Feature feature, String id) {
        requireNonNull(feature);
        requireNonNull(id);
        this.feature = feature;
        this.id = id;
    }


    @Override
    public Feature getFeature() {
        return this.feature;
    }

    @Override
    public String getId() {
        return this.id;
    }

    @Override
    public String getDescription() {
        return this.description;
    }

    @Override
    public void setDescription(String description) {
        this.description = description;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MemFeatureRevision)) return false;

        MemFeatureRevision that = (MemFeatureRevision) o;

        if (!getFeature().equals(that.getFeature())) return false;
        return getId().equals(that.getId());

    }

    @Override
    public int hashCode() {
        int result = getFeature().hashCode();
        result = 31 * result + getId().hashCode();
        return result;
    }

    @Override
    public String toString() {
        return this.getFeatureRevisionString();
    }
}
