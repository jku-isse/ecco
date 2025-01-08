package at.jku.isse.ecco.experiment.config;

import at.jku.isse.ecco.experiment.picker.FeatureTraceMemoryListPicker;
import at.jku.isse.ecco.experiment.utils.vevos.ConfigTransformer;
import at.jku.isse.ecco.featuretrace.evaluation.EvaluationStrategy;
import at.jku.isse.ecco.experiment.utils.vevos.VevosUtils;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class ExperimentRunConfiguration{

    private final String repositoryName;
    private final int numberOfRuns;
    private final int minVariantFeatures;
    private final int maxVariantFeatures;
    private final Path vevosSplRepositoryBasePath;
    private final Path vevosGroundTruthDatasetPath;
    private final Path variantsDir;
    private final Integer numberOfVariants;
    private final Integer[] featureTracePercentages;
    private final Integer[] mistakePercentages;
    private final List<EvaluationStrategy> evaluationStrategies;
    private final String[] mistakeStrategies;
    private List<Path> variantPicks;
    private List<String> features;
    private List<String> variantConfigurations;
    private List<FeatureTraceMemoryListPicker> listPickers;

    private Boosting boosting;
    
    public ExperimentRunConfiguration(String repositoryName,
                                      int numberOfRuns,
                                      int minVariantFeatures,
                                      int maxVariantFeatures,
                                      Path vevosSplRepositoryBasePath,
                                      Path vevosGroundTruthDatasetPath,
                                      Path variantsDir,
                                      Integer numberOfVariants,
                                      Integer[] featureTracePercentages,
                                      Integer[] mistakePercentages,
                                      List<EvaluationStrategy> evaluationStrategies,
                                      String[] mistakeStrategies,
                                      Boosting boosting,
                                      List<FeatureTraceMemoryListPicker> listPickers){
        this.repositoryName = repositoryName;
        this.numberOfRuns = numberOfRuns;
        this.minVariantFeatures = minVariantFeatures;
        this.maxVariantFeatures = maxVariantFeatures;
        this.vevosSplRepositoryBasePath = vevosSplRepositoryBasePath.resolve(repositoryName);
        this.vevosGroundTruthDatasetPath = vevosGroundTruthDatasetPath.resolve(repositoryName);
        this.variantsDir = variantsDir;
        this.numberOfVariants = numberOfVariants;
        this.featureTracePercentages = featureTracePercentages;
        this.mistakePercentages = mistakePercentages;
        this.evaluationStrategies = evaluationStrategies;
        this.mistakeStrategies = mistakeStrategies;
        this.boosting = boosting;
        this.listPickers = listPickers;
    }

    public void pickVariants(){
        List<Path> variantPaths = VevosUtils.getVariantFolders(this.variantsDir);
        this.variantPicks = this.pickRandomVariants(variantPaths, numberOfVariants);
        this.features = List.of(ConfigTransformer.gatherConfigFeatures(this.variantsDir.resolve("configs"), this.maxVariantFeatures));
        this.setVariantConfigurations();
    }

    private List<Path> pickRandomVariants(List<Path> variantPaths, int pickSize){
        Collections.shuffle(variantPaths);
        return variantPaths.subList(0, pickSize);
    }

    public List<String> getFeatures(){
        return this.features;
    }

    public List<String> getFeaturesIncludingBase(){
        List<String> literalsIncludingBase = new LinkedList<>(this.features);
        literalsIncludingBase.add("BASE");
        return literalsIncludingBase;
    }

    public List<String> getVariantConfigurations(){
        return this.variantConfigurations;
    }

    private void setVariantConfigurations(){
        List<String> configList = new LinkedList<>();
        for (Path variantPath : this.getVariantPicks()){
            configList.add(VevosUtils.variantPathToConfigString(variantPath));
        }
        this.variantConfigurations = configList;
    }

    public String getRepositoryName() {
        return repositoryName;
    }

    public int getNumberOfRuns() {
        return numberOfRuns;
    }

    public int getMinVariantFeatures() {
        return minVariantFeatures;
    }

    public int getMaxVariantFeatures() {
        return maxVariantFeatures;
    }

    public Path getVevosSplRepositoryBasePath() {
        return vevosSplRepositoryBasePath;
    }

    public Path getVevosGroundTruthDatasetPath() {
        return vevosGroundTruthDatasetPath;
    }

    public Path getVariantsDir() {
        return variantsDir;
    }

    public Integer[] getFeatureTracePercentages() {
        return featureTracePercentages;
    }

    public Integer[] getMistakePercentages() {
        return mistakePercentages;
    }

    public List<EvaluationStrategy> getEvaluationStrategies() {
        return evaluationStrategies;
    }

    public String[] getMistakeStrategies() {
        return mistakeStrategies;
    }

    public List<Path> getVariantPicks() {
        return variantPicks;
    }

    public Boosting getBoosting(){ return this.boosting; }

    public List<FeatureTraceMemoryListPicker> getListPickers(){
        return this.listPickers;
    }

    public String toString(){
        String description = "Experiment Run Configuration:\n" +
                "Repository: " + this.repositoryName + "\n" +
                "Number of Runs: " + this.numberOfRuns + "\n" +
                "Minimum Number of Features: " + this.minVariantFeatures + "\n" +
                "Maximum Number of Features: " + this.maxVariantFeatures + "\n" +
                "Path to SPL Repositories: " + this.vevosSplRepositoryBasePath + "\n" +
                "Path to Vevos Ground Truth: " + this.vevosGroundTruthDatasetPath + "\n" +
                "Path to Sampled Variants: " + this.variantsDir + "\n" +
                "Number of Variants: " + this.numberOfVariants + "\n" +
                "Feature Trace Percentages: " + this.featureTracePercentages + "\n" +
                "Mistake Percentages: " + this.mistakePercentages + "\n" +
                "Evaluation Strategies: " + this.evaluationStrategies + "\n" +
                "Mistake Strategies: " + this.mistakeStrategies + "\n";

        if (this.variantPicks != null){
            description += "Variant Picks: " + this.variantPicks + "\n" +
                    "Features: " + this.features + "\n" +
                    "Variant Configurations: " + this.variantConfigurations + "\n";
        } else {
            description += """
                    Variant Picks: Not yet picked
                    Features: Variants not yet picked
                    Variant Configurations: Variants not yet picked
                    """;
        }

        return description;
    }
}
