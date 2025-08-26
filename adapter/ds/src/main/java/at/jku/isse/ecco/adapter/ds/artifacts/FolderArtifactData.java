package at.jku.isse.ecco.adapter.ds.artifacts;

import at.jku.isse.designspace.variant.dto.FolderDto;
import at.jku.isse.ecco.artifact.ArtifactData;
import lombok.Getter;

import java.util.Objects;

@Getter
public class FolderArtifactData implements ArtifactData {

    private String name;
    public FolderArtifactData(FolderDto folderDto){
        this.name = folderDto.getName();
    }

    @Override
    public String toString() {
        return "<FolderArtifactData{ " +
                "name = "+ this.name +
                " }>";
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.name);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        FolderArtifactData other = (FolderArtifactData) obj;
        return (Objects.equals(this.name, other.name));
    }
}
