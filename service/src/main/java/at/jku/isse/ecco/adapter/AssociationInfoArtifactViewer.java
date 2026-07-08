package at.jku.isse.ecco.adapter;


import java.util.Collection;

public interface AssociationInfoArtifactViewer extends ArtifactViewer {

    void setAssociationInfos(Collection<AssociationInfo> associationInfos);

}
