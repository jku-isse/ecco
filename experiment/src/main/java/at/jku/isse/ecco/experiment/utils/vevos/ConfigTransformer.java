package at.jku.isse.ecco.experiment.utils.vevos;

import org.apache.commons.io.FilenameUtils;
import at.jku.isse.ecco.experiment.utils.DirUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Transform VEVOS configuration files to ECCO configuration files and put them in respective variant folders.
 */
public class ConfigTransformer {

    public static void main(String[] args) {
        //final Path VARIANTS_BASE_PATH = Paths.get("C:\\Users\\Bernhard\\Work\\Projects\\ArgoUML_Challenge\\ScenarioAllVariants");
        //final Path sampleBasePath = Paths.get("C:\\Users\\Berni\\Desktop\\Project\\Tools\\VEVOS_Simulation_Sampling\\simulated_variants\\openvpn");
        final Path sampleBasePath = Paths.get("C:\\Users\\Berni\\Desktop\\Project\\Tools\\VEVOS_Simulation_Sampling\\simulated_variants\\test");
        iterateSamplings(sampleBasePath);
    }

    public static void iterateSamplings(Path sampleBasePath){
        List<Path> samplePaths = DirUtils.getSubDirectoryPaths(sampleBasePath);
        List<Path> resolvedSamplePaths = VevosUtils.extendSamplePathsByCommitFolder(samplePaths);
        resolvedSamplePaths.forEach(ConfigTransformer::transformConfigurations);
    }

    public static void transformConfigurations(Path variantsBasePath){
        try (Stream<Path> stream = Files.list(variantsBasePath.resolve("configs"))) {
            List<Path> paths = stream.filter(file -> !Files.isDirectory(file))
                    .filter(file -> file.getFileName().toString().contains(".config"))
                    .toList();
            paths.forEach(ConfigTransformer::transformConfiguration);
        } catch (IOException e){
            throw new RuntimeException(e);
        }
    }

    public static void transformConfiguration(Path configFilePath){
        String configFileName = configFilePath.getFileName().toString();
        String extension = FilenameUtils.getExtension(configFileName);
        String variantName = configFileName.replace("." + extension, "");
        Path eccoConfigFile = configFilePath.getParent().getParent().resolve(variantName + "\\.config").toAbsolutePath();
        try {
            String eccoConfigString = vevosConfigFileToConfig(configFilePath);
            eccoConfigString = addBaseFeature(eccoConfigString);
            Files.write(eccoConfigFile, eccoConfigString.getBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    public static String addBaseFeature(String configString){
        if (configString.isEmpty()){
            configString = "BASE";
        } else {
            configString = "BASE, " + configString;
        }
        return configString;
    }

    public static String vevosConfigFileToConfig(Path configFile){
        List<String> features = readVevosConfigFile(configFile);
        return String.join(", ", features);
    }

    public static String[] gatherConfigFeatures(Path vevosVariantConfigsFolder, int maxFeatureNumber){
        Set<String> features = new HashSet<>();
        Set<String> vevosConfigFiles = listFilesUsingFilesList(vevosVariantConfigsFolder);
        Iterator<String> vevosConfigFileIterator = vevosConfigFiles.iterator();

        while (features.size() < maxFeatureNumber && vevosConfigFileIterator.hasNext()){
            String vevosConfigFileName = vevosConfigFileIterator.next();
            Path vevosConfigFilePath = vevosVariantConfigsFolder.resolve(vevosConfigFileName);
            List<String> vevosConfigFeatures = readVevosConfigFile(vevosConfigFilePath);
            features.addAll(vevosConfigFeatures);
        }

        return features.toArray(new String[0]);
    }

    public static Set<String> listFilesUsingFilesList(Path dir) {
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(file -> !Files.isDirectory(file))
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .collect(Collectors.toSet());
        } catch (IOException e){
            throw new RuntimeException(String.format("Listing files in directory %s failed.", dir));
        }
    }

    public static List<String> readVevosConfigFile(Path filePath){
        try {
            List<String> features = Files.readAllLines(filePath);
            features = features.stream().filter(f -> !f.isEmpty()).collect(Collectors.toList());
            return features;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
