package at.jku.isse.ecco.experiment.utils.vevos;

import org.apache.commons.collections4.CollectionUtils;
import at.jku.isse.ecco.experiment.utils.DirUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class VevosUtils {

    public static Path getVariantPath(Path variantsBasePath, String configString) {
        // a config-element may be a feature or a feature-revision
        Collection<String> configElements = configStringToElements(configString);
        List<Path> variantPaths = getVariantFolders(variantsBasePath);
        for (Path variantPath : variantPaths) {
            Collection<String> variantConfigElements = getConfigElements(variantPath);
            if (CollectionUtils.isEqualCollection(configElements, variantConfigElements)) {
                return variantPath;
            }
        }
        throw new RuntimeException("No matching variant found...");
    }

    public static String variantPathToConfigString(Path variantPath){
            Collection<String> variantConfigElements = getConfigElements(variantPath);
            return String.join(", ", variantConfigElements);
    }

    public static List<Path> getVariantFolders(Path variantsBasePath){
        try (Stream<Path> stream = Files.list(variantsBasePath)) {
            return stream.filter(Files::isDirectory)
                    .filter(folder -> !folder.getFileName().toString().equals("configs"))
                    .collect(Collectors.toList());
        } catch (IOException e){
            throw new RuntimeException(e.getMessage());
        }
    }

    private static Collection<String> getConfigElements(Path variantPath){
        try {
            Path configPath = variantPath.resolve(".config").toAbsolutePath();
            String variantConfig = Files.readAllLines(configPath).get(0);
            return configStringToElements(variantConfig);
        } catch (IOException e){
            throw new RuntimeException("Exception while reading configuration: " + e.getMessage());
        }
    }

    private static Collection<String> configStringToElements(String configString){
        String[] configElements = configString.split(",");
        for (int i = 0; i < configElements.length; i++){ configElements[i] = configElements[i].trim(); }
        return Arrays.stream(configElements).sorted().collect(Collectors.toList());
    }

    public static List<Path> extendSamplePathsByCommitFolder(List<Path> samplePaths){
        List<Path> extendedPaths = new LinkedList<>();
        for (Path samplePath : samplePaths){
            List<Path> subPaths = DirUtils.getSubDirectoryPaths(samplePath);
            if (subPaths.size() != 1){
                throw new RuntimeException("More than one commit-has subfolder for variant: " + samplePath);
            }
            extendedPaths.add(samplePath.resolve(subPaths.iterator().next()));
        }
        return extendedPaths;
    }
}
