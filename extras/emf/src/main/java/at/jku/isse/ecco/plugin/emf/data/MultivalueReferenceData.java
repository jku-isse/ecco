package at.jku.isse.ecco.plugin.emf.data;

import org.eclipse.emf.ecore.EStructuralFeature;

import java.util.List;

/**
 * Created by hhoyos on 17/07/2017.
 */
public class MultivalueReferenceData extends MultivalueFeatureData<EObjectArtifactData> {


    public MultivalueReferenceData(EStructuralFeature feature, boolean isSet, List<EObjectArtifactData> contents,
                                   boolean isUnique) {
        super(feature, isSet, contents, isUnique);
    }
}
