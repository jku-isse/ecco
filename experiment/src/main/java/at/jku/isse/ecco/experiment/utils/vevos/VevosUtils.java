package at.jku.isse.ecco.experiment.utils.vevos;

import at.jku.isse.ecco.featuretrace.parser.VevosCondition;
import org.apache.commons.collections4.CollectionUtils;
import at.jku.isse.ecco.experiment.utils.DirUtils;
import org.logicng.formulas.Formula;
import org.logicng.formulas.FormulaFactory;
import org.logicng.formulas.Literal;
import org.logicng.io.parsers.ParserException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class VevosUtils {

    public static final String VEVOS_FILENAME = "pcs.variant.csv";

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

    public static void sanitizeVevosConfigFiles(Path variantsBasePath) {
        // vevos sometimes uses single pipes in feature-names, which can confuse logic parsers
        try (Stream<Path> stream = Files.list(variantsBasePath.resolve("configs"))) {
            stream.forEach(VevosUtils::sanitizeVevosConfigFile);
        }catch (IOException e){
            throw new RuntimeException("Configs files could not be sanitized: " + e.getMessage());
        }
    }

    private static void sanitizeVevosConfigFile(Path configFile){
        try {
            List<String> lines = Files.readAllLines(configFile);
            List<String> newLines = lines.stream().map(line -> line.replaceAll("(?<!\\|)\\|(?!\\|)", "OR")).toList();
            Files.write(configFile, newLines, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Unable to sanitize config file " + configFile + ": " + e.getMessage());
        }
    }

    public static void removeRootFeatureFromConfigFiles(Path variantsBasePath){
        try (Stream<Path> stream = Files.list(variantsBasePath.resolve("configs"))) {
            stream.forEach(VevosUtils::removeRootFeatureFromConfigFile);
        }catch (IOException e){
            throw new RuntimeException("Configs files could not be sanitized: " + e.getMessage());
        }
    }

    public static void removeRootFeatureFromConfigFile(Path configFile){
        try {
            List<String> lines = Files.readAllLines(configFile);
            lines.remove("Root");
            Files.write(configFile, lines, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Unable to remove Root-Feature from config file " + configFile + ": " + e.getMessage());
        }
    }

    public static void sanitizeVevosFiles(Path variantsBasePath, List<String> features){
        List<Path> variantPaths = VevosUtils.getVariantFolders(variantsBasePath);
        for (Path variantPath : variantPaths){
            Path vevosFilePath = variantPath.resolve(VEVOS_FILENAME);
            sanitizeVevosFile(vevosFilePath, features);
        }
    }

    private static void sanitizeVevosFile(Path vevosFilePath, List<String> features) {
        try {
            List<String> lines = Files.readAllLines(vevosFilePath);
            List<String> newLines = new LinkedList<>();
            newLines.add(lines.remove(0));
            for (String line : lines){
                line = line.replaceAll("(?<!\\|)\\|(?!\\|)", "OR");
                VevosCondition vevosCondition = new VevosCondition(line);
                String presenceCondition = vevosCondition.getConditionString();
                if (conditionIsRelevant(features, presenceCondition)){
                    // vevos sometimes uses single pipes in feature-names, which can confuse logic parsers
                    newLines.add(line);
                }
            }
            Files.write(vevosFilePath, newLines, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Unable to sanitize VEVOS file: " + e.getMessage());
        }
    }

    private static boolean conditionIsRelevant(List<String> features, String condition) {
        try {
            FormulaFactory formulaFactory = new FormulaFactory();
            Formula conditionFormula = formulaFactory.parse(condition);
            Collection<Literal> literals = conditionFormula.literals();
            List<String> literalNames = literals.stream().map(Literal::name).toList();
            return features.stream().anyMatch(f -> literalNames.stream().anyMatch(ln -> ln.equals(f)));
        } catch (ParserException e){
            throw new RuntimeException("Condition could not be parsed: " + e.getMessage());
        }
    }

}
