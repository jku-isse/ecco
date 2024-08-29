package config;

import at.jku.isse.ecco.experiment.config.ExperimentRunConfiguration;
import at.jku.isse.ecco.experiment.utils.ResourceUtils;
import at.jku.isse.ecco.featuretrace.evaluation.DiffBasedEvaluation;
import at.jku.isse.ecco.featuretrace.evaluation.EvaluationStrategy;

import java.nio.file.Path;
import java.nio.file.Paths;

public class DummyConfiguration {

    private String repositoryName;
    private int numberOfRuns;
    private int minVariantFeatures;
    private int maxVariantFeatures;
    private Path vevosSplRepositoryBasePath;
    private Path vevosGroundTruthDatasetPath;
    private Path variantsDir;
    private Integer numberOfVariants;
    private Integer featureTracePercentage;
    private Integer mistakePercentage;
    private EvaluationStrategy evaluationStrategy;
    private String mistakeStrategy;

    public DummyConfiguration(){
        this.repositoryName = "";
        this.numberOfRuns = 1;
        this.minVariantFeatures = 0;
        this.maxVariantFeatures = 10;
        this.vevosGroundTruthDatasetPath = Paths.get("");
        this.vevosSplRepositoryBasePath = Paths.get("");
        this.variantsDir = ResourceUtils.getResourceFolderPath("Sampling_Base_1/C_SPL/Sample1/SingleCommit");
        this.numberOfVariants = 1;
        this.featureTracePercentage = 0;
        this.mistakePercentage = 0;
        this.evaluationStrategy = new DiffBasedEvaluation();
        this.mistakeStrategy = "DiffBasedEvaluation";
    }

    public ExperimentRunConfiguration createRunConfiguration(){
        return new ExperimentRunConfiguration(this.repositoryName,
                this.numberOfRuns,
                this.minVariantFeatures,
                this.maxVariantFeatures,
                this.vevosSplRepositoryBasePath,
                this.vevosGroundTruthDatasetPath,
                this.variantsDir,
                this.numberOfVariants,
                this.featureTracePercentage,
                this.mistakePercentage,
                this.evaluationStrategy,
                this.mistakeStrategy);
    }

    public String getRepositoryName() {return repositoryName;}
    public int getNumberOfRuns() {return numberOfRuns;}
    public int getMinVariantFeatures() {return minVariantFeatures;}
    public int getMaxVariantFeatures() {return maxVariantFeatures;}
    public Path getVevosSplRepositoryBasePath() {return vevosSplRepositoryBasePath;}
    public Path getVevosGroundTruthDatasetPath() {return vevosGroundTruthDatasetPath;}
    public Path getVariantsDir() {return variantsDir;}
    public Integer getNumberOfVariants() {return numberOfVariants;}
    public Integer getFeatureTracePercentage() {return featureTracePercentage;}
    public Integer getMistakePercentage() {return mistakePercentage;}
    public EvaluationStrategy getEvaluationStrategy() {return evaluationStrategy;}
    public String getMistakeStrategy() {return mistakeStrategy;}

    public void setRepositoryName(String repositoryName) {this.repositoryName = repositoryName;}
    public void setNumberOfRuns(int numberOfRuns) {this.numberOfRuns = numberOfRuns;}
    public void setMinVariantFeatures(int minVariantFeatures) {this.minVariantFeatures = minVariantFeatures;}
    public void setMaxVariantFeatures(int maxVariantFeatures) {this.maxVariantFeatures = maxVariantFeatures;}
    public void setVevosSplRepositoryBasePath(Path vevosSplRepositoryBasePath) {this.vevosSplRepositoryBasePath = vevosSplRepositoryBasePath;}
    public void setVevosGroundTruthDatasetPath(Path vevosGroundTruthDatasetPath) {this.vevosGroundTruthDatasetPath = vevosGroundTruthDatasetPath;}
    public void setVariantsDir(Path variantsDir) {this.variantsDir = variantsDir;}
    public void setNumberOfVariants(Integer numberOfVariants) {this.numberOfVariants = numberOfVariants;}
    public void setFeatureTracePercentage(Integer featureTracePercentage) {this.featureTracePercentage = featureTracePercentage;}
    public void setMistakePercentage(Integer mistakePercentage) {this.mistakePercentage = mistakePercentage;}
    public void setEvaluationStrategy(EvaluationStrategy evaluationStrategy) {this.evaluationStrategy = evaluationStrategy;}
    public void setMistakeStrategy(String mistakeStrategy) {this.mistakeStrategy = mistakeStrategy;}
}
