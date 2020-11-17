package at.jku.isse.ecco.plugin.emf.data;

import at.jku.isse.ecco.artifact.ArtifactData;
import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Save relevant information about the resource, mainly the metamodel
 * Created by hhoyos on 22/05/2017.
 */
public class EmfResourceData implements ArtifactData {

    private static final long serialVersionUID = -1820846448223404470L;
    /**
     * Keep track of all packages used by the resource.
     */
    private final Map<String, EPackageLocation> usedPacakges;

    /** The extension used to persist the model. It will be used in the future to
     * reconstruct the resource from the Ecco tree.
     */
    private final String extension;

    /**
     * The Emf {@link Resource.Factory} that has to be used to persist the resource.
     */
    private final String factoryClass;

    public EmfResourceData(String extension, String factoryClass) {
        this.extension = extension;
        this.factoryClass = factoryClass;
        usedPacakges = new HashMap<>();
    }

    public void addEPackageInformation(EPackage ePackage, Resource ePackageResource) {
        String uri = ePackage.getNsURI();
        if(!usedPacakges.containsKey(uri)) {
            EPackageLocation loc;
            if (ePackageResource != null) {
                // FIXME can we get the actual file?
                loc = new EPackageLocation(true, uri, ePackageResource.getURI().toString());
            }
            else {
                loc = new EPackageLocation(false, uri, null);
            }
            usedPacakges.put(uri, loc);
        }
    }

    public Map<String, EPackageLocation> getUsedPacakges() {
        return usedPacakges;
    }

    public String getExtension() {
        return extension;
    }

    public String getFactoryClass() {
        return factoryClass;
    }

    public String toString() {
        return String.format("Emf Resource, extension: %s.", this.extension);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        EmfResourceData that = (EmfResourceData) o;

        return usedPacakges.equals(that.usedPacakges);
    }

    @Override
    public int hashCode() {
        return usedPacakges.hashCode();
    }



    public class EPackageLocation implements Serializable {

        /** If the metamodel can be loadad from a local file */
        @NotNull
        private final boolean isLocal;

        /** The ns uri of the EPacakge to find it in the resource set */
        @NotNull
        private final String nsuri;

        /** If the EPacakge is not loaded, we have to loaded from this location */
        @Nullable
        private final String locationuri;
        

        public EPackageLocation(boolean isLocal, String nsuri, String locationuri) {
            this.isLocal = isLocal;
            this.nsuri = nsuri;
            this.locationuri = locationuri;
        }

        public boolean isLocal() {
            return isLocal;
        }

        public String getNsuri() {
            return nsuri;
        }

        public String getLocationuri() {
            return locationuri;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            EPackageLocation that = (EPackageLocation) o;

            if (isLocal != that.isLocal) return false;
            if (!nsuri.equals(that.nsuri)) return false;
            return locationuri != null ? locationuri.equals(that.locationuri) : that.locationuri == null;
        }

        @Override
        public int hashCode() {
            int result = (isLocal ? 1 : 0);
            result = 31 * result + nsuri.hashCode();
            result = 31 * result + (locationuri != null ? locationuri.hashCode() : 0);
            return result;
        }
    }
}
