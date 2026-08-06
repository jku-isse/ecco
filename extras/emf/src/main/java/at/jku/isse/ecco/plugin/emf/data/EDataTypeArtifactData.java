package at.jku.isse.ecco.plugin.emf.data;

import at.jku.isse.ecco.EccoException;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EDataType;
import org.eclipse.emf.ecore.EFactory;
import org.eclipse.emf.ecore.EStructuralFeature;

/**
 * Created by hhoyos on 15/05/2017.
 */
public class EDataTypeArtifactData extends EmfArtifactData {

    private static final long serialVersionUID = -1192257386498985489L;
    /**
     * We need to know the package that owns the datatype.
     */
    private final String ePackageUri;

    // The name can be used to retrieve the EClassifier from the Package
    private final String dataTypeName;

    // In EMF all DataTypes must be serializable and de-serializable from a string representation
    private final String value;

    public EDataTypeArtifactData(Object value, EStructuralFeature feature, EList container) {
        super(value, feature, container);

        EDataType dataType = (EDataType) feature.getEType();
        if (!dataType.isSerializable()) {
            // Exception
            throw new EccoException("ECCO requires all DataTypes to be serializable. DataType: " + dataType.getName() + " is not.");
        }
        else {
            // This will correctly delegate to the Conversion Delegate, if one is registered
            EFactory eFactory = dataType.getEPackage().getEFactoryInstance();
            this.value = eFactory.convertToString(dataType, value);
            this.dataTypeName = dataType.getName();
            this.ePackageUri = dataType.getEPackage().getNsURI();
        }
    }

    public String getDataTypeName() {
        return dataTypeName;
    }

    public String getValue() {
        return value;
    }

    public String getePackageUri() {
        return ePackageUri;
    }

    @Override
    public String toString() {
        return this.dataTypeName + " = " + this.value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        EDataTypeArtifactData that = (EDataTypeArtifactData) o;

        if (!ePackageUri.equals(that.ePackageUri)) return false;
        if (!dataTypeName.equals(that.dataTypeName)) return false;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + ePackageUri.hashCode();
        result = 31 * result + dataTypeName.hashCode();
        result = 31 * result + value.hashCode();
        return result;
    }
}
