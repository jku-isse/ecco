package at.jku.isse.ecco.plugin.emf.data;

import org.eclipse.emf.ecore.EStructuralFeature;

/**
 * Created by hhoyos on 17/07/2017.
 */
public class SinglevalueReferenceData extends SinglevalueFeatureData<EObjectArtifactData> {

    public SinglevalueReferenceData(EStructuralFeature feature, boolean isSet, EObjectArtifactData value, boolean isUnique) {
        super(feature, isSet, value, isUnique);
    }
}
