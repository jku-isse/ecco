package at.jku.isse.ecco.plugin.artifact.emf;

import at.jku.isse.ecco.artifact.ArtifactData;
import org.eclipse.emf.ecore.EObject;

/**
 * Created by hhoyos on 12/05/2017.
 */
public class EObjectArtifactData implements ArtifactData {

    private final EObject eObject;

    public EObjectArtifactData(EObject eObject) {
        this.eObject = eObject;
    }

    public EObject getEObject() {
        return eObject;
    }
}
