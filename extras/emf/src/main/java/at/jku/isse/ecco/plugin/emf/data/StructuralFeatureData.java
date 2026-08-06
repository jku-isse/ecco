package at.jku.isse.ecco.plugin.emf.data;

import at.jku.isse.ecco.artifact.ArtifactData;
import org.eclipse.emf.ecore.EStructuralFeature;

/**
 * Created by hhoyos on 17/07/2017.
 */
public abstract class StructuralFeatureData implements ArtifactData {

    private final Integer featureId;
    private final boolean isSet;
    private final String featureName;
    private final boolean isUnique;

    public StructuralFeatureData(EStructuralFeature feature, boolean isSet, boolean isUnique) {
        featureId = feature.getFeatureID();
        this.isSet = isSet;
        this.featureName = feature.getName();
        this.isUnique = isUnique;
    }

    public Integer getFeatureId() {
        return featureId;
    }

    public boolean isSet() {
        return isSet;
    }

    public String getFeatureName() {
        return featureName;
    }

    public boolean isUnique() {
        return isUnique;
    }

    @Override
    public String toString() {
        String set;
        if (isSet) {
            set = " \u2714";
        }
        else {
            set = " \u2718";
        }
        return featureName + set;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StructuralFeatureData)) return false;

        StructuralFeatureData that = (StructuralFeatureData) o;

        if (isSet != that.isSet) return false;
        return featureId.equals(that.featureId);
    }

    @Override
    public int hashCode() {
        int result = featureId.hashCode();
        result = 31 * result + (isSet ? 1 : 0);
        return result;
    }
}
