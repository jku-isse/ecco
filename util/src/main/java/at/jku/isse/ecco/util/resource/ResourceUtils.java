package at.jku.isse.ecco.util.resource;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

public class ResourceUtils {

    /**
     * Get the absolute path to a resource as String.
     * @param relativePath relative path from resource folder to resource that must not be Null.
     * @return absolute path to resource as String.
     * @throws ResourceException
     */
    public static String getResourceFolderPathAsString(String relativePath) throws ResourceException {
        try {
            URI configURI = Objects.requireNonNull(ResourceUtils.class.getClassLoader().getResource(relativePath)).toURI();
            return Paths.get(configURI).toString();
        } catch (URISyntaxException | NullPointerException e){
            throw new ResourceException(e);
        }
    }

    /**
     * Get the absolute path to a resource.
     * @param relativePath relative path from resource folder to resource that must not be Null.
     * @return absolute path to resource.
     * @throws ResourceException
     */
    public static Path getResourceFolderPath(String relativePath) throws ResourceException {
        assert(relativePath != null);
        try {
            URI configURI = Objects.requireNonNull(ResourceUtils.class.getClassLoader().getResource(relativePath)).toURI();
            return Paths.get(configURI);
        } catch (NullPointerException e){
            throw new ResourceException(String.format("Resource could not be found: %s", relativePath));
        } catch (URISyntaxException e){
            throw new ResourceException(e);
        }
    }
}
