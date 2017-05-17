package at.jku.isse.ecco.plugin.artifact.emf;

import at.jku.isse.ecco.EccoException;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EDataType;
import org.eclipse.emf.ecore.EFactory;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;

/**
 * Created by hhoyos on 15/05/2017.
 */
public class DataTypeArtifactData extends EmfArtifactData {

    // The name can be used to retrieve the EClassifier from the Package
    private final String dataTypeName;

    // In EMF all DataTypes must be serializable and de-serializable from a string representation
    private final String value;

    public DataTypeArtifactData(Object value, EStructuralFeature feature, EList container) {
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
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        DataTypeArtifactData that = (DataTypeArtifactData) o;

        if (!dataTypeName.equals(that.dataTypeName)) return false;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + dataTypeName.hashCode();
        result = 31 * result + value.hashCode();
        return result;
    }
}
