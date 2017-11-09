package at.jku.isse.ecco.plugin.emf.data;

import at.jku.isse.ecco.artifact.ArtifactData;

/**
 * An  ArtifactData to symbolize containment at the ResourceLevel. An EObject can also be contained by different
 * Resources.
 *
 * Created by hhoyos on 14/06/2017.
 */
public class ResourceContainerData implements ArtifactData {

    private static final long serialVersionUID = 2342740191321188886L;
    /**
     * The Resource that contains the EObject
     */
    private final EmfResourceData resource;

    public ResourceContainerData(EmfResourceData resource) {
        this.resource = resource;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ResourceContainerData that = (ResourceContainerData) o;

        return resource.equals(that.resource);
    }

    @Override
    public int hashCode() {
        return resource.hashCode();
    }
}
