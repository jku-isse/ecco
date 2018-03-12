package at.jku.isse.ecco.plugin.emf.data;

import at.jku.isse.ecco.artifact.ArtifactData;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EStructuralFeature;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

import static com.google.common.base.Preconditions.checkNotNull;


/**
 * Created by hhoyos on 12/05/2017.
 */
public abstract class EmfArtifactData implements ArtifactData {

    /** The feature that relates this element to the parent node.
     * It can be null for nodes in the root.
     * Since we need to serialize the data, we save the id, not the feature
     */
    private final Integer featureId;

    /**
     * For multivalue-ordered features, it determines the position in the collection in which
     * the feature value is placed in the owner.
     *
     * For multivalue-nonunique-ordered, the feature value can be found in many positions.
     */
    private final Integer[] position;

    /**
     * For multivalue-nonunique-unordered features, there is a choice of how many times an element is referenced.
     */
    private final int repetitions;

    /**
     * @param value
     * @param feature
     * @param multiValue If the feature is multivalued, the collection that holds the value
     */
    public EmfArtifactData(Object value, EStructuralFeature feature, EList<? extends Object> multiValue) {
        checkNotNull(value);
        if (feature != null) {
            this.featureId = feature.getFeatureID();
        }
        else {
            this.featureId = null;
        }
        if ((feature != null) && feature.isMany()) {
            if (feature.isUnique() && !feature.isOrdered()) {           // Set
                this.position = new Integer[] {-1};                     // HOw to say that position is irrelevant? -1?
                this.repetitions = 1;
            }
            else if (feature.isUnique() && feature.isOrdered()) {       // OrderedSet
                int index = multiValue.indexOf(value);
                this.position = new Integer[]{index};                   // HOw to say that position is irrelevant? -1?
                this.repetitions = 1;
            }
            else if (!feature.isUnique() && feature.isOrdered()) {      // List
                Iterator<? extends Object> it = multiValue.iterator();
                ArrayList<Integer> positions = new ArrayList<Integer>();
                int index = 0;
                while (it.hasNext()) {
                    if (it.next().equals(value)) {
                        positions.add(index);
                    }
                    index++;
                }
                this.position = positions.toArray(new Integer[]{});                   // HOw to say that position is irrelevant? -1?
                this.repetitions = positions.size();
            }
            else if (!feature.isUnique() && !feature.isOrdered()) {     // Bag
                Iterator<? extends Object> it = multiValue.iterator();
                int reps = 0;
                while (it.hasNext()) {
                    if (it.next().equals(value)) {
                        reps++;
                    }
                }
                this.position = new Integer[] {-1};
                this.repetitions = reps;
            }
            else {
                this.position = new Integer[] {-1};
                this.repetitions = 1;
            }
        }
        else {
            this.position = new Integer[] {-1};
            this.repetitions = 1;
        }
    }

    public int getFeatureId() {
        return featureId;
    }

    public int getRepetitions() {
        return repetitions;
    }

    public Integer[] getPosition() {
        return position;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EmfArtifactData)) return false;

        EmfArtifactData that = (EmfArtifactData) o;

        if (repetitions != that.repetitions) return false;
        if (featureId != null ? !featureId.equals(that.featureId) : that.featureId != null) return false;
        // Probably incorrect - comparing Object[] arrays with Arrays.equals
        return Arrays.equals(position, that.position);
    }

    @Override
    public int hashCode() {
        int result = featureId != null ? featureId.hashCode() : 0;
        result = 31 * result + Arrays.hashCode(position);
        result = 31 * result + repetitions;
        return result;
    }
}
