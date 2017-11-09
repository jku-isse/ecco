package at.jku.isse.ecco.plugin.emf.data;

import org.eclipse.emf.ecore.EStructuralFeature;

/**
 * Created by hhoyos on 17/07/2017.
 */
public class SinglevalueAttributeData extends SinglevalueFeatureData<EDataTypeArtifactData> {

    public SinglevalueAttributeData(EStructuralFeature feature, boolean isSet, EDataTypeArtifactData value, boolean isUnique) {
        super(feature, isSet, value, isUnique);
    }
}
