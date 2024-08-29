package at.jku.isse.ecco.experiment.utils;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

public class ResourceUtils {
    public static String getResourceFolderPathAsString(String relativePath) {
        try {
            URI configURI = Objects.requireNonNull(ResourceUtils.class.getClassLoader().getResource(relativePath)).toURI();
            return Paths.get(configURI).toString();
        } catch (Exception e){
            throw new RuntimeException(e.getMessage());
        }
    }

    public static Path getResourceFolderPath(String relativePath){
        try {
            URI configURI = Objects.requireNonNull(ResourceUtils.class.getClassLoader().getResource(relativePath)).toURI();
            return Paths.get(configURI);
        } catch (Exception e){
            throw new RuntimeException(e.getMessage());
        }
    }
}
