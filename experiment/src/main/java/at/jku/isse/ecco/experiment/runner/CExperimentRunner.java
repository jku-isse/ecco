package at.jku.isse.ecco.experiment.runner;

import at.jku.isse.ecco.experiment.config.ExperimentRunConfiguration;
import at.jku.isse.ecco.experiment.mistake.*;
import at.jku.isse.ecco.experiment.result.ResultCalculator;
import at.jku.isse.ecco.experiment.result.persister.ResultPersister;
import at.jku.isse.ecco.tree.*;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.feature.*;
import at.jku.isse.ecco.experiment.utils.BaseCleanUpVisitor;
import at.jku.isse.ecco.experiment.utils.LiteralCleanUpVisitor;

import java.util.*;


public class CExperimentRunner implements ExperimentRunner {
    private final ExperimentRunConfiguration config;
    private final MistakeCreator mistakeCreator;
    private final Repository.Op repository;
    private final ResultPersister persister;

    public CExperimentRunner(ExperimentRunConfiguration config, Repository.Op repository, ResultPersister persister){
        this.config = config;
        this.repository = repository;
        this.persister = persister;

        this.mistakeCreator = new MistakeCreator(this.createMistakeStrategy(config.getMistakeStrategy(), config.getFeatures()));
    }

    private MistakeStrategy createMistakeStrategy(String type, List<String> features){
        return switch (type) {
            case "ConditionSwapper" -> new ConditionSwapper();
            case "Conjugator" -> new Conjugator(features);
            case "FeatureSwitcher" -> new FeatureSwitcher(features);
            case "OperatorSwapper" -> new OperatorSwapper();
            case "Unconjugator" -> new Unconjugator();
            default -> throw new IllegalArgumentException("Unsupported mistake strategy type: " + type);
        };
    }


    public void runExperiment(){
        this.repository.removeFeatureTracePercentage(100 - this.config.getFeatureTracePercentage());
        this.mistakeCreator.createMistakePercentage(this.repository, this.config.getMistakePercentage());
        Node.Op mainTree = this.repository.fuseAssociationsWithFeatureTraces();
        this.literalNameCleanup(mainTree);
        ResultCalculator metricsCalculator = new ResultCalculator(this.config, this.persister);
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

    private void printExperimentMessage(){
        System.out.println(
                "Running experiment with following settings:\n" +
                        "Strategy: " + this.config.getEvaluationStrategy().getStrategyName() + "\n" +
                        "Feature Trace Percentage: " + this.config.getFeatureTracePercentage() + "\n" +
                        "Mistake Percentage: " + this.config.getMistakePercentage() + "\n" +
                        "Mistake Strategy: " + this.mistakeCreator.getMistakeStrategy().getClass().getSimpleName()
        );
    }
}
