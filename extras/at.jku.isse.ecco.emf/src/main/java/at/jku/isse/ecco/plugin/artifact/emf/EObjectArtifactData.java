package at.jku.isse.ecco.plugin.artifact.emf;

import at.jku.isse.ecco.EccoException;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;

/**
 * @author Horacio Hoyos
 */
public class EObjectArtifactData extends EmfArtifactData {

    // The name of the EObject EClass so we can reconstruct it.
    // Al features can be reconstructed from the nodes
    private final String eClassName;

    // We assume the meteamodel has one feature which is the id
    private final Object id;

    public EObjectArtifactData(EObject value, EStructuralFeature feature, EList<EObject> container) {
        super(value, feature, container);
        EClass eClass = value.eClass();
        this.eClassName = eClass.getName();
        EAttribute idAttr = eClass.getEIDAttribute();
        try {
            id = value.eGet(idAttr);
        } catch (NullPointerException ex) {
            throw new EccoException("ECCO requires all EClasses to define a property to be the ID.");
        }
    }

    public String geteClassName() {
        return eClassName;
    }

    public Object getId() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (! super.equals(o)) return false;

        EObjectArtifactData that = (EObjectArtifactData) o;
        if (!eClassName.equals(that.eClassName)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + eClassName.hashCode();
        result = 31 * result + id.hashCode();
        return result;
    }
}
