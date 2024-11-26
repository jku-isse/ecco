package config;

import at.jku.isse.ecco.experiment.config.ExperimentRunConfiguration;
import at.jku.isse.ecco.experiment.utils.ResourceUtils;
import at.jku.isse.ecco.featuretrace.evaluation.DiffBasedEvaluation;
import at.jku.isse.ecco.featuretrace.evaluation.EvaluationStrategy;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;

public class DummyConfiguration {

    private String repositoryName;
    private int numberOfRuns;
    private int minVariantFeatures;
    private int maxVariantFeatures;
    private Path vevosSplRepositoryBasePath;
    private Path vevosGroundTruthDatasetPath;
    private Path variantsDir;
    private Integer numberOfVariants;
    private Integer[] featureTracePercentages;
    private Integer[] mistakePercentages;
    private List<EvaluationStrategy> evaluationStrategies;
    private String[] mistakeStrategies;
    private boolean boosting = false;

    public DummyConfiguration(){
        this.repositoryName = "";
        this.numberOfRuns = 1;
        this.minVariantFeatures = 0;
        this.maxVariantFeatures = 10;
        this.vevosGroundTruthDatasetPath = Paths.get("");
        this.vevosSplRepositoryBasePath = Paths.get("");
        this.variantsDir = ResourceUtils.getResourceFolderPath("Sampling_Base_1/C_SPL/Sample1/SingleCommit");
        this.numberOfVariants = 1;
        this.featureTracePercentages = new Integer[]{0};
        this.mistakePercentages = new Integer[]{0};
        this.evaluationStrategies = new LinkedList<>();
        this.evaluationStrategies.add(new DiffBasedEvaluation());
        this.mistakeStrategies = new String[]{"DiffBasedEvaluation"};
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
                this.featureTracePercentages,
                this.mistakePercentages,
                this.evaluationStrategies,
                this.mistakeStrategies,
                this.boosting);
    }

    public void enableBoosting(){ this.boosting = true; }

    public String getRepositoryName() {return repositoryName;}
    public int getNumberOfRuns() {return numberOfRuns;}
    public int getMinVariantFeatures() {return minVariantFeatures;}
    public int getMaxVariantFeatures() {return maxVariantFeatures;}
    public Path getVevosSplRepositoryBasePath() {return vevosSplRepositoryBasePath;}
    public Path getVevosGroundTruthDatasetPath() {return vevosGroundTruthDatasetPath;}
    public Path getVariantsDir() {return variantsDir;}
    public Integer getNumberOfVariants() {return numberOfVariants;}
    public Integer[] getFeatureTracePercentages() {return featureTracePercentages;}
    public Integer[] getMistakePercentages() {return mistakePercentages;}
    public List<EvaluationStrategy> getEvaluationStrategies() {return evaluationStrategies;}
    public String[] getMistakeStrategies() {return mistakeStrategies;}

    public void setRepositoryName(String repositoryName) {this.repositoryName = repositoryName;}
    public void setNumberOfRuns(int numberOfRuns) {this.numberOfRuns = numberOfRuns;}
    public void setMinVariantFeatures(int minVariantFeatures) {this.minVariantFeatures = minVariantFeatures;}
    public void setMaxVariantFeatures(int maxVariantFeatures) {this.maxVariantFeatures = maxVariantFeatures;}
    public void setVevosSplRepositoryBasePath(Path vevosSplRepositoryBasePath) {this.vevosSplRepositoryBasePath = vevosSplRepositoryBasePath;}
    public void setVevosGroundTruthDatasetPath(Path vevosGroundTruthDatasetPath) {this.vevosGroundTruthDatasetPath = vevosGroundTruthDatasetPath;}
    public void setVariantsDir(Path variantsDir) {this.variantsDir = variantsDir;}
    public void setNumberOfVariants(Integer numberOfVariants) {this.numberOfVariants = numberOfVariants;}
    public void setFeatureTracePercentages(Integer[] featureTracePercentages) {this.featureTracePercentages = featureTracePercentages;}
    public void setMistakePercentages(Integer[] mistakePercentages) {this.mistakePercentages = mistakePercentages;}
    public void setEvaluationStrategies(List<EvaluationStrategy> evaluationStrategies) {this.evaluationStrategies = evaluationStrategies;}
    public void setMistakeStrategies(String[] mistakeStrategies) {this.mistakeStrategies = mistakeStrategies;}
}
