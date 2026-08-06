package at.jku.isse.ecco.plugin.emf.data;

import org.eclipse.emf.ecore.EStructuralFeature;

import java.util.List;

/**
 * Created by hhoyos on 17/07/2017.
 */
public class MultivalueAttributteData extends MultivalueFeatureData<EDataTypeArtifactData> {


    public MultivalueAttributteData(EStructuralFeature feature, boolean isSet, List<EDataTypeArtifactData> contents,
                                    boolean isUnique) {
        super(feature, isSet, contents, isUnique);
    }
}
