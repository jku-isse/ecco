package at.jku.isse.ecco.plugin.emf.data;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.artifact.ArtifactData;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;

/**
 * @author Horacio Hoyos
 */
public class EObjectArtifactData implements ArtifactData {

    private static final long serialVersionUID = -8872015795576655567L;

    /**
     * The package that contains the EClass that defines the EObject.
     */
    private final String ePackageUri;

    /**
     * The name of the EClass that defines the EObject
     */
    private final String eClassName;

    /**
     * The value of the feature that is defined in the EClass to be used as id.
     */
    private final Object id;

    public EObjectArtifactData(EObject value) {
        EClass eClass = value.eClass();
        this.eClassName = eClass.getName();
        this.ePackageUri = eClass.getEPackage().getNsURI();
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

    public String getePackageUri() {
        return ePackageUri;
    }

    @Override
    public String toString() {
        return this.id + ":" + this.eClassName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EObjectArtifactData that = (EObjectArtifactData) o;
        if (!ePackageUri.equals(that.ePackageUri)) return false;
        if (!eClassName.equals(that.eClassName)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        int result = 17;
        result = 31 * result + ePackageUri.hashCode();
        result = 31 * result + eClassName.hashCode();
        result = 31 * result + id.hashCode();
        return result;
    }
}
