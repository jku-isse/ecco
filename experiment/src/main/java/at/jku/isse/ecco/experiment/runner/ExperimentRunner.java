package at.jku.isse.ecco.experiment.runner;

import at.jku.isse.ecco.experiment.config.Boosting;
import at.jku.isse.ecco.experiment.config.ExperimentRunConfiguration;
import at.jku.isse.ecco.experiment.mistake.*;
import at.jku.isse.ecco.experiment.picker.FeatureTraceMemoryListPicker;
import at.jku.isse.ecco.experiment.picker.featuretracepicker.RandomFeatureTracePicker;
import at.jku.isse.ecco.experiment.result.ResultCalculator;
import at.jku.isse.ecco.experiment.result.persister.ResultPersister;
import at.jku.isse.ecco.experiment.utils.vevos.GroundTruth;
import at.jku.isse.ecco.featuretrace.evaluation.EvaluationStrategy;
import at.jku.isse.ecco.maintree.building.MainTreeBuildingStrategy;
import at.jku.isse.ecco.storage.mem.maintree.MemAssociationMerger;
import at.jku.isse.ecco.storage.mem.maintree.MemBoostedAssociationMerger;
import at.jku.isse.ecco.tree.*;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.feature.*;
import at.jku.isse.ecco.experiment.utils.LiteralCleanUpVisitor;

import java.util.*;


public class ExperimentRunner implements ExperimentRunnerInterface {

    // todo: refactor (too many fields)
    private final ExperimentRunConfiguration config;
    private final Repository.Op repository;
    private final ResultPersister persister;
    private MainTreeBuildingStrategy boostedBuildingStrategy;
    private MainTreeBuildingStrategy nonBoostedBuildingStrategy;


    private EvaluationStrategy evaluationStrategy;
    private int featureTracePercentage;
    private int mistakePercentage;
    private String mistakeStrategyName;
    private Boosting boosting;
    private FeatureTraceMemoryListPicker listPicker;



    public ExperimentRunner(ExperimentRunConfiguration config,
                            Repository.Op repository,
                            ResultPersister persister){
        this.config = config;
        this.repository = repository;
        this.persister = persister;
        this.nonBoostedBuildingStrategy = new MemAssociationMerger();
        this.boostedBuildingStrategy = new MemBoostedAssociationMerger();
    }

    public void setBoostedBuildingStrategy(MainTreeBuildingStrategy boostedBuildingStrategy){
        this.boostedBuildingStrategy = boostedBuildingStrategy;
    }

    public void setNonBoostedBuildingStrategy(MainTreeBuildingStrategy nonBoostedBuildingStrategy) {
        this.nonBoostedBuildingStrategy = nonBoostedBuildingStrategy;
    }

    private MistakeStrategy createMistakeStrategy(String type, List<String> features){
        return switch (type) {
            case "NoMistake" -> new NoMistake();
            case "SwappedCondition" -> new SwappedCondition();
            case "ErroneousConjunction" -> new ErroneousConjunction(features);
            case "SwappedFeature" -> new SwappedFeature(features);
            case "SwappedOperator" -> new SwappedOperator();
            case "MissingConjunction" -> new MissingConjunction();
            default -> throw new IllegalArgumentException("Unsupported mistake strategy type: " + type);
        };
    }

    public void runExperiment(){
        this.iterateFeatureTraceListPickers();
    }

    private void iterateFeatureTraceListPickers(){
        List<FeatureTraceMemoryListPicker> listPickers = this.config.getListPickers();
        if (listPickers.isEmpty()){
            listPickers.add(new RandomFeatureTracePicker());
        }

        for (FeatureTraceMemoryListPicker listPicker : listPickers){
            this.listPicker = listPicker;
            this.iterateEvaluationStrategies();
        }
    }

    private void iterateEvaluationStrategies(){
        for (EvaluationStrategy evaluationStrategy : this.config.getEvaluationStrategies()){
            this.evaluationStrategy = evaluationStrategy;
            this.iterateFeatureTracePercentages();
        }
    }

    private void iterateFeatureTracePercentages(){
        for (int featureTracePercentage : this.config.getFeatureTracePercentages()){
            this.featureTracePercentage = featureTracePercentage;
            this.iterateMistakePercentages();
        }
    }

    private void iterateMistakePercentages(){
        if (featureTracePercentage == 0){
            this.mistakePercentage = 0;
            this.iterateMistakeStrategies();
        } else {
            for (int mistakePercentage : this.config.getMistakePercentages()){
                this.mistakePercentage = mistakePercentage;
                this.iterateMistakeStrategies();
            }
        }
    }

    private void iterateMistakeStrategies(){
        if (mistakePercentage == 0){
            this.mistakeStrategyName = "NoMistake";
            this.performExperimentIteration();
        } else {
            for (String mistakeStrategy : this.config.getMistakeStrategies()) {
                this.mistakeStrategyName = mistakeStrategy;
                this.performExperimentIteration();
            }
        }
    }

    private void performExperimentIteration(){
        MistakeCreator mistakeCreator = new MistakeCreator(this.createMistakeStrategy(this.mistakeStrategyName, config.getFeatures()));
        RepositoryPreparator repositoryPreparator = new RepositoryPreparator(mistakeCreator, this.listPicker);
        GroundTruth groundTruth = new GroundTruth(this.config.getVariantsDir());

        repositoryPreparator.prepareRepository(this.repository, featureTracePercentage, mistakePercentage, groundTruth);

        this.boosting = this.config.getBoosting();

        // perform without boost
        if (boosting.equals(Boosting.DISABLED) || boosting.equals(Boosting.BOTH)) {
            this.repository.setMaintreeBuildingStrategy(this.nonBoostedBuildingStrategy);
            this.evaluateMainTree(false, groundTruth);
        }

        // perform with boost
        if (boosting.equals(Boosting.ENABLED) || boosting.equals(Boosting.BOTH)) {
            this.repository.setMaintreeBuildingStrategy(this.boostedBuildingStrategy);
            this.evaluateMainTree(true, groundTruth);
        }

        repositoryPreparator.undoPreparation();
    }

    private void evaluateMainTree(boolean boost, GroundTruth groundTruth){
        this.repository.buildMainTree();
        Node.Op mainTree = this.repository.getMainTree();
        this.literalNameCleanup(mainTree);
        // todo: refactor (too many parameters)
        ResultCalculator metricsCalculator = new ResultCalculator(this.config, featureTracePercentage, this.persister, evaluationStrategy, mistakePercentage, mistakeStrategyName, boost, groundTruth, this.listPicker);
        metricsCalculator.calculateMetrics(mainTree);
    }

    private void literalNameCleanup(Node.Op node){
        // necessary for when there are features in the ground-truth without revision-ID
        Map<String, String> literalNameMap = new HashMap<>();
        for (String groundTruthName : this.config.getFeaturesIncludingBase()){
            Collection<Feature> features = this.repository.getFeaturesByName(groundTruthName);
            if (features.size() != 0){
                String repoName = features.iterator().next().getLatestRevision().getLogicLiteralRepresentation();
                literalNameMap.put(groundTruthName, repoName);
            }
        }
        LiteralCleanUpVisitor visitor = new LiteralCleanUpVisitor(literalNameMap);
        node.traverse(visitor);
    }
}
