package at.jku.isse.ecco.plugin.emf.data;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EStructuralFeature;

/**
 * Created by hhoyos on 14/06/2017.
 */
public class EContainerData extends EmfArtifactData {

    private static final long serialVersionUID = -7938760274097459115L;
    private final EObjectArtifactData container;

    /**
     * Create a new ArtifactData to represent the EContainer reference of an EObject.
     * In this case, the value represents the EObject that is contained, the feature points at the Feature in the
     * multiValue's EClass that defines the containment relationship, the multiValue is the value of the Feature
     * in case it is multivalued.
     *
     * For EObjects contained at the root of the resource the {@link ResourceContainerData} should be used.
     *
     * @param value
     * @param container
     * @param feature
     * @param multiValue If the feature is multivalued, the collection that holds the value
     */
    public EContainerData(Object value, EObjectArtifactData container, EStructuralFeature feature, EList<?> multiValue) {
        super(value, feature, multiValue);
        this.container = container;
    }


    public EObjectArtifactData getContainer() {
        return container;
    }

    @Override
    public String toString() {
        return "eContainer = " + container;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        EContainerData that = (EContainerData) o;

        return container.equals(that.container);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + container.hashCode();
        return result;
    }
}
