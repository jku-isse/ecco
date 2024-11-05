package at.jku.isse.ecco.experiment.runner;

import at.jku.isse.ecco.experiment.config.ExperimentRunConfiguration;
import at.jku.isse.ecco.experiment.mistake.*;
import at.jku.isse.ecco.experiment.result.ResultCalculator;
import at.jku.isse.ecco.experiment.result.persister.ResultPersister;
import at.jku.isse.ecco.featuretrace.FeatureTrace;
import at.jku.isse.ecco.featuretrace.evaluation.EvaluationStrategy;
import at.jku.isse.ecco.tree.*;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.feature.*;
import at.jku.isse.ecco.experiment.utils.LiteralCleanUpVisitor;

import java.util.*;


public class CExperimentRunner implements ExperimentRunner {
    private final ExperimentRunConfiguration config;
    private final Repository.Op repository;
    private final ResultPersister persister;

    public CExperimentRunner(ExperimentRunConfiguration config, Repository.Op repository, ResultPersister persister){
        this.config = config;
        this.repository = repository;
        this.persister = persister;
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
        this.iterateEvaluationStrategies();
    }

    private void iterateEvaluationStrategies(){
        for (EvaluationStrategy evaluationStrategy : this.config.getEvaluationStrategies()){
            this.iterateFeatureTracePercentages(evaluationStrategy);
        }
    }

    private void iterateFeatureTracePercentages(EvaluationStrategy evaluationStrategy){
        for (int featureTracePercentage : this.config.getFeatureTracePercentages()){
            this.iterateMistakePercentages(evaluationStrategy, featureTracePercentage);
        }
    }

    private void iterateMistakePercentages(EvaluationStrategy evaluationStrategy, int featureTracePercentage){
        for (int mistakePercentage : this.config.getMistakePercentages()){
            this.iterateMistakeStrategies(evaluationStrategy, featureTracePercentage, mistakePercentage);
        }
    }

    private void iterateMistakeStrategies(EvaluationStrategy evaluationStrategy, int featureTracePercentage, int mistakePercentage){
        for (String mistakeStrategy : this.config.getMistakeStrategies()){
            this.performExperimentIteration(evaluationStrategy, featureTracePercentage, mistakePercentage, mistakeStrategy);
        }
    }

    private void performExperimentIteration(EvaluationStrategy evaluationStrategy, int featureTracePercentage, int mistakePercentage, String mistakeStrategy){
        MistakeCreator mistakeCreator = new MistakeCreator(this.createMistakeStrategy(mistakeStrategy, config.getFeatures()));
        Collection<FeatureTrace> initialTraces = this.removeFeatureTracePercentage(this.repository, 100 - featureTracePercentage);
        try {
            mistakeCreator.createMistakePercentage(this.repository, mistakePercentage);
        } catch(Exception e){
            System.out.println("Mistake creation failed!");
            return;
        }
        Node.Op mainTree = this.repository.fuseAssociationsWithFeatureTraces();
        this.literalNameCleanup(mainTree);
        ResultCalculator metricsCalculator = new ResultCalculator(this.config, featureTracePercentage, this.persister, evaluationStrategy, mistakePercentage, mistakeStrategy);
        metricsCalculator.calculateMetrics(mainTree);
        this.restoreFeatureTraces(initialTraces);
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

    private Collection<FeatureTrace> removeFeatureTracePercentage(Repository.Op repo, int percentage) {
        if (percentage < 0 || percentage > 100){
            throw new RuntimeException(String.format("Percentage of feature traces is invalid (%d).", percentage));
        }
        Collection<FeatureTrace> traces = repo.getFeatureTraces();
        int noOfRemovals = (traces.size() * percentage) / 100;
        List<FeatureTrace> featureTraceList = new ArrayList<>(traces);
        Collections.shuffle(featureTraceList);
        Iterator<FeatureTrace> iterator = featureTraceList.stream().iterator();
        for (int i = 1; i <= noOfRemovals; i++){
            FeatureTrace trace = iterator.next();
            Node.Op traceNode = (Node.Op) trace.getNode();
            traceNode.removeFeatureTrace();
        }
        return traces;
    }

    private void restoreFeatureTraces(Collection<FeatureTrace> traces){
        for (FeatureTrace featureTrace: traces){
            Node.Op node = (Node.Op) featureTrace.getNode();
            node.setFeatureTrace(featureTrace);
        }
    }
}
