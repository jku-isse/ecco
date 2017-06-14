package at.jku.isse.ecco.plugin.emf.data;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EStructuralFeature;

/**
 * Created by hhoyos on 17/05/2017.
 */
public class NonContainmentReferenceData extends EmfArtifactData {

    private static final long serialVersionUID = 9136613555386474489L;

    private final EObjectArtifactData reference;

    public NonContainmentReferenceData(Object value, EStructuralFeature feature,
                                       EList<?> container, EObjectArtifactData targetData) {
        super(value, feature, container);
        this.reference = targetData;
    }

    public EObjectArtifactData getReference() {
        return reference;
    }

    @Override
    public String toString() {
        return "referenceTo " + reference.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        NonContainmentReferenceData that = (NonContainmentReferenceData) o;

        return reference.equals(that.reference);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + reference.hashCode();
        return result;
    }
}
