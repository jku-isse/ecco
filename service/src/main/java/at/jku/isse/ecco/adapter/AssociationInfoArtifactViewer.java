package at.jku.isse.ecco.adapter;


import java.util.Collection;

public interface AssociationInfoArtifactViewer extends ArtifactViewer {

    void setAssociationInfos(Collection<AssociationInfo> associationInfos);

    /**
     * Updates the viewer's display to reflect which associations are currently selected (e.g. checked in an
     * associations table), for example by highlighting the artifacts belonging to those associations.
     */
    void markSelectedAssociations();

}
