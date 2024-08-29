package at.jku.isse.ecco.experiment.config;

import at.jku.isse.ecco.experiment.utils.PropertyUtils;
import at.jku.isse.ecco.experiment.utils.ResourceUtils;
import at.jku.isse.ecco.featuretrace.evaluation.EvaluationStrategy;

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.*;

public class ExperimentConfiguration{
    private final List<String> repositoryNames;
    private final int numberOfRuns;
    private final int minVariantFeatures;
    private final int maxVariantFeatures;
    private final Path vevosSplRepositoryBasePath;
    private final Path vevosGroundTruthDatasetPath;
    private final Path variantsDir;
    private final List<Integer> numbersOfVariants;
    private final List<Integer> featureTracePercentages;
    private final List<Integer> mistakePercentages;
    private final List<EvaluationStrategy> evaluationStrategies;
    private final List<String> mistakeStrategies;
    private List<ExperimentRunConfiguration> runConfigurations;

    public ExperimentConfiguration(String configurationPath) {
        Properties config = PropertyUtils.loadProperties(configurationPath);
        this.repositoryNames = PropertyUtils.loadStringList(config, "repositoryNames");
        this.numberOfRuns = PropertyUtils.loadInteger(config, "numberOfRuns");
        this.minVariantFeatures = PropertyUtils.loadInteger(config, "minVariantFeatures");
        this.maxVariantFeatures = PropertyUtils.loadInteger(config, "maxVariantFeatures");
        this.vevosSplRepositoryBasePath = PropertyUtils.loadPath(config, "vevosSplRepositoryBasePath");
        this.vevosGroundTruthDatasetPath = PropertyUtils.loadPath(config, "vevosGroundTruthDatasetPath");
        this.numbersOfVariants = PropertyUtils.loadIntegerList(config, "numbersOfVariants");
        this.featureTracePercentages = PropertyUtils.loadIntegerList(config, "featureTracePercentages");
        this.mistakePercentages = PropertyUtils.loadIntegerList(config, "mistakePercentages");
        this.evaluationStrategies = this.loadEvaluationStrategies(config);
        this.mistakeStrategies = PropertyUtils.loadStringList(config, "mistakeStrategies");

        this.variantsDir = ResourceUtils.getResourceFolderPath("sample");

        this.createExperimentRunConfigurations();
    }
    
    public ExperimentConfiguration(List<String> repositoryNames,
                                 int numberOfRuns,
                                 int minVariantFeatures,
                                 int maxVariantFeatures,
                                 Path vevosSplRepositoryBasePath,
                                 Path vevosGroundTruthDatasetPath,
                                 Path variantsDir,
                                 List<Integer> numbersOfVariants,
                                 List<Integer> featureTracePercentages,
                                 List<Integer> mistakePercentages,
                                 List<EvaluationStrategy> evaluationStrategies,
                                 List<String> mistakeStrategies){
        this.repositoryNames = repositoryNames;
        this.numberOfRuns = numberOfRuns;
        this.minVariantFeatures = minVariantFeatures;
        this.maxVariantFeatures = maxVariantFeatures;
        this.vevosGroundTruthDatasetPath = vevosGroundTruthDatasetPath;
        this.vevosSplRepositoryBasePath = vevosSplRepositoryBasePath;
        this.variantsDir = variantsDir;
        this.numbersOfVariants = numbersOfVariants;
        this.featureTracePercentages = featureTracePercentages;
        this.mistakePercentages = mistakePercentages;
        this.mistakeStrategies = mistakeStrategies;
        this.evaluationStrategies = evaluationStrategies;
        this.createExperimentRunConfigurations();
    }

    private List<EvaluationStrategy> loadEvaluationStrategies(Properties properties){
        List<EvaluationStrategy> instances = new ArrayList<>();
        try {
            String classNames = properties.getProperty("evaluationStrategies");
            if (classNames == null) { throw new RuntimeException("evaluationStrategies not found in properties file."); }
            String[] classArray = classNames.split(",");
            for (String className : classArray) {
                Class<?> clazz = Class.forName(className.trim());
                if (EvaluationStrategy.class.isAssignableFrom(clazz)) {
                    EvaluationStrategy instance = (EvaluationStrategy) clazz.getDeclaredConstructor().newInstance();
                    instances.add(instance);
                } else {
                    throw new RuntimeException(className + " is no subtype of EvaluationStrategy.");
                }
            }
        } catch (InvocationTargetException | ClassNotFoundException | InstantiationException |
                 IllegalAccessException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        return instances;
    }

    private void createExperimentRunConfigurations(){
        List<ExperimentRunConfiguration> runConfigurations = new LinkedList<>();
        for (String repositoryName : this.repositoryNames){
            for (int numberOfVariants : this.numbersOfVariants){
                for (int featureTracePercentage : this.featureTracePercentages){
                    for (int mistakePercentage : this.mistakePercentages){
                        for (EvaluationStrategy evaluationStrategy : this.evaluationStrategies){
                            for (String mistakeStrategy : this.mistakeStrategies){
                                runConfigurations.add(new ExperimentRunConfiguration(
                                        repositoryName,
                                        this.numberOfRuns,
                                        this.minVariantFeatures,
                                        this.maxVariantFeatures,
                                        this.vevosSplRepositoryBasePath,
                                        this.vevosGroundTruthDatasetPath,
                                        this.variantsDir,
                                        numberOfVariants,
                                        featureTracePercentage,
                                        mistakePercentage,
                                        evaluationStrategy,
                                        mistakeStrategy));
                            }
                        }
                    }
                }
            }
        }
        this.runConfigurations = runConfigurations;
    }

    public ExperimentRunConfiguration getNextRunConfiguration(){
        if (this.runConfigurations.isEmpty()){
            return null;
        } else {
            ExperimentRunConfiguration runConfiguration = this.runConfigurations.get(0);
            this.runConfigurations.remove(0);
            return runConfiguration;
        }
    }

    public int getNumberOfRunsLeft(){
        if (this.runConfigurations.isEmpty()){
            throw new RuntimeException("Configurations for experiments are not yet deduced.");
        } else {
            return this.runConfigurations.size();
        }
    }

    public String toString(){
        return "Experiment Configuration:\n" +
                "Repositories: " + this.repositoryNames + "\n" +
                "Number of Runs per Unique Configuration: " + this.numberOfRuns + "\n" +
                "Minimum Number of Features: " + this.minVariantFeatures + "\n" +
                "Maximum Number of Features: " + this.maxVariantFeatures + "\n" +
                "Path to SPL Repositories: " + this.vevosSplRepositoryBasePath + "\n" +
                "Path to Vevos Ground Truth: " + this.vevosGroundTruthDatasetPath + "\n" +
                "Path to Sampled Variants: " + this.variantsDir + "\n" +
                "Numbers of Variants: " + this.numbersOfVariants + "\n" +
                "Feature Trace Percentages: " + this.featureTracePercentages + "\n" +
                "Mistake Percentages: " + this.mistakePercentages + "\n" +
                "Evaluation Strategies: " + this.evaluationStrategies + "\n" +
                "Mistake Strategies: " + this.mistakeStrategies + "\n";
    }
}
